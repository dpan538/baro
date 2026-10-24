package studio.sundown.weather

import android.graphics.*
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Source and availability belong in information areas, never over the illustration. */
class NativeArtworkTest {
    @Test fun artworkHasNoBadgeInReadyStaleLoadingOrMissingStates(){
        val i=InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use{s->s.onActivity{a->
            a.repository.cancelWeather()
            val weather=WeatherJson.parse(i.context.assets.open("live-brisbane.json").bufferedReader().use{it.readText()},a.model.place)
            fun hasCaption(v:View):Boolean=v is TextView||(v is ViewGroup&&(0 until v.childCount).any{hasCaption(v.getChildAt(it))})
            assertFalse(hasCaption(a.pager.weatherPage));assertFalse(hasCaption(a.pager.observationPage))
            val bitmap=Bitmap.createBitmap(3408,3408,Bitmap.Config.ARGB_8888)
            for(lang in listOf("zh","en"))for(portrait in listOf(false,true))for(status in listOf("ready","offline","loading","unavailable")){
                a.model.lang=lang;a.model.status=status;a.model.weather=if(status in listOf("ready","offline"))weather else null
                for(board in listOf(WeatherDashboard(a,a.model),ObservationDashboard(a,a.model))){
                    val d=a.resources.displayMetrics.density
                    board.layout(0,0,((if(portrait)1136 else 1704)*d).toInt(),((if(portrait)1704 else 1136)*d).toInt())
                    val ink=mutableListOf<Pair<String,RectF>>()
                    val canvas=object:Canvas(bitmap){
                        override fun drawText(text:String,x:Float,y:Float,paint:Paint){
                            val bounds=Rect();paint.getTextBounds(text,0,text.length,bounds)
                            val b=RectF(bounds).apply{offset(x,y)};val matrix=Matrix();getMatrix(matrix);matrix.mapRect(b)
                            if(paint.alpha>0&&text.isNotBlank())ink.add(text to b)
                            super.drawText(text,x,y,paint)
                        }
                    }
                    board.draw(canvas)
                    val art=RectF(board.artRect).apply{val scale=board.coordinateScale;left*=scale;right*=scale;top*=scale;bottom*=scale}
                    assertTrue("$lang/$portrait/$status: text over artwork: "+ink.filter{RectF.intersects(it.second,art)},ink.none{RectF.intersects(it.second,art)})
                }
            }
            bitmap.recycle()
        }}
    }
}
