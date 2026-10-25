package com.example.localtwouserapp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.MotionEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DrawingBoardTest {
    private fun layer(color: Int) = Bitmap.createBitmap(384, 384, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
    @Test fun erasingAndClearingOwnLayerPreservesPartner() {
        val board = DrawingBoardView(RuntimeEnvironment.getApplication())
        board.layout(0, 0, 384, 384)
        board.setPartner(DrawingImages.encode(layer(Color.BLUE)))
        board.loadOwn(DrawingImages.encode(layer(Color.RED)))
        board.erasing = true
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(0, 1, action, 192f, 192f, 0)
            board.onTouchEvent(event); event.recycle()
        }
        val erased = layer(Color.TRANSPARENT)
        board.draw(Canvas(erased))
        assertEquals(Color.BLUE, erased.getPixel(192, 192))
        assertEquals(Color.RED, erased.getPixel(20, 20))
        board.clearOwn()
        board.draw(Canvas(erased))
        assertEquals(Color.BLUE, erased.getPixel(20, 20))
        assertEquals(Color.TRANSPARENT, DrawingImages.decode(board.snapshot())!!.getPixel(20, 20))
    }
    @Test fun malformedAndOversizedImagesAreRejected() {
        assertNull(DrawingImages.decode("not an image"))
        assertNull(DrawingImages.decode("a".repeat(DrawingImages.MAX_ENCODED + 1)))
        val oversized = Bitmap.createBitmap(385, 10, Bitmap.Config.ARGB_8888)
        assertNull(DrawingImages.decode(DrawingImages.encode(oversized)))
    }
    @Test fun canvasDoesNotPaintOverSurroundingControls() {
        val board = DrawingBoardView(RuntimeEnvironment.getApplication())
        board.layout(0, 0, 384, 384)
        val parent = Bitmap.createBitmap(484, 484, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val canvas = Canvas(parent)
        canvas.translate(50f, 50f)
        board.draw(canvas)
        assertEquals(Color.RED, parent.getPixel(10, 10))
        assertEquals(Color.rgb(255, 250, 252), parent.getPixel(100, 100))
    }
    @Test fun drawingOwnershipIsCheckedLocally() {
        val mine = Entry(id = "drawing-alice", type = "drawing", author = "alice")
        assertTrue(DrawingImages.canEdit(mine, "alice"))
        assertFalse(DrawingImages.canEdit(mine, "bob"))
        assertFalse(DrawingImages.canEdit(mine.copy(author = "bob"), "alice"))
    }
    @Test fun photoPinRendersAvatarAndFallsBackForInvalidImage() {
        val context = RuntimeEnvironment.getApplication()
        val pin = PhotoPin.create(context, "Alex", DrawingImages.encode(layer(Color.GREEN)), true)
        val scale = pin.bitmap.width / 48f
        assertEquals(Color.GREEN, pin.bitmap.getPixel((24 * scale).toInt(), (24 * scale).toInt()))
        val fallback = PhotoPin.create(context, "Alex", "invalid", true)
        assertEquals(pin.bitmap.width, fallback.bitmap.width)
        assertEquals(Color.TRANSPARENT, fallback.bitmap.getPixel(0, fallback.bitmap.height - 1))
    }
}
