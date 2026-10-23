package studio.sundown.weather

import android.content.*
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.android.apksig.ApkVerifier
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import kotlin.math.*

fun sha256(bytes: ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
fun fileHash(file:File):String {val md=MessageDigest.getInstance("SHA-256");file.inputStream().use { val b=ByteArray(65536);while(true){val n=it.read(b);if(n<0)break;md.update(b,0,n)} };return md.digest().joinToString(""){"%02x".format(it)}}
data class Release(val code:Long,val version:String,val sha:String,val signer:String,val size:Long,val url:String,val notes:String,val preview:Boolean) {
    fun json()=JSONObject().put("versionCode",code).put("versionName",version).put("sha256",sha).put("certificateSha256",signer).put("size",size).put("url",url).put("notes",notes).put("preview",preview).put("packageName","studio.sundown.weather")
    companion object {
        fun parse(o:JSONObject,notes:String="",preview:Boolean=false):Release {
            require(o.getString("packageName")=="studio.sundown.weather")
            val code=o.getLong("versionCode");val size=o.getLong("size");val sha=o.getString("sha256");val signer=o.getString("certificateSha256");val url=o.getString("url")
            require(code in 1..Int.MAX_VALUE&&size in 1..536870912&&Regex("[a-f0-9]{64}").matches(sha)&&Regex("[a-f0-9]{64}").matches(signer)&&assetUrl(url))
            return Release(code,o.getString("versionName").take(80),sha,signer,size,url,notes.take(4000),preview)
        }
        fun assetUrl(url:String):Boolean=runCatching{val u=URL(url);u.protocol=="https"&&u.host=="github.com"&&u.port==-1&&u.userInfo==null&&u.path.startsWith("/dpan538/baro/releases/download/")&&u.query==null}.getOrDefault(false)
    }
}

class Updates(private val context:Context,private val pool:ExecutorService) {
    private val main=android.os.Handler(android.os.Looper.getMainLooper())
    private var generation=0
    private var job:NetworkJob?=null
    private val dir=File(context.cacheDir,"updates").apply{mkdirs()}
    val apk=File(dir,"candidate.apk")
    var selected:Release?=null;private set
    var installing=false
    @Synchronized fun cancel(){generation++;job?.cancel();job=null;dir.listFiles()?.filter{it.name.startsWith("download-")&&it.extension=="part"}?.forEach{it.delete()}}
    private fun signer():String {
        val info=context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_SIGNING_CERTIFICATES)
        val certs=info.signingInfo!!.apkContentsSigners;require(certs.size==1);return sha256(certs[0].toByteArray())
    }
    fun check(preview:Boolean, callback:(Release?,String)->Unit){
        cancel();val gen=generation;val j=NetworkJob();job=j
        j.future=pool.submit{try{
            val body=j.request("https://api.github.com/repos/dpan538/baro/releases"+(if(preview)"?per_page=30" else "/latest"))
            val releases=if(preview)JSONArray(body) else JSONArray().put(JSONObject(body))
            val found=mutableListOf<Release>();var incomplete=false
            for(i in 0 until releases.length()){
                val r=releases.getJSONObject(i);if(r.optBoolean("draft")||(!preview&&r.optBoolean("prerelease")))continue
                val assets=r.optJSONArray("assets")?:continue
                val manifest=(0 until assets.length()).map{assets.getJSONObject(it)}.find{it.optString("name")=="baro-update.json"}
                if(manifest==null){incomplete=true;continue}
                val link=manifest.getString("browser_download_url");require(Release.assetUrl(link))
                val m=JSONObject(fetchAsset(j,link,65536));val item=Release.parse(m,r.optString("body"),r.optBoolean("prerelease"))
                require((0 until assets.length()).map { assets.getJSONObject(it).optString("browser_download_url") }.contains(item.url))
                if(item.code>BuildConfig.VERSION_CODE)found.add(item)
            }
            val release=found.maxByOrNull{it.code}
            main.post{if(gen==generation&&!j.cancelled){selected=release;callback(release,when{release!=null->"available";releases.length()==0->"empty";incomplete->"unversioned";else->"current"})}}
        }catch(e:Exception){main.post{if(gen==generation&&!j.cancelled)callback(null,if(e.message=="HTTP 404")"empty" else "error: ${e.message}")}}}
    }
    private fun redirected(j:NetworkJob,url:String):HttpURLConnection {
        var link=url
        repeat(6){
            check(!j.cancelled)
            val u=URL(link);require(u.protocol=="https"&&u.userInfo==null&&u.port==-1&&u.host in setOf("github.com","objects.githubusercontent.com","release-assets.githubusercontent.com"))
            val c=u.openConnection() as HttpURLConnection;j.track(c);c.instanceFollowRedirects=false;c.connectTimeout=8000;c.readTimeout=12000;c.setRequestProperty("User-Agent","BARO/${BuildConfig.VERSION_NAME}")
            val code=c.responseCode
            if(code in setOf(301,302,303,307,308)){val next=c.getHeaderField("Location")?:error("Missing redirect");c.disconnect();link=URL(u,next).toString()}
            else{if(code!=200){c.disconnect();error("HTTP $code")};return c}
        };error("Too many redirects")
    }
    private fun fetchAsset(j:NetworkJob,url:String,max:Int):String {
        require(Release.assetUrl(url));val c=redirected(j,url)
        try{return c.inputStream.use{input->val out=java.io.ByteArrayOutputStream();val buf=ByteArray(8192);while(true){check(!j.cancelled);val n=input.read(buf);if(n<0)break;out.write(buf,0,n);require(out.size()<=max)};out.toString("UTF-8")}}finally{c.disconnect();j.track(null)}
    }
    fun download(release:Release, callback:(Int,String)->Unit){
        cancel();val gen=generation;val j=NetworkJob();job=j;selected=release
        j.future=pool.submit{
            val part=File(dir,"download-$gen.part")
            try{
                require(dir.usableSpace>release.size+10_000_000){"Insufficient storage"}
                val c=redirected(j,release.url);var total=0L;var last=-1
                try{c.inputStream.use{input->part.outputStream().use{out->val buf=ByteArray(65536);while(true){check(!j.cancelled);val n=input.read(buf);if(n<0)break;total+=n;require(total<=release.size);out.write(buf,0,n);val percent=(total*100/release.size).toInt();if(percent!=last){last=percent;main.post{if(gen==generation&&!j.cancelled)callback(percent,"downloading")}}}}}}finally{c.disconnect();j.track(null)}
                require(total==release.size);verify(part,release);check(!j.cancelled)
                synchronized(this@Updates){check(gen==generation&&!j.cancelled);require(part.renameTo(apk));File(dir,"pending.json").writeText(release.json().toString())}
                main.post{if(gen==generation&&!j.cancelled)callback(100,"verified")}
            }catch(e:Exception){part.delete();main.post{if(gen==generation&&!j.cancelled)callback(0,"error: ${e.message}")}}
        }
    }
    fun verify(file:File,release:Release){
        require(file.length()==release.size&&fileHash(file)==release.sha){"APK hash/size mismatch"}
        require(release.code>BuildConfig.VERSION_CODE){"Version is not newer"}
        val result=ApkVerifier.Builder(file).setMinCheckedPlatformVersion(29).build().verify()
        require(result.isVerified&&result.signerCertificates.size==1){"Invalid APK signature"}
        val cert=sha256(result.signerCertificates.single().encoded)
        require(cert==release.signer&&cert==signer()){ "Signing identity mismatch" }
        val info=context.packageManager.getPackageArchiveInfo(file.absolutePath,0)?:error("Invalid APK manifest")
        require(info.packageName==context.packageName&&info.longVersionCode==release.code&&info.versionName==release.version){"Package/version mismatch"}
    }
    fun pending():Release? = runCatching { Release.parse(JSONObject(File(dir,"pending.json").readText())).also{verify(apk,it)} }.getOrNull()
    fun cleanInstalled(){
        val manifest=File(dir,"pending.json")
        val code=runCatching{JSONObject(manifest.readText()).getLong("versionCode")}.getOrNull()
        if(code!=null&&code<=BuildConfig.VERSION_CODE){apk.delete();manifest.delete()}
        dir.listFiles()?.filter{it.name.startsWith("download-")&&it.extension=="part"}?.forEach{it.delete()}
    }
    fun install(activity:android.app.Activity):Boolean {
        pending()?:error("No verified update")
        if(!context.packageManager.canRequestPackageInstalls()){
            installing=true
            activity.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}")))
            return false
        }
        installing=false
        activity.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://${context.packageName}.updates/candidate.apk"),"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        return true
    }
}

class UpdateProvider:ContentProvider(){
    override fun onCreate()=true
    override fun getType(uri:Uri)="application/vnd.android.package-archive"
    override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {
        require(uri.path=="/candidate.apk"&&mode=="r");return ParcelFileDescriptor.open(File(context!!.cacheDir,"updates/candidate.apk"),ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor?=null
    override fun insert(uri:Uri,values:ContentValues?):Uri?=null
    override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?):Int=0
    override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?):Int=0
}
