package studio.sundown.weather

import android.content.SharedPreferences
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ExecutorService

class Feedback(private val prefs:SharedPreferences,private val pool:ExecutorService,private val backend:String){
    private val main=android.os.Handler(android.os.Looper.getMainLooper())
    private var job:NetworkJob?=null
    var draft:String
        get()=prefs.getString("feedback_draft","")?:""
        set(value){if(value!=draft)prefs.edit().putString("feedback_draft",value).putString("feedback_id",UUID.randomUUID().toString()).putString("feedback_version",BuildConfig.VERSION_NAME).apply()}
    private var deadline:Runnable?=null
    fun cancel(){deadline?.let(main::removeCallbacks);deadline=null;job?.cancel();job=null}
    fun send(callback:(Boolean,String)->Unit){
        cancel();val body=draft;val id=prefs.getString("feedback_id",null)?:UUID.randomUUID().toString().also{prefs.edit().putString("feedback_id",it).apply()}
        if(body.trim().isEmpty()||body.codePointCount(0,body.length)>1000){callback(false,"invalid");return}
        if(backend.isEmpty()){callback(false,"unconfigured");return}
        val version=prefs.getString("feedback_version",null)?:BuildConfig.VERSION_NAME.also{prefs.edit().putString("feedback_version",it).apply()}
        val j=NetworkJob();job=j
        deadline=Runnable{if(job===j){j.cancel();job=null;deadline=null;callback(false,"timeout")}}
        main.postDelayed(deadline!!,45000)
        j.future=pool.submit{try{
            val data=JSONObject(j.request("$backend/v1/feedback",JSONObject().put("body",body).put("version",version).put("requestId",id),maxBytes=65536,readTimeoutMs=30000))
            val receipt=data.getString("receipt");val pattern=Regex("https://github.com/dpan538/baro/issues/([1-9][0-9]*)");val match=pattern.matchEntire(receipt)?:error("Invalid receipt")
            require(data.getBoolean("accepted")&&data.getString("requestId")==id)
            val issue=JSONObject(j.request("https://api.github.com/repos/dpan538/baro/issues/${match.groupValues[1]}"))
            val digest=sha256((body+"\u0000"+version).toByteArray())
            require(issue.getString("html_url")==receipt&&!issue.has("pull_request")&&issue.getString("body").contains("<!-- BARO:$id:$digest -->"))
            main.post{if(!j.cancelled){deadline?.let(main::removeCallbacks);deadline=null;job=null;if(draft==body&&prefs.getString("feedback_id",null)==id)prefs.edit().putString("feedback_draft","").remove("feedback_id").remove("feedback_version").apply();prefs.edit().putString("feedback_receipt",receipt).apply();callback(true,receipt)}}
        }catch(e:Exception){main.post{if(!j.cancelled){deadline?.let(main::removeCallbacks);deadline=null;job=null;callback(false,e.message?:"Network unavailable")}}}}
    }
}
