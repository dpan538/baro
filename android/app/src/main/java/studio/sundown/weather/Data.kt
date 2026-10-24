package studio.sundown.weather

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

object WeatherJson {
    val units = mapOf("temperature_2m" to "°C","dew_point_2m" to "°C","apparent_temperature" to "°C","relative_humidity_2m" to "%","precipitation_probability" to "%","precipitation" to "mm","pressure_msl" to "hPa","visibility" to "m","cloud_cover" to "%","cloud_cover_low" to "%","cloud_cover_mid" to "%","cloud_cover_high" to "%","wind_speed_10m" to "km/h","wind_gusts_10m" to "km/h","wind_direction_10m" to "°","wind_speed_300hPa" to "km/h","wind_direction_300hPa" to "°","total_column_integrated_water_vapour" to "kg/m²","freezing_level_height" to "m","cape" to "J/kg","convective_inhibition" to "J/kg","soil_moisture_0_to_1cm" to "m³/m³","soil_temperature_0cm" to "°C","direct_radiation_instant" to "W/m²","diffuse_radiation_instant" to "W/m²","uv_index" to "","weather_code" to "wmo code","is_day" to "")
    fun numeric(o: JSONObject?, key: String): Double? = if(o==null||o.isNull(key)||!o.has(key))null else (o.opt(key) as? Number)?.toDouble()?.takeIf { it.isFinite() }
    private fun valid(key: String, n: Double?): Double? = n?.takeIf {
        when(key){"relative_humidity_2m","cloud_cover","cloud_cover_low","cloud_cover_mid","cloud_cover_high","precipitation_probability"->it in 0.0..100.0
            "is_day"->it==0.0||it==1.0
            "wind_direction_10m","wind_direction_300hPa"->it in 0.0..360.0
            "soil_moisture_0_to_1cm"->it in 0.0..1.0
            "visibility","wind_speed_10m","wind_speed_300hPa","wind_gusts_10m","precipitation","cape","total_column_integrated_water_vapour","uv_index","direct_radiation_instant","diffuse_radiation_instant"->it>=0
            else->true}
    }
    fun parse(body: String, place: Place): Weather {
        val root=JSONObject(body);require(root.getInt("schema")==1)
        require(abs(root.getDouble("latitude")-place.latitude)<=.011 && abs(root.getDouble("longitude")-place.longitude)<=.011)
        val wx=root.getJSONObject("forecast");val zone=wx.getString("timezone");ZoneId.of(zone)
        val cur=wx.getJSONObject("current");val cu=wx.getJSONObject("current_units")
        val current=units.keys.filter { cur.has(it) }.associateWith { key -> if(cu.optString(key,"?")==units[key]) valid(key,numeric(cur,key)) else null }
        require(cu.getString("time")=="unixtime")
        val h=wx.getJSONObject("hourly");val hu=wx.getJSONObject("hourly_units");require(hu.getString("time")=="unixtime")
        val times=h.getJSONArray("time").let { a -> (0 until a.length()).map { a.getLong(it) } };require(times.zipWithNext().all { it.first<it.second })
        val fields=units.keys.filter { h.has(it) }.associateWith { key ->
            val arr=h.getJSONArray(key);require(arr.length()==times.size)
            (0 until arr.length()).map { if(hu.optString(key,"?")!=units[key]||arr.isNull(it))null else valid(key,(arr.opt(it) as? Number)?.toDouble()?.takeIf { v->v.isFinite() }) }
        }
        val d=wx.getJSONObject("daily");val du=wx.getJSONObject("daily_units");require(du.getString("time")=="unixtime")
        fun dayNum(key:String,i:Int,unit:String):Double? = if(du.optString(key)!=unit)null else d.optJSONArray(key)?.let { a -> if(a.isNull(i))null else (a.opt(i) as? Number)?.toDouble()?.takeIf { it.isFinite() } }
        val days=(0 until d.getJSONArray("time").length()).map { i->
            val date=Instant.ofEpochSecond(d.getJSONArray("time").getLong(i)).atZone(ZoneId.of(zone)).toLocalDate()
            Day(date,dayNum("temperature_2m_min",i,"°C"),dayNum("temperature_2m_max",i,"°C"),dayNum("sunrise",i,"unixtime")?.toLong()?.takeIf { it>0 },dayNum("sunset",i,"unixtime")?.toLong()?.takeIf { it>0 })
        }
        val validAt=cur.getLong("time");val air=root.optJSONObject("air");val ac=air?.optJSONObject("current");val au=air?.optJSONObject("current_units");val airAt=numeric(ac,"time")?.toLong()
        val airValid=air?.optString("timezone")==zone&&au?.optString("time")=="unixtime"&&airAt!=null&&abs(airAt-validAt)<=3600&&!root.optBoolean("airStale")
        return Weather(place.copy(zone=zone),root.getLong("fetchedAt"),validAt,current,Series(times,fields),days,
            if(airValid&&au?.optString("us_aqi")=="USAQI")numeric(ac,"us_aqi")?.takeIf { it>=0 } else null,
            if(airValid&&au?.optString("pm2_5")=="μg/m³")numeric(ac,"pm2_5")?.takeIf { it>=0 } else null,airAt,root.optBoolean("stale"),airStale=root.optBoolean("airStale"))
    }
}

