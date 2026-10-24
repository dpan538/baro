package studio.sundown.weather

import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.*

const val CACHE_SECONDS = 1800L
const val MEMORY_IDLE_SECONDS = 3600L
const val MIN_READ_SECONDS = 300L

/** Continuous travel with hysteresis at each tooth, including wraparound and reversal. */
class DialDetents(initial:Int=0){
    var position=initial.toFloat();private set
    var selected=initial;private set
    private var origin=initial
    private var start=position
    fun begin(index:Int){origin=index;selected=index;position=index.toFloat();start=position}
    fun move(steps:Float):List<Int>{
        if(!steps.isFinite())return emptyList()
        position=(start+steps).coerceIn(origin-24f,origin+24f)
        val crossed=mutableListOf<Int>()
        while(position>selected+.58f){selected++;crossed.add(selected)}
        while(position<selected-.58f){selected--;crossed.add(selected)}
        return crossed
    }
    fun visual(value:Float){position=value}
    fun cancel(){selected=origin;position=origin.toFloat()}
    val index get()=((selected%12)+12)%12
}

/** Process memory only; persisted JSON remains the separately bounded offline layer. */
class ForecastMemory(private val capacity:Int=12){
    data class Entry(val weather:Weather,val raw:String,var usedAt:Long)
    private val entries=LinkedHashMap<String,Entry>(16,.75f,true)
    private val attempts=LinkedHashMap<String,Long>()
    @Synchronized fun get(key:String,elapsed:Long):Entry?{
        entries.entries.removeAll{elapsed-it.value.usedAt>=MEMORY_IDLE_SECONDS}
        return entries[key]?.also{it.usedAt=elapsed}
    }
    @Synchronized fun put(key:String,weather:Weather,raw:String,elapsed:Long){
        entries[key]=Entry(weather,raw,elapsed)
        while(entries.size>capacity)entries.remove(entries.keys.first())
    }
    @Synchronized fun mayRead(key:String,elapsed:Long)=attempts[key]?.let{elapsed-it>=MIN_READ_SECONDS}?:true
    @Synchronized fun attempted(key:String,elapsed:Long){
        attempts.remove(key);attempts[key]=elapsed
        while(attempts.size>64)attempts.remove(attempts.keys.first())
    }
    @Synchronized fun abandonInitial(key:String,attempt:Long){if(entries[key]==null&&attempts[key]==attempt)attempts.remove(key)}
    @Synchronized fun clear(){entries.clear();attempts.clear()}
    @Synchronized fun count()=entries.size
}

data class Place(val name: String, val latitude: Double, val longitude: Double, val zone: String = "UTC") {
    init { require(latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0) }
    val key: String get() = String.format(java.util.Locale.US, "%.2f,%.2f", latitude, longitude)
}

data class Series(val times: List<Long>, val fields: Map<String, List<Double?>>) {
    fun at(key: String, epoch: Long): Double? {
        val index = times.indexOfLast { it <= epoch }
        return if (index < 0 || epoch - times[index] >= 3600) null else fields[key]?.getOrNull(index)
    }
    fun ending(key: String, epoch: Long): Pair<Long, Double?>? {
        val i = times.indexOfFirst { it > epoch }
        return if (i < 0 || times[i] - epoch > 3600) null else times[i] to fields[key]?.getOrNull(i)
    }
    fun range(key: String, start: Long, end: Long): List<Pair<Long, Double?>> = times.indices.filter { times[it] in start..end }.map { times[it] to fields[key]?.getOrNull(it) }
}

