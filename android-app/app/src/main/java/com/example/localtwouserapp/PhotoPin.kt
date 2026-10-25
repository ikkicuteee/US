package com.example.localtwouserapp

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable

object PhotoPin {
    fun create(context: Context, name: String, avatar: String, own: Boolean): BitmapDrawable {
        val scale = context.resources.displayMetrics.density.coerceAtMost(3f)
        val bitmap = Bitmap.createBitmap((48 * scale).toInt(), (64 * scale).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.scale(scale, scale)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val color = if (own) Color.rgb(123, 59, 74) else Color.rgb(181, 101, 118)
        paint.color = Color.WHITE
        val shape = Path().apply {
            moveTo(24f, 63f); cubicTo(17f, 49f, 1f, 39f, 1f, 24f)
            cubicTo(1f, -7f, 47f, -7f, 47f, 24f)
            cubicTo(47f, 39f, 31f, 49f, 24f, 63f); close()
        }
        canvas.drawPath(shape, paint)
        paint.color = color; canvas.save(); canvas.scale(.94f, .94f, 24f, 30f); canvas.drawPath(shape, paint); canvas.restore()
        val photo = decode(avatar)
        if (photo != null) {
            val shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val matrix = Matrix()
            val factor = 34f / minOf(photo.width, photo.height)
            matrix.setScale(factor, factor)
            matrix.postTranslate(24f - photo.width * factor / 2, 24f - photo.height * factor / 2)
            shader.setLocalMatrix(matrix); paint.shader = shader
            canvas.drawCircle(24f, 24f, 17f, paint); paint.shader = null
            photo.recycle()
        } else {
            paint.color = Color.rgb(233, 227, 230); canvas.drawCircle(24f, 24f, 17f, paint)
            paint.color = color; paint.textSize = 20f; paint.textAlign = Paint.Align.CENTER; paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText(name.trim().take(1).uppercase().ifEmpty { "?" }, 24f, 31f, paint)
        }
        bitmap.density = context.resources.displayMetrics.densityDpi
        return BitmapDrawable(context.resources, bitmap)
    }
    private fun decode(value: String): Bitmap? = runCatching {
        require(value.length <= 32_000)
        val bytes = ProfilePhotos.decode(value) ?: return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        require(options.outWidth > 0 && options.outHeight > 0)
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 192) options.inSampleSize *= 2
        options.inJustDecodeBounds = false
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }.getOrNull()
}
