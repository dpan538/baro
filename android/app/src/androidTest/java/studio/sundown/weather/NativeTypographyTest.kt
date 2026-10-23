package studio.sundown.weather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*

class NativeTypographyTest {
    @Test fun homeAndStorageForecastCaptionsNeverOverlapHeadingOrChart(){
        val i=InstrumentationRegistry.getInstrumentation();i.targetContext.getSharedPreferences("baro-native",Context.MODE_PRIVATE).edit().putBoolean("motion",false).commit()
        ActivityScenario.launch(MainActivity::class.java).use{s->s.onActivity{a->
            a.repository.cancelWeather();a.model.weather=WeatherJson.parse(i.context.assets.open("live-brisbane.json").bufferedReader().use{it.readText()},a.model.place)
            data class Run(val text:String,val rect:RectF)
            class Recorder:Canvas(Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888)){
                val runs=mutableListOf<Run>()
                override fun drawText(text:String,x:Float,y:Float,paint:Paint){runs.add(Run(text,RectF(x,y+paint.fontMetrics.ascent,x+paint.measureText(text),y+paint.fontMetrics.descent)));super.drawText(text,x,y,paint)}
            }
            for(lang in listOf("zh","en"))for(portrait in listOf(false,true))for(purpose in listOf(10,11)){
                a.model.lang=lang;a.model.purpose=purpose;val w=if(portrait)1136 else 1704;val h=if(portrait)1704 else 1136;val d=a.resources.displayMetrics.density
                val board=ObservationDashboard(a,a.model);board.layout(0,0,(w*d).toInt(),(h*d).toInt());val canvas=Recorder();board.draw(canvas)
                val metrics=DesignMetrics(a,"observation-${if(portrait)"portrait"else "landscape"}-$lang-$purpose");metrics.configure(w.toFloat(),h.toFloat(),a.root.paddingTop/d)
                val note=canvas.runs.single{it.text==a.model.tr("模型预报","Model forecast")}
                assertFalse("Forecast caption overlaps its heading: $lang/$portrait/$purpose",RectF.intersects(note.rect,metrics.rect("ins-focus:heading")))
                assertTrue(note.rect.bottom<metrics.rect("ins-focus:graphic").top);assertTrue(metrics.rect("ins-focus").contains(note.rect))
            }
        }}
    }
}
