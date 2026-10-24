package studio.sundown.weather

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class NativeAboutTest {
    private fun texts(v:View):List<TextView> = (if(v is TextView)listOf(v)else emptyList())+(if(v is ViewGroup)(0 until v.childCount).flatMap{texts(v.getChildAt(it))}else emptyList())
    private fun scroll(v:View):ScrollView?{if(v is ScrollView)return v;if(v is ViewGroup)for(n in 0 until v.childCount)scroll(v.getChildAt(n))?.let{return it};return null}
    @Test fun aboutOpensCompleteServiceAndRuntimeNoticesInBothLanguages(){
        val i=InstrumentationRegistry.getInstrumentation()
        for(lang in listOf("zh","en")){
            i.targetContext.getSharedPreferences("baro-native",Context.MODE_PRIVATE).edit().putString("language",lang).putBoolean("motion",false).commit()
            ActivityScenario.launch(MainActivity::class.java).use{s->s.onActivity{a->a.settings.open()};i.waitForIdleSync()
                s.onActivity{a->val all=texts(a.settings);assertEquals(7,all.count{it.tag=="baro-setting-index"});all.single{it.text.toString()==a.model.tr("关于","About")}.performClick()};SystemClock.sleep(350)
                s.onActivity{a->texts(a.settings).single{it.text.toString()==a.model.tr("数据来源 Open-Meteo · BARO","Powered by Open-Meteo · BARO")}.performClick()};i.waitForIdleSync()
                s.onActivity{a->val all=texts(a.settings);val text=all.joinToString("\n"){it.text};for(credit in listOf("Open-Meteo","CAMS","GeoNames","GitHub API","AndroidX","apksig","Kotlin","JetBrains","Guava","MiSans","Inter","Roboto Mono","SunCalc","Apache-2.0.txt","Runtime-dependencies.txt"))assertTrue("Missing About credit: $lang/$credit",text.contains(credit))
                    all.filter{it.layout!=null}.forEach{v->for(line in 0 until v.layout.lineCount)assertEquals("Ellipsis in About: ${v.text}",0,v.layout.getEllipsisCount(line))}
                    assertTrue(a.assets.open("licenses/Runtime-dependencies.txt").bufferedReader().readText().contains("kotlinx-coroutines"));a.settings.back();a.settings.close()
                }
            }
        }
    }
}
