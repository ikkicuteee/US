package com.example.localtwouserapp

import android.content.Context
import android.graphics.*
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import java.io.ByteArrayOutputStream

object DrawingImages {
    const val SIZE = 384
    const val MAX_ENCODED = 128_000
    fun decode(value: String, limit: Int = MAX_ENCODED): Bitmap? = runCatching {
        require(value.length <= limit)
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth in 1..SIZE && bounds.outHeight in 1..SIZE)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
    fun encode(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
    fun own(entries: List<Entry>, uid: String) = entries.firstOrNull { it.id == "drawing-$uid" && it.type == "drawing" && !it.deleted }
    fun partner(entries: List<Entry>, uid: String) = entries.firstOrNull { it.type == "drawing" && it.author != uid && !it.deleted }
    fun canEdit(entry: Entry, uid: String) = if (entry.type == "drawing")
        entry.id == "drawing-$uid" && (entry.author.isEmpty() || entry.author == uid)
    else !entry.id.startsWith("drawing-")
}

/** Erasing touches only the local user's transparent layer; the partner layer is immutable here. */
class DrawingBoardView(context: Context) : View(context) {
    private val own = Bitmap.createBitmap(DrawingImages.SIZE, DrawingImages.SIZE, Bitmap.Config.ARGB_8888)
    private val ownCanvas = Canvas(own)
    private var partner: Bitmap? = null
    private var partnerValue = ""
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val backgroundPaint = Paint().apply { color = Color.rgb(255, 250, 252) }
    private var lastX = 0f
    private var lastY = 0f
    private var drawing = false
    var inkColor: Int = Color.rgb(123, 59, 74)
    var erasing = false
    var ownOnTop = true
    var changed: () -> Unit = {}
    fun loadOwn(value: String) {
        own.eraseColor(Color.TRANSPARENT)
        DrawingImages.decode(value, 1_000_000)?.let { bitmap ->
            ownCanvas.drawBitmap(bitmap, null, Rect(0, 0, own.width, own.height), imagePaint); bitmap.recycle()
        }
        invalidate()
    }
    fun setPartner(value: String) {
        if (value == partnerValue) return
        partnerValue = value
        partner?.recycle(); partner = DrawingImages.decode(value); invalidate()
    }
    fun snapshot() = DrawingImages.encode(own)
    fun clearOwn() { own.eraseColor(Color.TRANSPARENT); invalidate(); changed() }
    override fun onDraw(canvas: Canvas) {
        // Compose can provide a canvas clipped to the parent, not this AndroidView.
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        val dest = Rect(0, 0, width, height)
        if (!ownOnTop) canvas.drawBitmap(own, null, dest, imagePaint)
        partner?.let { canvas.drawBitmap(it, null, dest, imagePaint) }
        if (ownOnTop) canvas.drawBitmap(own, null, dest, imagePaint)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (width == 0 || height == 0) return false
        val x = (event.x / width * own.width).coerceIn(0f, own.width.toFloat())
        val y = (event.y / height * own.height).coerceIn(0f, own.height.toFloat())
        paint.color = inkColor; paint.strokeWidth = if (erasing) 24f else 5f
        paint.xfermode = if (erasing) PorterDuffXfermode(PorterDuff.Mode.CLEAR) else null
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                drawing = true; lastX = x; lastY = y
                ownCanvas.drawPoint(x, y, paint)
            }
            MotionEvent.ACTION_MOVE -> if (drawing) {
                ownCanvas.drawLine(lastX, lastY, x, y, paint); lastX = x; lastY = y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (drawing) { drawing = false; changed() }
                parent?.requestDisallowInterceptTouchEvent(false)
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
            }
        }
        invalidate(); return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
