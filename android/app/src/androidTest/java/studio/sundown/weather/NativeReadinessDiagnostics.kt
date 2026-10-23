package studio.sundown.weather

import android.os.SystemClock
import org.json.JSONObject
import java.io.File

/** Test-only observer: joins an existing request, never starts an extra provider read. */
class NativeReadinessDiagnostics(private val name:String){
    private val began=SystemClock.elapsedRealtime()
    private val data=JSONObject().put("name",name).put("source",BuildConfig.SOURCE_COMMIT).put("epochMs",System.currentTimeMillis())
    fun observe(a:MainActivity){
        val pending=WeatherRepository::class.java.getDeclaredField("weatherJob").apply{isAccessible=true}.get(a.repository) as? NetworkJob
        if(pending==null||pending.cancelled){data.put("requestOutcome","no-pending-request");return}
        a.repository.refresh(a.model.place,false){weather,error->data.put("requestOutcome",error?:"success").put("responseAfterMs",SystemClock.elapsedRealtime()-began).put("responseFetchedAt",weather?.fetchedAt?:JSONObject.NULL)}
    }
    fun save(a:MainActivity):String{
        data.put("elapsedMs",SystemClock.elapsedRealtime()-began).put("modelStatus",a.model.status).put("weatherPresent",a.model.weather!=null).put("activityAlive",!a.isFinishing&&!a.isDestroyed).put("windowFocus",a.hasWindowFocus()).put("weatherFrames",a.pager.weatherPage.art.renderedFrames).put("weatherArtError",a.pager.weatherPage.art.error?:JSONObject.NULL).put("observationArtError",a.pager.observationPage.art.error?:JSONObject.NULL)
        File(a.getExternalFilesDir(null),"readiness-$name.json").writeText(data.toString(2));return data.toString()
    }
}
