package com.example.localtwouserapp

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.AtomicFile
import java.io.File
import java.util.UUID

data class FramePhoto(val filename: String, val fit: Boolean)

/** Widget photos and settings stay in this app's private storage, independently of Firebase. */
class PhotoFrameStore(context: Context) : SQLiteOpenHelper(context.applicationContext, "photo-frames.db", null, 1) {
    private val context = context.applicationContext
    private val directory = File(this.context.filesDir, "photo-frames").apply { mkdirs() }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE frames (widget_id INTEGER PRIMARY KEY, filename TEXT NOT NULL, fit INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun get(id: Int): FramePhoto? = readableDatabase.query("frames", arrayOf("filename", "fit"),
        "widget_id=?", arrayOf(id.toString()), null, null, null).use {
        if (it.moveToFirst()) FramePhoto(it.getString(0), it.getInt(1) != 0) else null
    }
    fun file(photo: FramePhoto) = File(directory, photo.filename)

    @Synchronized fun save(id: Int, source: Uri?, fit: Boolean) {
        require(id > 0)
        val old = get(id)
        var created: File? = null
        try {
            val filename = if (source != null) {
                val bitmap = PhotoDecoder.decode(context, source)
                val next = File(directory, UUID.randomUUID().toString() + ".jpg")
                created = next
                val atomic = AtomicFile(next)
                var stream: java.io.FileOutputStream? = null
                try {
                    stream = atomic.startWrite()
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)) { "Could not save this photo." }
                    atomic.finishWrite(stream)
                } catch (e: Exception) { atomic.failWrite(stream); throw e }
                finally { bitmap.recycle() }
                next.name
            } else old?.filename ?: error("Choose a photo first.")
            val values = ContentValues().apply {
                put("widget_id", id); put("filename", filename); put("fit", if (fit) 1 else 0)
            }
            writableDatabase.insertWithOnConflict("frames", null, values, SQLiteDatabase.CONFLICT_REPLACE).also {
                check(it != -1L) { "Could not save frame settings." }
            }
            if (old != null && old.filename != filename) deleteIfUnused(old)
        } catch (e: Exception) { created?.let { AtomicFile(it).delete() }; throw e }
    }
    @Synchronized fun delete(id: Int) {
        val old = get(id) ?: return
        writableDatabase.delete("frames", "widget_id=?", arrayOf(id.toString()))
        deleteIfUnused(old)
    }
    private fun deleteIfUnused(photo: FramePhoto) {
        val used = readableDatabase.rawQuery("SELECT 1 FROM frames WHERE filename=? LIMIT 1", arrayOf(photo.filename)).use { it.moveToFirst() }
        if (!used) AtomicFile(file(photo)).delete()
    }
    @Synchronized fun restore(oldId: Int, newId: Int) {
        val old = get(oldId) ?: return
        val displaced = get(newId)
        writableDatabase.beginTransaction()
        try {
            val values = ContentValues().apply { put("widget_id", newId); put("filename", old.filename); put("fit", if (old.fit) 1 else 0) }
            check(writableDatabase.insertWithOnConflict("frames", null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L)
            writableDatabase.delete("frames", "widget_id=?", arrayOf(oldId.toString()))
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
        displaced?.let { deleteIfUnused(it) }
    }
    fun thumbnail(photo: FramePhoto): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file(photo).path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 960) sample *= 2
        val decoded = BitmapFactory.decodeFile(file(photo).path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        return PhotoDecoder.scaled(decoded, 480)
    }
}