class NetworkJob {
    private val closed=AtomicBoolean(false)
    @Volatile private var connection: HttpURLConnection?=null
    var future: Future<*>?=null
    val cancelled get()=closed.get()||Thread.currentThread().isInterrupted
    fun cancel(){closed.set(true);connection?.disconnect();future?.cancel(true)}
    fun track(c:HttpURLConnection?){connection=c;if(cancelled)c?.disconnect()}
    fun request(url: String, body: JSONObject?=null, maxBytes: Int=3_000_000): String {
        check(!cancelled)
        val parsed=URL(url)
        require(parsed.protocol=="https" || (BuildConfig.DEBUG&&parsed.host in setOf("127.0.0.1","10.0.2.2","localhost")&&parsed.protocol=="http"))
        val c=parsed.openConnection() as HttpURLConnection;connection=c
        c.connectTimeout=8000;c.readTimeout=10000;c.instanceFollowRedirects=false
        c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","BARO/${BuildConfig.VERSION_NAME}")
        try {
            if(body!=null){c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.outputStream.use { it.write(body.toString().toByteArray()) }}
            val status=c.responseCode
            if(status!=200&&status!=201)throw java.io.IOException("HTTP $status")
            val bytes=c.inputStream.use { input -> val out=java.io.ByteArrayOutputStream();val buf=ByteArray(8192);while(true){if(cancelled)throw InterruptedException();val n=input.read(buf);if(n<0)break;out.write(buf,0,n);require(out.size()<=maxBytes)};out.toByteArray() }
            check(!cancelled);return String(bytes,Charsets.UTF_8)
        }finally{c.disconnect();connection=null}
    }
}