data class Day(val date: LocalDate, val low: Double?, val high: Double?, val sunrise: Long?, val sunset: Long?)
data class Weather(val place: Place, val fetchedAt: Long, val validAt: Long, val current: Map<String, Double?>,
                   val hourly: Series, val days: List<Day>, val aqi: Double?, val pm25: Double?, val airAt: Long?,
                   val serverStale: Boolean = false, val airStale: Boolean = false) {
    val zone: ZoneId get() = ZoneId.of(place.zone)
    fun value(key: String, at: Long = validAt): Double? = if (at == validAt && current.containsKey(key)) current[key] else hourly.at(key, at)
    fun today(at: Long = validAt) = days.find { it.date == Instant.ofEpochSecond(at).atZone(zone).toLocalDate() }
    fun stale(now: Long) = (serverStale || now - fetchedAt >= CACHE_SECONDS || now - validAt > 7200 || fetchedAt > now + 300)
    fun clock(at: Long?) = at?.let { DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochSecond(it).atZone(zone)) } ?: "—"
    fun nextSolar(at: Long = validAt): Pair<Boolean, Long>? = days.flatMap { listOfNotNull(it.sunrise?.let { t -> true to t }, it.sunset?.let { t -> false to t }) }.filter { it.second > at }.minByOrNull { it.second }
    fun precip(at: Long = validAt) = hourly.ending("precipitation_probability", at)
    fun difference(key: String, at: Long, hours: Int): Double? {
        val a = hourly.at(key, at); val b = hourly.at(key, at-hours*3600)
        return if (a == null || b == null) null else a-b
    }
    fun scene(at: Long = validAt): String {
        val code = value("weather_code", at)?.toInt() ?: return "unknown"
        val day = value("is_day", at)?.let { it == 1.0 } ?: (Astronomy.sun(at, place).elevation > -.833)
        return when (code) {
            95,96,99 -> "storm"
            71,73,75,77,85,86 -> "snow"
            51,53,55,56,57,61,63,65,66,67,80,81,82 -> "rain"
            45,48 -> "fog"
            3 -> "overcast"
            0,1,2 -> when {
                !day -> "night"
                (value("cloud_cover", at) ?: 100.0) <= 50 && today(at)?.sunrise?.let { abs(at-it) <= 1800 } == true -> "dawn"
                (value("cloud_cover", at) ?: 100.0) <= 50 && today(at)?.sunset?.let { abs(at-it) <= 1800 } == true -> "sunset"
                (value("wind_speed_10m", at) ?: 0.0) >= 28 -> "wind"
                code == 0 -> "clear"
                else -> "partly"
            }
            else -> "unknown"
        }
    }
    /** An ongoing light window can start between model hours. Prefer the actual
     * current WMO observation there, rather than a different hourly condition. */
    fun atmosphereTime(selected:Long)=if(abs(selected-validAt)<3600)validAt else selected
    fun rainStrength(at:Long):Double {
        if(scene(at) !in setOf("rain","storm"))return 0.0
        val floor=if(scene(at)=="storm").65 else .35
        return max(floor,(value("precipitation",at)?:0.0)/3).coerceIn(0.0,1.0)
    }
}

data class ObservationWindow(val start:Long,val end:Long,val available:Boolean)
fun validLocationFix(latitude:Double,longitude:Double,ageMs:Long,accuracyMetres:Double?):Boolean =
    latitude.isFinite()&&longitude.isFinite()&&latitude in -90.0..90.0&&longitude in -180.0..180.0&&ageMs in 0..120_000&&
    (accuracyMetres==null||(accuracyMetres.isFinite()&&accuracyMetres in 0.0..50_000.0))
