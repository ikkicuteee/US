package com.example.localtwouserapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProfilePhotosTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun exifRotationIsAppliedBeforeCrop() {
        val file = File.createTempFile("orientation-test-", ".jpg", context.cacheDir)
        val source = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        try {
            file.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            ExifInterface(file.path).apply { setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString()); saveAttributes() }
            val decoded = PhotoDecoder.decode(context, Uri.fromFile(file))
            try { assertEquals(200, decoded.width); assertEquals(400, decoded.height) } finally { decoded.recycle() }
        } finally { source.recycle(); file.delete() }
    }
    @Test fun selectedCropPositionChangesSavedAvatarAndKeepsSourceAlive() {
        val source = Bitmap.createBitmap(300, 100, Bitmap.Config.ARGB_8888)
        for (x in 0 until 300) for (y in 0 until 100) source.setPixel(x, y, if (x < 150) Color.RED else Color.BLUE)
        try {
            val left = ProfilePhotos.decode(ProfilePhotos.encode(source, 0f, .5f, 1f))!!
            val right = ProfilePhotos.decode(ProfilePhotos.encode(source, 1f, .5f, 1f))!!
            assertTrue(left.size <= 24000); assertFalse(source.isRecycled)
            val a = BitmapFactory.decodeByteArray(left, 0, left.size); val b = BitmapFactory.decodeByteArray(right, 0, right.size)
            try {
                assertEquals(192, a.width); assertEquals(192, a.height)
                assertTrue(Color.red(a.getPixel(96, 96)) > 200)
                assertTrue(Color.blue(b.getPixel(96, 96)) > 200)
            } finally { a.recycle(); b.recycle() }
        } finally { source.recycle() }
    }
}
