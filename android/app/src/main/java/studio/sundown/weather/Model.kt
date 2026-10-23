package studio.sundown.weather

import android.content.Context
import java.time.Instant
import java.util.Locale
import kotlin.math.*

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
        if(w==null)return tr(if(status=="loading")"正在获取天气…" else "天气不可用 · 点击地点重试",if(status=="loading")"Loading weather…" else "Unavailable · tap place to retry")
        val stale=w.stale(System.currentTimeMillis()/1000)||status=="offline"
        return tr(if(stale)"过期缓存 · Open-Meteo" else "Open-Meteo 模型",if(stale)"STALE · Open-Meteo" else "Open-Meteo")+" · "+w.clock(w.validAt)
    }
}

data class ObservationWindow(val start:Long,val end:Long,val available:Boolean)
fun observationWindow(w:Weather,purpose:Int):ObservationWindow {
    if(purpose !in 4..6)return ObservationWindow(w.validAt,w.validAt+7200,true)
    val date=Instant.ofEpochSecond(w.validAt).atZone(w.zone).toLocalDate()
    val rising=purpose==5;val startAngle=if(purpose==4)6.0 else if(purpose==5)-6.0 else -4.0;val endAngle=if(purpose==5)6.0 else -6.0
    for(i in 0L..2L){
        val day=date.plusDays(i)
        val start=Astronomy.crossings(day,w.place,startAngle).find{it.second==rising}?.first
        val end=Astronomy.crossings(day,w.place,endAngle).find{it.second==rising}?.first
        if(start!=null&&end!=null&&end>w.validAt&&end>start)return ObservationWindow(start,end,true)
    };return ObservationWindow(w.validAt,w.validAt,false)
}
fun interpretation(m:AppModel,w:Weather?,at:Long):String {
    if(w==null)return m.tr("天气数据暂不可用。请联网后选择地点重试。","Weather is unavailable. Connect and choose a place to retry.")
    val t=w.value("temperature_2m",at);val rh=w.value("relative_humidity_2m",at);val td=w.value("dew_point_2m",at)?:Physics.dew(t,rh)
    val spread=if(t!=null&&td!=null)t-td else null
    val wind=w.value("wind_gusts_10m",at);val rain=w.precip(at)?.second;val vis=w.value("visibility",at)
    val humid=spread!=null&&spread<3
    val lead=if(rain!=null&&rain>=60)m.tr("本时段降水机会较高。","A higher chance of precipitation. ") else if(t==null)m.tr("部分模型数据缺失。","Some model fields are missing. ") else if(t>=28)m.tr("近地气温偏暖。","Warm air near the ground. ") else if(t<=8)m.tr("近地气温较低。","Cool air near the ground. ") else m.tr("近地气温温和。","Mild air near the ground. ")
    val detail=when(m.purpose){
        10->if(humid)m.tr("空气接近饱和，湿衣物蒸发较慢；户外风雨会改变晾晒条件。","Near saturation, damp fabrics dry slowly; outdoor wind and rain affect drying.") else m.tr("蒸发受温度、湿度与通风共同影响；室内湿度没有测量。","Drying depends on warmth, humidity and ventilation. Indoor humidity is unmeasured.")
        11->if(humid)m.tr("较冷物品表面更易接近露点；湿物品带入室内会增加受潮机会，未测器材温度。","Cold item surfaces may approach the dew point. Damp items add moisture; equipment temperature is unmeasured.") else m.tr("物品表面降到露点附近才可能结露；纸张、织物与金属的含水状态未测。","Condensation is possible near the dew point. Paper, fabric and metal moisture is unmeasured.")
        4,5,6,7,8->if(humid)m.tr("镜片降温后可能接近露点，远景受云层与空气透明度影响。","Cooling lenses may approach the dew point; clouds and air clarity affect distant views.") else if(vis!=null&&vis<8000)m.tr("远景受低能见度影响；光照时段为几何近似，不含地形遮挡。","Low visibility mutes distant views. Light windows are geometric, without terrain obstruction.") else m.tr("云层影响光线与远景；光照时段为近似，天空亮度没有现场测量。","Clouds affect light and distant views. Light windows are approximate; sky brightness is unmeasured.")
        else->if(wind!=null&&wind>=35)m.tr("阵风较强；读数是模型估计，局地遮挡会改变风感。","Stronger gusts; model estimates cannot resolve local shelter.") else if(humid)m.tr("空气湿度较高，体感还受日照、风与活动强度影响。","Humid air; sun, wind and activity also affect how conditions feel.") else m.tr("体感还受日照、风与活动强度影响；当地情况可能不同。","Sun, wind and activity also affect comfort; local conditions may differ.")
    }
    return lead+detail
}
