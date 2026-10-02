package com.example.localtwouserapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

/** A bounded avatar, never a shared original or a Storage upload. */
object ProfilePhotos {
    fun encode(context: Context, uri: Uri): String {
        val source = PhotoDecoder.decode(context, uri)
        return try { encode(source, .5f, .5f, 1f) } finally { source.recycle() }
    }
    fun encode(source: Bitmap, x: Float, y: Float, zoom: Float): String {
        val box = PhotoCrop.bounds(source.width, source.height, x, y, zoom)
        val cropped = Bitmap.createBitmap(source, box.left, box.top, box.size, box.size)
        val avatar = Bitmap.createScaledBitmap(cropped, 192, 192, true)
        val output = ByteArrayOutputStream()
        try {
            var quality = 85
            do {
                output.reset(); avatar.compress(Bitmap.CompressFormat.JPEG, quality, output); quality -= 10
            } while (output.size() > 24_000 && quality >= 15)
            require(output.size() <= 24_000) { "Choose a simpler picture." }
            return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        } finally {
            if (avatar !== source) avatar.recycle()
            if (cropped !== avatar && cropped !== source) cropped.recycle()
        }
    }
    fun decode(value: String): ByteArray? = if (value.isBlank()) null else runCatching { Base64.decode(value, Base64.NO_WRAP) }.getOrNull()
}

