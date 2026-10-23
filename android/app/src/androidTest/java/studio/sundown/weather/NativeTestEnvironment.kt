package studio.sundown.weather

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry

/** CPU-only hosted UI checks; never packaged in the app or used for product captures. */
object NativeTestEnvironment {
    val uiOnly get()=InstrumentationRegistry.getArguments().getString("uiOnly")=="true"
    private var installed=false
    fun install(){
        if(!uiOnly||installed)return
        installed=true
        val i=InstrumentationRegistry.getInstrumentation()
        val raw=i.context.assets.open("live-brisbane.json").bufferedReader().use{it.readText()}
        (i.targetContext.applicationContext as Application).registerActivityLifecycleCallbacks(object:Application.ActivityLifecycleCallbacks{
            override fun onActivityCreated(activity:Activity,state:Bundle?){
                if(activity !is MainActivity)return
                val arts=listOf(activity.pager.weatherPage.art,activity.pager.observationPage.art)
                arts.forEach{it.surfaceTextureListener=null;it.visibility=View.INVISIBLE;it.dispose()}
                activity.root.viewTreeObserver.addOnPreDrawListener{arts.forEach{it.visibility=View.INVISIBLE};true}
            }
            override fun onActivityResumed(activity:Activity){
                if(activity !is MainActivity)return
                activity.repository.cancelWeather();activity.model.weather=WeatherJson.parse(raw,activity.model.place);activity.model.status="offline";activity.redraw()
            }
            override fun onActivityStarted(activity:Activity){}
            override fun onActivityPaused(activity:Activity){}
            override fun onActivityStopped(activity:Activity){}
            override fun onActivitySaveInstanceState(activity:Activity,state:Bundle){}
            override fun onActivityDestroyed(activity:Activity){}
        })
    }
}