fun observationWindow(w:Weather,purpose:Int,reference:Long=w.validAt):ObservationWindow {
    if(purpose !in 4..6)return ObservationWindow(w.validAt,w.validAt+7200,true)
    val date=Instant.ofEpochSecond(reference).atZone(w.zone).toLocalDate()
    val rising=purpose==5
    val startAngle=when(purpose){4->6.0;5->-6.0;else->-4.0};val endAngle=if(rising)6.0 else -6.0
    for(offset in -1L..2L){
        val day=date.plusDays(offset)
        val starts=Astronomy.crossings(day,w.place,startAngle)
        val start=starts.find{it.second==rising}?.first?:continue
        val nextStarts=starts+Astronomy.crossings(day.plusDays(1),w.place,startAngle)
        val reset=nextStarts.filter{it.first>start&&it.second!=rising}.minByOrNull{it.first}?.first
        val end=(Astronomy.crossings(day,w.place,endAngle)+Astronomy.crossings(day.plusDays(1),w.place,endAngle))
            .filter{it.first>start&&it.second==rising}.minByOrNull{it.first}?.first?:continue
        if(end>reference&&(reset==null||end<reset))return ObservationWindow(start,end,true)
    }
    return ObservationWindow(reference,reference,false)
}
data class LightTarget(val epoch:Long,val kind:String)
fun lightTarget(w:Weather,purpose:Int,now:Long,selected:ObservationWindow?=null):LightTarget? {
    if(purpose in 4..6){
        val window=selected?:observationWindow(w,purpose,now)
        if(!window.available||window.end<=now)return null
        val ongoing=now>=window.start
        return LightTarget(if(ongoing)window.end else window.start,if(ongoing)"end"else when(purpose){5->"dawn";6->"blue";else->"golden"})
    }
    val date=Instant.ofEpochSecond(now).atZone(w.zone).toLocalDate()
    val event=(0L..2L).flatMap{Astronomy.crossings(date.plusDays(it),w.place,6.0)}.filter{it.first>now}.minByOrNull{it.first}?:return null
    return LightTarget(event.first,"golden")
}

object Physics {
    fun dew(t: Double?, rh: Double?): Double? {
        if (t == null || rh == null || rh <= 0 || rh > 100 || t <= -243.04) return null
        val a = ln(rh/100) + 17.625*t/(243.04+t)
        return 243.04*a/(17.625-a)
    }
    fun vpd(t: Double?, rh: Double?): Double? = if (t == null || rh == null || rh !in 0.0..100.0 || t <= -237.3) null else .6108*exp(17.27*t/(t+237.3))*(1-rh/100)
    fun wetBulb(t: Double?, rh: Double?): Double? {
        if (t == null || rh == null) return null
        if (rh == 100.0) return t
        if (t !in -20.0..50.0 || rh !in 5.0..99.0) return null
        val tw = t*atan(.151977*sqrt(rh+8.313659)) + atan(t+rh) - atan(rh-1.676331) + .00391838*rh.pow(1.5)*atan(.023101*rh) - 4.686035
        return tw.coerceIn(dew(t,rh) ?: t, t)
    }
    fun lcl(t: Double?, td: Double?) = if(t == null || td == null) null else max(0.0,125*(t-td))
    fun windDelta(a: Double?, af: Double?, b: Double?, bf: Double?): Double? {
        if (listOf(a, af, b, bf).any { it == null }) return null
        val ar = Math.toRadians(af!!); val br = Math.toRadians(bf!!)
        return hypot(a!!*sin(ar)-b!!*sin(br),a*cos(ar)-b*cos(br))
    }
    fun glow(high: Double?, mid: Double?, low: Double?, visibility: Double?, humidity: Double?): Double? {
        if (listOf(high, mid, low, visibility, humidity).any { it == null }) return null
        return (20+(1-abs(high!!-50)/55).coerceIn(0.0,1.0)*29+(mid!!/30).coerceIn(0.0,1.0)*10+(visibility!!/25000).coerceIn(0.0,1.0)*25-low!!*.48-max(0.0,humidity!!-85)*.35).coerceIn(0.0,100.0)
    }
}

