package com.example.localtwouserapp

import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Feeds snapshots into the actual Plans screen without modifying any account or cloud data. */
@RunWith(AndroidJUnit4::class)
class CalendarUiTest {
    @Test fun plansCheckboxReflectsNewEntriesWithoutNavigatingAway() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        org.junit.Assume.assumeFalse("Unlock the phone for the calendar UI check",
            context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked)
        val entry = Entry(type = "event", date = LocalDate.now().toString(), text = "Checklist rendering test", fields = mapOf("todo" to "true", "at" to "21:30"))
        val entries = MutableStateFlow(listOf(entry))
        ActivityScenario.launch<PhotoFrameActivity>(Intent(context, PhotoFrameActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                activity.setContent { MaterialTheme(colorScheme = UsColors) {
                    CalendarPage(SharedStore.get(context), entries.collectAsState().value) { }
                } }
            }
            fun checkbox(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if (node == null) return null
                if (node.isCheckable) return node
                for (index in 0 until node.childCount) checkbox(node.getChild(index))?.let { return it }
                return null
            }
            fun awaitChecked(checked: Boolean) {
                val end = SystemClock.uptimeMillis() + 5000
                do {
                    instrumentation.waitForIdleSync()
                    val node = checkbox(instrumentation.uiAutomation.rootInActiveWindow)
                    if (node != null && node.isChecked == checked) return
                    SystemClock.sleep(100)
                } while (SystemClock.uptimeMillis() < end)
                fail("Plans checkbox did not update to checked=$checked")
            }
            awaitChecked(false)
            entries.value = listOf(entry.copy(done = true, version = 2))
            awaitChecked(true)
            entries.value = listOf(entry.copy(done = false, version = 3))
            awaitChecked(false)
        }
    }
}
