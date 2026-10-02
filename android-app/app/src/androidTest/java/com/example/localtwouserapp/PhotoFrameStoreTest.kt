package com.example.localtwouserapp

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.Manifest
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.view.View
import android.widget.ImageView
import androidx.test.filters.SdkSuppress
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoFrameStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val first = 2_147_480_001
    private val second = 2_147_480_002
    private val restored = 2_147_480_003
    private lateinit var source: File
    @Before fun prepare() {
        source = File.createTempFile("frame-test-", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(1600, 800, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.GREEN)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @After fun clean() {
        PhotoFrameStore(context).use { store -> listOf(first, second, restored).forEach { store.delete(it) } }
        source.delete()
    }
    @Test fun savedCopySurvivesSourceDeletionAndDatabaseReopen() {
        PhotoFrameStore(context).use { it.save(first, Uri.fromFile(source), false) }
        assertTrue(source.delete())
        PhotoFrameStore(context).use { store ->
            val record = requireNotNull(store.get(first))
            assertFalse(record.fit)
            val bitmap = requireNotNull(store.thumbnail(record))
            assertEquals(480, bitmap.width)
            assertEquals(240, bitmap.height)
            assertTrue(bitmap.allocationByteCount < 1_000_000)
            bitmap.recycle()
        }
    }
    @Test fun deletingOneFrameLeavesOtherFramePhotoIntact() {
        PhotoFrameStore(context).use { store ->
            store.save(first, Uri.fromFile(source), false)
            store.save(second, Uri.fromFile(source), true)
            val one = requireNotNull(store.get(first)); val two = requireNotNull(store.get(second))
            assertNotEquals(one.filename, two.filename)
            store.delete(first)
            assertNull(store.get(first)); assertFalse(store.file(one).exists())
            assertTrue(store.file(two).exists()); assertTrue(requireNotNull(store.get(second)).fit)
        }
    }
    @Test fun fitChangeKeepsPhotoAndReplacementRemovesPreviousCopy() {
        PhotoFrameStore(context).use { store ->
            store.save(first, Uri.fromFile(source), false)
            val original = requireNotNull(store.get(first))
            store.save(first, null, true)
            assertEquals(original.filename, requireNotNull(store.get(first)).filename)
            assertTrue(requireNotNull(store.get(first)).fit)
            store.save(first, Uri.fromFile(source), true)
            assertNotEquals(original.filename, requireNotNull(store.get(first)).filename)
            assertFalse(store.file(original).exists())
        }
    }
    @Test fun invalidImageDoesNotDestroyExistingPhoto() {
        PhotoFrameStore(context).use { store ->
            store.save(first, Uri.fromFile(source), false)
            val original = requireNotNull(store.get(first))
            source.writeText("invalid image")
            assertTrue(runCatching { store.save(first, Uri.fromFile(source), true) }.isFailure)
            assertEquals(original, store.get(first))
            assertTrue(store.file(original).exists())
        }
    }
    @Test fun widgetIdRestorePreservesPhotoAndSettings() {
        PhotoFrameStore(context).use { store ->
            store.save(first, Uri.fromFile(source), true)
            val original = requireNotNull(store.get(first))
            store.restore(first, restored)
            assertNull(store.get(first))
            assertEquals(original, store.get(restored))
            assertTrue(store.file(original).exists())
        }
    }
    @Test @SdkSuppress(minSdkVersion = 29) fun allFourProvidersRenderTheSavedPhotoInBothModes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val host = AppWidgetHost(context, 86321)
        val manager = AppWidgetManager.getInstance(context)
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.BIND_APPWIDGET)
        try {
            PhotoFrameWidget.providers.forEach { provider ->
                val id = host.allocateAppWidgetId()
                try {
                    assertTrue(manager.bindAppWidgetIdIfAllowed(id, ComponentName(context, provider)))
                    PhotoFrameStore(context).use { store -> store.save(id, Uri.fromFile(source), false) }
                    for (fit in listOf(false, true)) {
                        PhotoFrameStore(context).use { it.save(id, null, fit) }
                        PhotoFrameWidget.update(context, id)
                        instrumentation.runOnMainSync {
                            val view = host.createView(context, id, manager.getAppWidgetInfo(id))
                            assertEquals(View.GONE, view.findViewById<View>(R.id.photo_placeholder).visibility)
                            val image = view.findViewById<ImageView>(if (fit) R.id.photo_fit else R.id.photo_fill)
                            assertEquals(View.VISIBLE, image.visibility)
                            assertNotNull(image.drawable)
                        }
                    }
                } finally {
                    PhotoFrameStore(context).use { it.delete(id) }
                    host.deleteAppWidgetId(id)
                }
            }
        } finally {
            host.deleteHost()
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }
}
