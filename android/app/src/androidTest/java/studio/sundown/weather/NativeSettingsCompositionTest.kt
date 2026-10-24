package studio.sundown.weather

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Native GLES and a genuine system IME; no public feedback is sent. */
class NativeSettingsCompositionTest {
    private val i=InstrumentationRegistry.getInstrumentation()
    private fun find(v:View,predicate:(View)->Boolean):View? {
        if(predicate(v))return v
        if(v is ViewGroup)for(n in 0 until v.childCount)find(v.getChildAt(n),predicate)?.let{return it}
        return null
    }
    @Test fun exposedWeatherKeepsRenderingAndImeResizeRetainsTheFeedbackEditor(){
        i.uiAutomation.executeShellCommand("wm user-rotation lock 1").close();SystemClock.sleep(1000)
        val old=android.provider.Settings.Secure.getString(i.targetContext.contentResolver,"show_ime_with_hard_keyboard")
        i.uiAutomation.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1").close()
        val draft="Native IME draft 草稿 retained"
        i.targetContext.getSharedPreferences("baro-native",Context.MODE_PRIVATE).edit().clear().putString("language","en").putBoolean("motion",true).putInt("page",0).commit()
        try{
            ActivityScenario.launch(MainActivity::class.java).use{s->
                var ready=false;val deadline=SystemClock.uptimeMillis()+30000
                while(!ready&&SystemClock.uptimeMillis()<deadline){s.onActivity{ready=it.model.weather!=null&&it.pager.weatherPage.art.renderedFrames>0};SystemClock.sleep(100)}
                assertTrue("Real weather and GLES required",ready)
                var before=0L;s.onActivity{a->a.settings.open();before=a.pager.weatherPage.art.renderedFrames};SystemClock.sleep(400)
                var moving=false;val until=SystemClock.uptimeMillis()+10000
                while(!moving&&SystemClock.uptimeMillis()<until){s.onActivity{a->assertTrue(a.pager.overlayOpen);assertTrue(a.pager.weatherPage.art.active);moving=a.pager.weatherPage.art.renderedFrames>before+2};SystemClock.sleep(100)}
                assertTrue("Visible sky must keep animating under partial Settings",moving)
                s.onActivity{a->find(a.settings){it is TextView&&it.text.toString()=="···"}!!.performClick()};i.waitForIdleSync()
                s.onActivity{a->find(a.settings){it is TextView&&it.text.toString()=="Feedback"}!!.performClick()};i.waitForIdleSync()
                lateinit var editor:EditText
                s.onActivity{a->editor=find(a.settings){it is EditText} as EditText;editor.setText(draft);editor.requestFocus();(a.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(editor,InputMethodManager.SHOW_IMPLICIT)}
                var keyboard=false;val imeDeadline=SystemClock.uptimeMillis()+12000
                while(!keyboard&&SystemClock.uptimeMillis()<imeDeadline){s.onActivity{keyboard=it.root.rootWindowInsets?.isVisible(WindowInsets.Type.ime())==true};SystemClock.sleep(100)}
                assertTrue("Real Android keyboard must open",keyboard);SystemClock.sleep(600)
                s.onActivity{a->
                    assertSame("IME resize must not rebuild the focused editor",editor,find(a.settings){it is EditText})
                    assertEquals(draft,a.feedback.draft)
                    val pane=a.settings.findViewWithTag<View>("baro-settings-pane")!!;val r=Rect();pane.getGlobalVisibleRect(r);val root=Rect();a.root.getGlobalVisibleRect(root)
                    assertTrue("Resized Settings must stay inside available app space",r.bottom<=root.bottom)
                    (a.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(editor.windowToken,0)
                }
                SystemClock.sleep(500);s.onActivity{it.settings.close()};SystemClock.sleep(250)
                s.recreate();i.waitForIdleSync();s.onActivity{assertEquals(draft,it.feedback.draft);assertFalse(it.pager.overlayOpen)}
            }
        }finally{
            i.uiAutomation.executeShellCommand(if(old==null)"settings delete secure show_ime_with_hard_keyboard"else"settings put secure show_ime_with_hard_keyboard $old").close()
            i.uiAutomation.executeShellCommand("wm user-rotation lock 0").close()
        }
    }
}
