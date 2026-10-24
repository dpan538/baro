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
    @Test fun compactForecastCaptionsNeverOverlapHeadingReadingOrChart(){
        val i=InstrumentationRegistry.getInstrumentation();i.targetContext.getSharedPreferences("baro-native",Context.MODE_PRIVATE).edit().putBoolean("motion",false).commit()
        ActivityScenario.launch(MainActivity::class.java).use{s->s.onActivity{a->
            a.repository.cancelWeather();a.model.weather=WeatherJson.parse(i.context.assets.open("live-brisbane.json").bufferedReader().use{it.readText()},a.model.place)
            data class Run(val text:String,val rect:RectF,val size:Float)
            class Recorder:Canvas(Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888)){
                val runs=mutableListOf<Run>()
                val strokes=mutableListOf<RectF>()
                override fun drawText(text:String,x:Float,y:Float,paint:Paint){val width=paint.measureText(text);val left=x-when(paint.textAlign){Paint.Align.RIGHT->width;Paint.Align.CENTER->width/2;else->0f};val ink=android.graphics.Rect();paint.getTextBounds(text,0,text.length,ink);runs.add(Run(text,RectF(ink).apply{offset(left,y)},paint.textSize));super.drawText(text,x,y,paint)}
                override fun drawLine(x:Float,y:Float,xx:Float,yy:Float,paint:Paint){val edge=paint.strokeWidth/2;strokes.add(RectF(minOf(x,xx)-edge,minOf(y,yy)-edge,maxOf(x,xx)+edge,maxOf(y,yy)+edge));super.drawLine(x,y,xx,yy,paint)}
            }
            for(lang in listOf("zh","en"))for(portrait in listOf(false,true))for(purpose in listOf(2,8,10,11)){
                a.model.lang=lang;a.model.purpose=purpose;val w=if(portrait)1136 else 1704;val h=if(portrait)1704 else 1136;val d=a.resources.displayMetrics.density
                val board=ObservationDashboard(a,a.model);board.layout(0,0,(w*d).toInt(),(h*d).toInt());val canvas=Recorder();board.draw(canvas)
                val metrics=DesignMetrics(a,"observation-${if(portrait)"portrait"else "landscape"}-$lang-$purpose");metrics.configure(w.toFloat(),h.toFloat(),a.root.paddingTop/d)
                val module=metrics.rect("ins-focus")
                if(purpose==2){
                    val direction=canvas.runs.single{module.contains(it.rect)&&it.text.startsWith(a.model.tr("来向","From"))}
                    assertFalse("Wind ticks overlap direction footer: $lang/$portrait",canvas.strokes.any{module.contains(it)&&RectF.intersects(it,direction.rect)})
                    continue
                }
                val note=canvas.runs.single{module.contains(it.rect)&&it.text==a.model.tr("逐小时模型","Hourly model")}
                assertTrue("Source caption must retain readable type",note.size>=16f)
                assertFalse("Forecast caption overlaps actual heading/reading/facts: $lang/$portrait/$purpose",canvas.runs.any{it!==note&&module.contains(it.rect)&&RectF.intersects(note.rect,it.rect)})
                assertTrue(module.contains(note.rect))
            }
        }}
    }
}