data class Sun(val elevation: Double, val azimuth: Double)
data class Moon(val age: Double, val fraction: Double, val illumination: Double)
object Astronomy {
    private fun rad(x: Double) = Math.toRadians(x)
    fun wrap(x: Double) = ((x%360)+360)%360
    fun sun(epoch: Long, place: Place): Sun {
        val n=epoch/86400.0+2440587.5-2451545
        val mean=rad(wrap(280.46+.9856474*n)); val anomaly=rad(wrap(357.528+.9856003*n))
        val ecliptic=mean+rad(1.915*sin(anomaly)+.020*sin(2*anomaly)); val obliquity=rad(23.439-.0000004*n)
        val ascension=Math.toDegrees(atan2(cos(obliquity)*sin(ecliptic),cos(ecliptic)))
        val declination=asin(sin(obliquity)*sin(ecliptic)); val hour=rad(wrap(280.46061837+360.98564736629*n+place.longitude-ascension)); val lat=rad(place.latitude)
        return Sun(Math.toDegrees(asin(sin(lat)*sin(declination)+cos(lat)*cos(declination)*cos(hour))),wrap(Math.toDegrees(atan2(sin(hour),cos(hour)*sin(lat)-tan(declination)*cos(lat)))+180))
    }
    fun moon(epoch: Long): Moon {
        val age=((epoch/86400.0-10962.7597222222)%29.530588853+29.530588853)%29.530588853
        val phase=age/29.530588853
        return Moon(age,phase,(1-cos(phase*2*PI))/2)
    }
    // Low precision SunCalc lunar position; BSD notice bundled with source provenance.
    fun moonAltitude(epoch: Long, place: Place): Double {
        val d=epoch/86400.0+2440587.5-2451545
        val l=rad(218.316+13.176396*d); val m=rad(134.963+13.064993*d); val f=rad(93.272+13.229350*d)
        val lon=l+rad(6.289)*sin(m); val lat=rad(5.128)*sin(f); val e=rad(23.4397)
        val ra=atan2(sin(lon)*cos(e)-tan(lat)*sin(e),cos(lon))
        val dec=asin(sin(lat)*cos(e)+cos(lat)*sin(e)*sin(lon))
        val hour=rad(280.16+360.9856235*d+place.longitude)-ra; val phi=rad(place.latitude)
        val alt=asin(sin(phi)*sin(dec)+cos(phi)*cos(dec)*cos(hour))
        val h=max(0.0,alt)
        return Math.toDegrees(alt+.0002967/tan(h+.00312536/(h+.08901179)))-.133
    }
    fun crossings(date: LocalDate, place: Place, altitude: Double, lunar: Boolean=false): List<Pair<Long,Boolean>> {
        val zone=ZoneId.of(place.zone); val start=date.atStartOfDay(zone).toEpochSecond(); val end=date.plusDays(1).atStartOfDay(zone).toEpochSecond()
        fun height(t: Long)=if(lunar) moonAltitude(t,place) else sun(t,place).elevation-altitude
        val result=mutableListOf<Pair<Long,Boolean>>(); var prev=start; var a=height(start)
        var at=start+300
        while(at<=end){val b=height(at); if((a<0)!=(b<0)){var lo=prev;var hi=at;repeat(12){val mid=(lo+hi)/2;if((height(lo)<0)==(height(mid)<0))lo=mid else hi=mid};result.add((lo+hi)/2 to (b>a))};prev=at;a=b;at+=300}
        return result
    }
}

object Units {
    fun temperature(c: Double?, fahrenheit: Boolean, delta: Boolean=false) = c?.let { if(fahrenheit) it*1.8+(if(delta)0 else 32) else it }
    fun wind(kmh: Double?, unit: String)=kmh?.let { it*when(unit){"ms"->1/3.6;"mph"->1/1.609344;"kts"->1/1.852;else->1.0} }
    fun number(x: Double?, digits: Int=0): String = if(x==null || !x.isFinite()) "—" else String.format(java.util.Locale.US,"%.${digits}f",if(abs(x)<.5/10.0.pow(digits))0.0 else x)
}

class SwipeDecision(private val slop: Float, private val edge: Float) {
    var locked=false;private set
    var rejected=false;private set
    private var x=0f;private var y=0f
    fun begin(px: Float, py: Float, width: Float, excluded: Boolean){x=px;y=py;locked=false;rejected=excluded||px<edge||px>width-edge}
    fun move(px: Float, py: Float): Float {
        if(rejected)return 0f
        val dx=px-x;val dy=py-y
        if(!locked&&hypot(dx,dy)>slop){if(abs(dx)>abs(dy)*1.35f)locked=true else rejected=true}
        return if(locked)dx else 0f
    }
    fun finish(dx: Float, velocity: Float, width: Float, cancelled: Boolean): Boolean = !cancelled&&!rejected&&locked&&(abs(dx)>=max(48f,width*.16f)||(abs(dx)>slop*2&&abs(velocity)>700&&dx*velocity>0))
    fun cancel(){rejected=true;locked=false}
}
