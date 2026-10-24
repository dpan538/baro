package studio.sundown.weather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Exact preview APK: real weather/GLES and native Settings in both languages
 * and orientations. Controlled long notes are identified separately. */
class NativePreviewAcceptanceTest {
    private val i = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = i.targetContext
    private val records = JSONArray()
    private fun texts(v: View): List<TextView> = (if (v is TextView) listOf(v) else emptyList()) +
        (if (v is ViewGroup) (0 until v.childCount).flatMap { texts(v.getChildAt(it)) } else emptyList())
    private fun pause(ms: Long = 350) { i.waitForIdleSync();SystemClock.sleep(ms) }
    private fun click(s: ActivityScenario<MainActivity>, label: String) {
        s.onActivity { a -> texts(a.settings).first { it.text.toString() == label }.performClick() };pause()
    }
    private fun audit(s: ActivityScenario<MainActivity>) {
        s.onActivity { a ->
            for (v in texts(a.settings).filter { it.visibility == View.VISIBLE && it.layout != null }) {
                val l = v.layout
                for (line in 0 until l.lineCount) {
                    assertEquals("Ellipsis: ${v.text}", 0, l.getEllipsisCount(line))
                    assertTrue("Horizontal text clipping: ${v.text}", l.getLineMax(line) <= v.width - v.compoundPaddingLeft - v.compoundPaddingRight + 2)
                }
                assertTrue("Vertical text clipping: ${v.text}", l.height <= v.height - v.compoundPaddingTop - v.compoundPaddingBottom + 2)
            }
        }
    }
    private fun capture(s: ActivityScenario<MainActivity>, name: String, settings: Boolean = true, controlled: Boolean = false) {
        pause(500)
        val image = i.uiAutomation.takeScreenshot()!!
        File(ctx.getExternalFilesDir(null), "preview-$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        records.put(JSONObject().put("file", "preview-$name.png").put("width", image.width).put("height", image.height).put("controlledLongNotes", controlled))
        image.recycle()
        if(settings)audit(s)
    }
    private fun present(s: ActivityScenario<MainActivity>, page: Int) {
        s.onActivity { a -> a.model.page = page;a.redraw() };pause(700)
        var left = 0;var y = 0
        s.onActivity { a -> val r = Rect();a.root.getGlobalVisibleRect(r);left = r.left + 3;y = r.centerY() }
        val expected = if (page == 0) PAPER else Color.rgb(11, 11, 10)
        val deadline = SystemClock.uptimeMillis() + 12000;var shown = false
        while (!shown && SystemClock.uptimeMillis() < deadline) {
            val b = i.uiAutomation.takeScreenshot()!!;val p = b.getPixel(left, y);b.recycle()
            shown = kotlin.math.abs(Color.red(p) - Color.red(expected)) < 3 && kotlin.math.abs(Color.green(p) - Color.green(expected)) < 3 && kotlin.math.abs(Color.blue(p) - Color.blue(expected)) < 3
            if (!shown) SystemClock.sleep(150)
        }
        assertTrue("Actual compositor has not presented page $page", shown)
    }
    @Test fun twoPagesSettingsAndLiveReleaseCheckAcrossLanguageAndOrientation() {
        try {
            for (portrait in listOf(false, true)) for (lang in listOf("zh", "en")) {
                i.uiAutomation.executeShellCommand("wm user-rotation lock ${if(portrait) 1 else 0}").use { fd ->
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
                }
                SystemClock.sleep(1000)
                ctx.getSharedPreferences("baro-native", Context.MODE_PRIVATE).edit().clear().putString("language", lang).putBoolean("motion", false).commit()
                ActivityScenario.launch(MainActivity::class.java).use { s ->
                    val until = SystemClock.uptimeMillis() + 30000;var ready = false
                    while (!ready && SystemClock.uptimeMillis() < until) {
                        s.onActivity { a -> ready = a.model.status == "ready" && a.model.weather != null && (a.pager.height > a.pager.width) == portrait && a.pager.weatherPage.art.renderedFrames > 0 }
                        SystemClock.sleep(150)
                    }
                    assertTrue("Real API, orientation and GLES must be ready", ready)
                    val prefix = "${if (portrait) "portrait" else "landscape"}-$lang"
                    s.onActivity { a -> File(ctx.getExternalFilesDir(null), "preview-$prefix-live.json").writeText(a.repository.rawFor(a.model.place)!!) }
                    for (page in 0..1) { present(s, page);capture(s, "$prefix-page$page", false) }
                    present(s,0)
                    s.onActivity { it.settings.open() };pause()
                    s.onActivity { a ->
                        val pane=a.settings.findViewWithTag<View>("baro-settings-pane")!!;val r=Rect();val art=Rect();pane.getGlobalVisibleRect(r);a.pager.weatherPage.art.getGlobalVisibleRect(art)
                        if(portrait)assertTrue("Portrait Settings must end at the living sky",kotlin.math.abs(r.bottom-art.top)<=3)
                        else assertTrue("Landscape Settings must end at the living sky",kotlin.math.abs(r.right-art.left)<=3)
                    }
                    capture(s, "$prefix-appearance")
                    s.onActivity{a->
                        fun find(v:View):View?{if(v.contentDescription?.toString()==a.model.tr("天气动画","Weather animation"))return v;if(v is ViewGroup)for(n in 0 until v.childCount)find(v.getChildAt(n))?.let{return it};return null}
                        val toggle=find(a.settings)!!;val group=toggle.parent.parent as View;group.requestRectangleOnScreen(Rect(0,0,group.width,group.height),true)
                    }
                    capture(s,"$prefix-appearance-scrolled")
                    click(s, if (lang == "zh") "单位" else "Units");capture(s, "$prefix-units")
                    click(s, if (lang == "zh") "关于" else "About");capture(s, "$prefix-about")
                    s.onActivity { a -> assertEquals(7, texts(a.settings).count { it.tag=="baro-setting-index" }) }
                    click(s, if (lang == "zh") "检查更新" else "Check updates")
                    var message = "";val checked = SystemClock.uptimeMillis() + 20000
                    while (SystemClock.uptimeMillis() < checked) {
                        s.onActivity { a -> message = (SettingsPanel::class.java.getDeclaredField("updateMessage").apply { isAccessible = true }.get(a.settings) as TextView).text.toString() }
                        if (message.contains("尚无发布包") || message.contains("No release") || message.contains("最新版本") || message.contains("latest version")) break
                        SystemClock.sleep(150)
                    }
                    assertTrue("Actual GitHub API check failed: $message", message.contains("尚无发布包") || message.contains("No release") || message.contains("最新版本") || message.contains("latest version"))
                    capture(s, "$prefix-update-live")
                    s.onActivity { a ->
                        val card=a.settings.findViewWithTag<View>("baro-update-card")!!;val r=Rect();val art=Rect();card.getGlobalVisibleRect(r);a.pager.weatherPage.art.getGlobalVisibleRect(art)
                        assertTrue("Update card must stay over the living sky: card=$r, sky=$art",r.left>=art.left&&r.top>=art.top&&r.right<=art.right&&r.bottom<=art.bottom)
                        assertEquals(card.height,r.height())
                    }
                    s.onActivity { a ->
                        val messageView = SettingsPanel::class.java.getDeclaredField("updateMessage").apply { isAccessible = true }.get(a.settings) as TextView
                        messageView.text = (if (lang == "zh") "较长发布说明，验证滚动区域与安装按钮始终可用。\n" else "Long release notes to verify scrolling and accessible installation controls.\n").repeat(55)
                    }
                    pause();capture(s, "$prefix-update-long-notes", controlled = true)
                    s.onActivity { a ->
                        val button = texts(a.settings).single { it.text.toString()==a.model.tr("查看发布页 ↗","View releases ↗") }
                        val r = Rect();assertTrue(button.getGlobalVisibleRect(r));assertEquals(button.height, r.height())
                        a.settings.back()
                    };pause()
                    click(s, if (lang == "zh") "数据来源 Open-Meteo · BARO" else "Powered by Open-Meteo · BARO");capture(s, "$prefix-credits")
                    s.onActivity { it.settings.close() }
                }
            }
        } finally {
            File(ctx.getExternalFilesDir(null), "preview-acceptance.json").writeText(JSONObject().put("captures", records).put("publicDownloadVerified", false).toString(2))
            i.uiAutomation.executeShellCommand("wm user-rotation lock 0").close()
        }
    }
}