class WeatherRepository(context:Context,backendOrigin:String=BuildConfig.BACKEND_URL,
                        private val clock:()->Long={System.currentTimeMillis()/1000},
                        private val elapsed:()->Long={SystemClock.elapsedRealtime()/1000},
                        private val requestDeadlineMs:Long=WEATHER_REQUEST_SECONDS*1000) {
    companion object { private val memory=ForecastMemory() }
    private val context=context.applicationContext
    val pool=Executors.newFixedThreadPool(3)
    private val main=Handler(Looper.getMainLooper())
    val backend=backendOrigin.trimEnd('/')
    private val cache=File(context.cacheDir,if(backend.isEmpty())"weather"else "weather-"+java.security.MessageDigest.getInstance("SHA-256").digest(backend.toByteArray()).take(6).joinToString(""){"%02x".format(it)}).apply { mkdirs() }
    private var generation=0L
    private var weatherJob: NetworkJob?=null
    private var inFlightKey:String?=null
    private var attemptAt=0L
    private var weatherTimeout:Runnable?=null
    private var initialRead=false
    private val listeners=mutableListOf<(Weather?,String?)->Unit>()
    private fun key(place:Place)="$backend|${place.key}"
    fun cached(place:Place):Weather?{
        val k=key(place);val hot=memory.get(k,elapsed())
        if(hot!=null&&!hot.weather.stale(clock())){
            val weather=hot.weather
            return if(weather.place==place.copy(zone=weather.place.zone))weather else weather.copy(place=place.copy(zone=weather.place.zone))
        }
        if(hot!=null){memory.remove(k);File(cache,place.key+".json").delete();return null}
        return runCatching{
            val file=File(cache,place.key+".json");val raw=file.readText();val weather=WeatherJson.parse(raw,place)
            if(weather.stale(clock())){file.delete();null}else weather.also{memory.put(k,it,raw,elapsed())}
        }.getOrNull()
    }
    fun rawFor(place:Place):String?{if(cached(place)==null)return null;return memory.get(key(place),elapsed())?.raw}
    fun cancelWeather(){generation++;weatherTimeout?.let(main::removeCallbacks);weatherTimeout=null;weatherJob?.cancel();if(weatherJob!=null&&initialRead)inFlightKey?.let{memory.abandonInitial(it,attemptAt)};weatherJob=null;inFlightKey=null;listeners.clear()}
    private fun weatherDirectories()=context.cacheDir.listFiles()?.filter{it.isDirectory&&(it.name=="weather"||Regex("weather-[a-f0-9]{12}").matches(it.name))}?:emptyList()
    fun clear(){cancelWeather();memory.clear();weatherDirectories().forEach{folder->folder.listFiles()?.forEach{it.delete()}}}
    fun bytes()=weatherDirectories().sumOf{folder->folder.listFiles()?.sumOf{it.length()}?:0L}
    fun refresh(place: Place, force: Boolean=false, callback: (Weather?,String?)->Unit) {
        val k=key(place)
        if(inFlightKey!=null&&inFlightKey!=k)cancelWeather()
        val hadReading=memory.get(k,elapsed())!=null||File(cache,place.key+".json").exists()
        val available=cached(place)
        if(available!=null&&(!force||clock()-available.fetchedAt<MIN_READ_SECONDS)){callback(available,null);return}
        if(inFlightKey==k&&weatherJob?.cancelled==false){listeners.add(callback);return}
        cancelWeather();val g=generation
        if(!memory.mayRead(k,elapsed())){callback(available,"refresh_interval");return}
        attemptAt=elapsed();memory.attempted(k,attemptAt);listeners.add(callback);inFlightKey=k
        val job=NetworkJob();weatherJob=job;initialRead=!hadReading
        fun complete(weather:Weather?,error:String?){
            if(g!=generation||job.cancelled)return
            weatherTimeout?.let(main::removeCallbacks);weatherTimeout=null
            weatherJob=null;inFlightKey=null;val pending=listeners.toList();listeners.clear()
            pending.forEach{it(weather,error)}
        }
        weatherTimeout=Runnable{if(g==generation&&!job.cancelled){complete(cached(place),"request_timeout");job.cancel()}}
        main.postDelayed(weatherTimeout!!,requestDeadlineMs)
        job.future=pool.submit {
            try {
                val body=if(backend.isNotEmpty())job.request("$backend/v1/weather?lat=${place.latitude}&lon=${place.longitude}") else direct(job,place)
                val weather=WeatherJson.parse(body,place)
                require(!weather.stale(clock())){"source_timestamp"}
                if(job.cancelled)return@submit
                main.post { if(g==generation&&!job.cancelled){
                    if(weather.stale(clock())){complete(cached(place),"source_timestamp");return@post}
                    memory.put(k,weather,body,elapsed())
                    runCatching{val target=File(cache,place.key+".json");val tmp=File(cache,place.key+".tmp");tmp.writeText(body);if(!tmp.renameTo(target))tmp.delete()}
                    cache.listFiles()?.filter { it.extension=="json" }?.sortedByDescending { it.lastModified() }?.drop(MAX_CACHED_PLACES)?.forEach { it.delete() }
                    complete(weather,null)
                } };return@submit
            }catch(e:Exception){if(!job.cancelled)main.post{complete(cached(place),e.message?:"Network unavailable")}}
        }
    }
    private fun direct(job: NetworkJob, place: Place): String {
        val common="latitude=${place.latitude}&longitude=${place.longitude}&timezone=auto&timeformat=unixtime&past_days=1&forecast_days=3"
        val current="temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m,uv_index,visibility"
        val hourly=WeatherJson.units.keys.joinToString(",")
        val wx=JSONObject(job.request("https://api.open-meteo.com/v1/forecast?$common&wind_speed_unit=kmh&current=$current&hourly=$hourly&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset"))
        val air=try{JSONObject(job.request("https://air-quality-api.open-meteo.com/v1/air-quality?$common&current=us_aqi,pm2_5"))}catch(_:Exception){null}
        return JSONObject().put("schema",1).put("latitude",place.latitude).put("longitude",place.longitude).put("fetchedAt",System.currentTimeMillis()/1000).put("forecast",wx).put("air",air).toString()
    }
    fun search(q: String, lang: String, callback: (List<Place>?,String?)->Unit): NetworkJob {
        val job=NetworkJob();job.future=pool.submit {try{
            val encoded=URLEncoder.encode(q,"UTF-8")
            val url=if(backend.isNotEmpty())"$backend/v1/places?q=$encoded&lang=$lang" else "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=8&language=$lang&format=json"
            val arr=JSONObject(job.request(url)).optJSONArray("results")
            val places=(0 until (arr?.length()?:0)).map { val o=arr!!.getJSONObject(it);Place(listOf(o.getString("name"),o.optString("admin1"),o.optString("country")).filter { it.isNotBlank() }.distinct().joinToString(" · "),o.getDouble("latitude"),o.getDouble("longitude"),o.optString("timezone","UTC")) }
            main.post{if(!job.cancelled)callback(places,null)}
        }catch(e:Exception){main.post{if(!job.cancelled)callback(null,e.message)}}};return job
    }
    fun close(){cancelWeather();pool.shutdownNow()}
}
