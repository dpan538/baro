package studio.sundown.weather

import android.content.Context
import android.graphics.*
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.roundToInt

/** Check composed tile pixels: a radius in metadata alone does not make a visible corner. */
class NativeSurfaceTest {
    @Test fun instrumentCornersRemainVisibleAndSurfacesIncreaseContrast(){
        val i=InstrumentationRegistry.getInstrumentation()
        i.targetContext.getSharedPreferences("baro-native",Context.MODE_PRIVATE).edit().putBoolean("motion",false).commit()
        val bitmap=Bitmap.createBitmap(3408,4260,Bitmap.Config.ARGB_8888)
        val results=JSONArray();val issues=mutableListOf<String>()
        ActivityScenario.launch(MainActivity::class.java).use{s->s.onActivity{a->
            a.repository.cancelWeather()
            val weather=WeatherJson.parse(i.context.assets.open("live-brisbane.json").bufferedReader().use{it.readText()},a.model.place)
            a.model.weather=weather;a.model.now=weather.validAt
            val originalTop=a.root.paddingTop
            val landscapeTop=a.window.decorView.rootWindowInsets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.statusBars()).top
            for(lang in listOf("zh","en"))for(profile in listOf(Triple(false,1704,1136),Triple(true,1136,1704),Triple(true,909,2130)))for(purpose in 0..11){
                val (portrait,w,h)=profile;a.model.lang=lang;a.model.purpose=purpose
                val d=a.resources.displayMetrics.density;val inset=if(portrait)0 else landscapeTop;a.root.setPadding(0,inset,0,0)
                val board=ObservationDashboard(a,a.model);board.layout(0,0,(w*d).toInt(),(h*d).toInt()-inset)
                bitmap.eraseColor(Color.MAGENTA);board.draw(Canvas(bitmap))
                val m=DesignMetrics(a,"observation-${if(portrait)"portrait"else "landscape"}-$lang-$purpose");m.configure(w.toFloat(),h-inset/d,inset/d)
                val names=listOf("ins-focus","ins-thermal","ins-optical","ins-freezing","ins-signal","ins-cape","ins-rain","ins-rain-total","ins-wind","ins-pressure","ins-dew","ins-moisture","ins-clouds","ins-column","ins-soil","ins-distance","ins-solar","ins-bearing","ins-radiation","ins-stage","ins-lunar","ins-drift","ins-seeing","ins-stability","ins-micro","ins-advice")
                fun pixel(x:Float,y:Float)=bitmap.getPixel((x*d).roundToInt(),(y*d).roundToInt())
                for(name in names){
                    val b=m.rect(name);val center=pixel(b.centerX(),b.top+1)
                    val corners=listOf(pixel(b.left+.7f,b.top+.7f),pixel(b.right-.7f,b.top+.7f),pixel(b.right-.7f,b.bottom-.7f),pixel(b.left+.7f,b.bottom-.7f))
                    val visible=corners.count{it!=center}
                    if(visible!=4)issues+="$lang/$w/$purpose/$name: $visible visible corners"
                    results.put(JSONObject().put("language",lang).put("widthDp",w).put("purpose",purpose).put("component",name).put("surfacePixel",center).put("visibleCorners",visible))
                }
                val frame=pixel(1f,1f);val wind=m.rect("ins-wind");val dark=pixel(wind.centerX(),wind.top+1)
                val moon=m.rect("ins-lunar");val paper=pixel(moon.centerX(),moon.top+1)
                if(frame!=Color.rgb(11,11,10)||dark!=Color.rgb(24,24,23)||paper!=Color.rgb(255,255,251))issues+="$lang/$w/$purpose unexpected composed palette"
                if(ColorUtils.calculateContrast(paper,dark)<17.5)issues+="$lang/$w/$purpose insufficient primary contrast"
            }
            a.root.setPadding(0,originalTop,0,0)
        }}
        bitmap.recycle()
        File(i.targetContext.getExternalFilesDir(null),"surface-audit.json").writeText(JSONObject().put("cases",results).put("issues",JSONArray(issues)).toString())
        assertTrue(issues.take(20).joinToString("\n"),issues.isEmpty())
    }
}
