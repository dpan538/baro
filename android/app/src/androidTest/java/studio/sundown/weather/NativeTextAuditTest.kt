package studio.sundown.weather

import android.content.Context
import android.graphics.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Audits actual glyph ink against the active Canvas clip, including StaticLayout text runs. */
class NativeTextAuditTest {
    @Test fun everyPurposeLanguageOrientationDrawsWholeGlyphs(){
        val i=InstrumentationRegistry.getInstrumentation();i.targetContext.getSharedPreferences("baro-native",Context.MODE_PRIVATE).edit().putBoolean("motion",false).commit()
        val results=JSONArray();var failures=0
        ActivityScenario.launch(MainActivity::class.java).use{s->s.onActivity{a->
            a.repository.cancelWeather();val weather=WeatherJson.parse(i.context.assets.open("live-brisbane.json").bufferedReader().use{it.readText()},a.model.place)
            val bitmap=Bitmap.createBitmap(3408,4260,Bitmap.Config.ARGB_8888)
            val originalTop=a.root.paddingTop
            val landscapeTop=a.window.decorView.rootWindowInsets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.statusBars()).top
            class Recorder:Canvas(bitmap){
                val issues=JSONArray();var texts=0
                val runs=mutableListOf<Pair<String,RectF>>()
                var wheelClip:RectF?=null
                fun record(value:String,x:Float,y:Float,p:Paint){
                    if(value.isBlank()||p.alpha==0)return;texts++
                    val ink=Rect();p.getTextBounds(value,0,value.length,ink);val advance=p.measureText(value);val left=x-when(p.textAlign){Paint.Align.RIGHT->advance;Paint.Align.CENTER->advance/2;else->0f};val b=RectF(ink).apply{offset(left,y)};val clip=RectF(clipBounds)
                    val matrix=Matrix();getMatrix(matrix);val values=FloatArray(9);matrix.getValues(values)
                    val rotating=kotlin.math.abs(values[Matrix.MSKEW_X])>.001f
                    if(!rotating){val global=RectF(b);matrix.mapRect(global);runs.add(value to global)}
                    if(!rotating&&!clip.contains(RectF(b).apply{inset(.1f,.1f)}))issues.put(JSONObject().put("text",value).put("ink",b.toString()).put("clip",clip.toString()))
                    if(value in purposesZh||value in purposesEn){val global=RectF(b);matrix.mapRect(global);val visible=wheelClip!!;if(!visible.contains(RectF(global).apply{inset(.1f,.1f)}))issues.put(JSONObject().put("wheelText",value).put("ink",global.toString()).put("clip",visible.toString()))}
                    // These two complete source strings use an intentional progress mark, not truncation.
                    if(value.contains('…')&&value !in listOf("正在获取天气…","Loading weather…"))issues.put(JSONObject().put("text",value).put("reason","ellipsis"))
                }
                override fun drawText(text:String,x:Float,y:Float,paint:Paint){record(text,x,y,paint);super.drawText(text,x,y,paint)}
                override fun drawText(text:CharArray,index:Int,count:Int,x:Float,y:Float,paint:Paint){record(String(text,index,count),x,y,paint);super.drawText(text,index,count,x,y,paint)}
                override fun drawTextRun(text:CharSequence,start:Int,end:Int,contextStart:Int,contextEnd:Int,x:Float,y:Float,isRtl:Boolean,paint:Paint){record(text.subSequence(start,end).toString(),x,y,paint);super.drawTextRun(text,start,end,contextStart,contextEnd,x,y,isRtl,paint)}
                override fun drawTextRun(text:CharArray,index:Int,count:Int,contextIndex:Int,contextCount:Int,x:Float,y:Float,isRtl:Boolean,paint:Paint){record(String(text,index,count),x,y,paint);super.drawTextRun(text,index,count,contextIndex,contextCount,x,y,isRtl,paint)}
            }
            for(lang in listOf("zh","en"))for(profile in listOf(Triple(false,1704,1136),Triple(true,1136,1704),Triple(true,909,1363)))for(missing in listOf(false,true))for(purpose in 0..11){
                val (portrait,w,h)=profile
                a.model.lang=lang;a.model.purpose=purpose;a.model.weather=if(missing)null else weather
                val d=a.resources.displayMetrics.density
                val insetPx=if(portrait)0 else landscapeTop;a.root.setPadding(a.root.paddingLeft,insetPx,a.root.paddingRight,a.root.paddingBottom)
                val board=ObservationDashboard(a,a.model);board.layout(0,0,(w*d).toInt(),(h*d).toInt()-insetPx);val canvas=Recorder();canvas.clipRect(0,0,board.width,board.height)
                val m=DesignMetrics(a,"observation-${if(portrait)"portrait"else "landscape"}-$lang-$purpose");val scale=board.coordinateScale;m.configure(board.w,board.h,insetPx/scale);canvas.wheelClip=m.rect("ins-wheel").apply{top+=16;bottom-=44;left*=scale;top*=scale;right*=scale;bottom*=scale};board.draw(canvas)
                for(x in canvas.runs.indices)for(y in x+1 until canvas.runs.size){val one=canvas.runs[x];val two=canvas.runs[y];val intersection=RectF(one.second);if(intersection.intersect(two.second)&&intersection.width()>2*d&&intersection.height()>minOf(one.second.height(),two.second.height())*.22f)canvas.issues.put(JSONObject().put("overlap",one.first+" / "+two.first).put("intersection",intersection.toString()))}
                results.put(JSONObject().put("language",lang).put("portrait",portrait).put("widthDp",w).put("heightDp",h).put("purpose",purpose).put("missing",missing).put("textRuns",canvas.texts).put("issues",canvas.issues));failures+=canvas.issues.length()
                val dial=board.dialRect;val start=android.os.SystemClock.uptimeMillis();val tooth=maxOf(38f,(dial.height()*.70f*Math.toRadians(8.5)).toFloat())
                for((action,delta)in listOf(android.view.MotionEvent.ACTION_DOWN to 0f,android.view.MotionEvent.ACTION_MOVE to tooth*.38f)){
                    val event=android.view.MotionEvent.obtain(start,start+20,action,dial.centerX()*scale,(dial.centerY()-delta)*scale,0);board.onTouchEvent(event);event.recycle()
                }
                val partial=Recorder();partial.clipRect(0,0,board.width,board.height);partial.wheelClip=RectF(canvas.wheelClip);board.draw(partial)
                results.put(JSONObject().put("language",lang).put("portrait",portrait).put("widthDp",w).put("heightDp",h).put("purpose",purpose).put("missing",missing).put("dialPhase",.38).put("textRuns",partial.texts).put("issues",partial.issues));failures+=partial.issues.length()
            }
            for(lang in listOf("zh","en"))for(portrait in listOf(false,true))for(stress in listOf(false,true)){
                a.model.lang=lang;a.model.weather=if(stress)weather.copy(current=weather.current+mapOf("weather_code" to 99.0,"temperature_2m" to -40.0,"wind_speed_10m" to 250.0,"apparent_temperature" to -55.0,"uv_index" to 15.0))else weather
                a.model.place=weather.place.copy(name=if(stress)"South Brisbane · Kangaroo Point · Queensland"else weather.place.name)
                val d=a.resources.displayMetrics.density;val w=if(portrait)1136 else 1704;val h=if(portrait)1704 else 1136
                val insetPx=if(portrait)0 else landscapeTop;a.root.setPadding(a.root.paddingLeft,insetPx,a.root.paddingRight,a.root.paddingBottom)
                val board=WeatherDashboard(a,a.model);board.layout(0,0,(w*d).toInt(),(h*d).toInt()-insetPx);val canvas=Recorder();canvas.clipRect(0,0,board.width,board.height);board.draw(canvas)
                for(x in canvas.runs.indices)for(y in x+1 until canvas.runs.size){val one=canvas.runs[x];val two=canvas.runs[y];val intersection=RectF(one.second);if(intersection.intersect(two.second)&&intersection.width()>2*d&&intersection.height()>minOf(one.second.height(),two.second.height())*.22f)canvas.issues.put(JSONObject().put("overlap",one.first+" / "+two.first))}
                results.put(JSONObject().put("language",lang).put("portrait",portrait).put("weather",true).put("stress",stress).put("textRuns",canvas.texts).put("issues",canvas.issues));failures+=canvas.issues.length()
            }
            a.root.setPadding(a.root.paddingLeft,originalTop,a.root.paddingRight,a.root.paddingBottom)
            bitmap.recycle()
        }}
        File(i.targetContext.getExternalFilesDir(null),"text-audit.json").writeText(results.toString(2))
        assertEquals("Glyph clipping/ellipsis found; inspect text-audit.json",0,failures)
    }
}
