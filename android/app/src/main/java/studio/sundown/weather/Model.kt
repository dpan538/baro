package studio.sundown.weather

import android.content.Context

val purposesZh=listOf("通勤","步行","骑行","户外停留","晚霞拍摄","晨光拍摄","蓝调拍摄","夜空观测","远眺","露营","居家建议","储存建议")
val purposesEn=listOf("COMMUTE","WALKING","CYCLING","OUTDOORS","SUNSET PHOTO","DAWN PHOTO","BLUE HOUR","STARGAZING","DISTANT VIEW","CAMPING","HOME TIPS","STORAGE TIPS")
class AppModel(context:Context){
    val prefs=context.getSharedPreferences("baro-native",Context.MODE_PRIVATE)
    var lang:String
        get()=prefs.getString("language","zh")?:"zh"
        set(v){prefs.edit().putString("language",v).apply()}
    var block:Boolean
        get()=prefs.getBoolean("block",false)
        set(v){prefs.edit().putBoolean("block",v).apply()}
    var motion:Boolean
        get()=prefs.getBoolean("motion",true)
        set(v){prefs.edit().putBoolean("motion",v).apply()}
    var fahrenheit:Boolean
        get()=prefs.getBoolean("fahrenheit",false)
        set(v){prefs.edit().putBoolean("fahrenheit",v).apply()}
    var wind:String
        get()=prefs.getString("wind","kmh")?:"kmh"
        set(v){prefs.edit().putString("wind",v).apply()}
    var page:Int
        get()=prefs.getInt("page",0).coerceIn(0,1)
        set(v){prefs.edit().putInt("page",v).apply()}
    var purpose:Int
        get()=prefs.getInt("purpose",0).coerceIn(0,11)
        set(v){prefs.edit().putInt("purpose",v.coerceIn(0,11)).apply()}
    var place:Place
        get()=runCatching{Place(prefs.getString("place_name",tr("布里斯班","Brisbane"))!!,Double.fromBits(prefs.getLong("lat",(-27.47).toBits())),Double.fromBits(prefs.getLong("lon",153.03.toBits())),prefs.getString("zone","Australia/Brisbane")!!)}.getOrDefault(Place("Brisbane",-27.47,153.03,"Australia/Brisbane"))
        set(v){prefs.edit().putString("place_name",v.name).putLong("lat",v.latitude.toBits()).putLong("lon",v.longitude.toBits()).putString("zone",v.zone).apply()}
    init { prefs.edit().remove("demo").remove("demo_scene").apply() }
    fun placeName()=when(place.name){"布里斯班","Brisbane"->tr("布里斯班","Brisbane");"当前位置","Current location"->tr("当前位置","Current location");else->place.name}
    var weather:Weather?=null
    var status="loading"
    var locationStatus="off"
    var now=System.currentTimeMillis()/1000
    private var windowKey=""
    private var cachedWindow:ObservationWindow?=null
    private var lightKey=""
    private var cachedLight:LightTarget?=null
    fun light(w:Weather):LightTarget?{
        val key="${w.place.key}:${w.place.zone}:$purpose:${now/60}"
        if(key!=lightKey||cachedLight?.let{it.epoch<=now}==true){cachedLight=lightTarget(w,purpose,now,window(w));lightKey=key}
        return cachedLight
    }
    fun window(w:Weather):ObservationWindow {
        if(purpose !in 4..6)return observationWindow(w,purpose,now)
        val key="${w.place.latitude},${w.place.longitude},${w.place.zone}:$purpose:${now/60}"
        val cached=cachedWindow
        if(key==windowKey&&cached!=null&&cached.end>now)return cached
        return observationWindow(w,purpose,now).also{windowKey=key;cachedWindow=it}
    }
    fun tr(zh:String,en:String)=if(lang=="zh")zh else en
    val tempUnit get()=if(fahrenheit)"°F" else "°C"
    val windUnit get()=when(wind){"ms"->"m/s";"mph"->"mph";"kts"->"kts";else->"km/h"}
    fun temp(t:Double?,digits:Int=0,delta:Boolean=false)=Units.number(Units.temperature(t,fahrenheit,delta),digits)
    fun speed(v:Double?,digits:Int=0)=Units.number(Units.wind(v,wind),digits)
    fun purposeName(i:Int=purpose)=if(lang=="zh")purposesZh[i] else purposesEn[i]
    fun conditionName(w:Weather?):String {
        return when(w?.value("weather_code")?.toInt()){
            0->tr("晴朗","Clear skies");1->tr("晴间少云","Mainly clear");2->tr("晴间多云","Partly cloudy");3->tr("阴天","Overcast");45->tr("雾","Fog");48->tr("雾凇","Rime fog")
            51->tr("小毛毛雨","Light drizzle");53->tr("毛毛雨","Drizzle");55->tr("浓毛毛雨","Dense drizzle");56->tr("冻毛毛雨","Freezing drizzle");57->tr("强冻毛毛雨","Heavy freezing drizzle")
            61->tr("小雨","Light rain");63->tr("雨","Rain");65->tr("大雨","Heavy rain");66->tr("冻雨","Freezing rain");67->tr("强冻雨","Heavy freezing rain")
            71->tr("小雪","Light snow");73->tr("雪","Snow");75->tr("大雪","Heavy snow");77->tr("米雪","Snow grains");80->tr("小阵雨","Light showers");81->tr("阵雨","Showers");82->tr("强阵雨","Violent showers");85->tr("阵雪","Snow showers");86->tr("强阵雪","Heavy snow showers")
            95->tr("雷暴","Thunderstorm");96->tr("雷暴伴冰雹","Thunderstorm with hail");99->tr("强雷暴伴冰雹","Thunderstorm with heavy hail");else->tr("暂无天气","Unavailable")
        }
    }
    fun source():String {
        val w=weather
        if(w==null)return tr(if(status=="loading")"正在更新天气…" else "天气暂不可用 · 自动重试中",if(status=="loading")"Updating weather…" else "Weather unavailable · retrying")
        return tr("Open-Meteo 模型","Open-Meteo")+" · "+w.clock(w.validAt)
    }
}

fun interpretation(m:AppModel,w:Weather?,at:Long):String =
    conditionsAdvice(w,m.purpose,at,m.lang,w?.let{m.window(it).available}?:true).text
