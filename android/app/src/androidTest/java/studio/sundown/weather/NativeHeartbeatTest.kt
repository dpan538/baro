package studio.sundown.weather

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Real Activity heartbeat + actual local HTTP/disk, with accelerated clocks.
 * Recorded provider payloads and loopback transport are androidTest-only. */
class NativeHeartbeatTest {
    private val i=InstrumentationRegistry.getInstrumentation()
    private class Clock {
        val wall=AtomicLong(System.currentTimeMillis()/1000);val elapsed=AtomicLong(1000)
        fun advance(seconds:Long){wall.addAndGet(seconds);elapsed.addAndGet(seconds)}
    }
    private inner class Server(val clock:Clock):AutoCloseable {
        private val socket=ServerSocket(0)
        val origin="http://127.0.0.1:${socket.localPort}"
        val requests=AtomicInteger()
        @Volatile var status=200
        @Volatile var gate:CountDownLatch?=null
        private val raw=i.context.assets.open("live-brisbane.json").bufferedReader().use{it.readText()}
        private val worker=Thread{
            while(!socket.isClosed)try{socket.accept().use{client->
                client.soTimeout=5000;val input=client.getInputStream().bufferedReader()
                while(true){val line=input.readLine()?:break;if(line.isEmpty())break}
                requests.incrementAndGet();gate?.await(12,TimeUnit.SECONDS)
                val response=JSONObject(raw).put("fetchedAt",clock.wall.get()).apply{getJSONObject("forecast").getJSONObject("current").put("time",clock.wall.get())}.toString().toByteArray()
                client.getOutputStream().apply{write("HTTP/1.1 $status Test\r\nContent-Type: application/json\r\nContent-Length: ${response.size}\r\nConnection: close\r\n\r\n".toByteArray());write(response);flush()}
            }}catch(_:Exception){}
        }.apply{isDaemon=true;start()}
        override fun close(){gate?.countDown();socket.close();worker.join(1000)}
    }
    private fun seed(){
        i.targetContext.getSharedPreferences("baro-native",Context.MODE_PRIVATE).edit().clear().putBoolean("motion",false).putBoolean("block",true).commit()
        i.targetContext.getSharedPreferences("sundown-device",Context.MODE_PRIVATE).edit().putBoolean("location_follow",false).commit()
    }
    private fun prepare(a:MainActivity,server:Server,clock:Clock){
        a.repository.close()
        val repo=WeatherRepository(a,server.origin,{clock.wall.get()},{clock.elapsed.get()})
        MainActivity::class.java.getDeclaredField("repository").apply{isAccessible=true}.set(a,repo)
        MainActivity::class.java.getDeclaredField("refreshSchedule").apply{isAccessible=true}.set(a,WeatherRefreshSchedule({clock.wall.get()},{clock.elapsed.get()}))
        repo.clear();a.model.weather=null;a.refresh(true)
    }
    private fun await(s:ActivityScenario<MainActivity>,check:(MainActivity)->Boolean){
        val end=SystemClock.uptimeMillis()+15000
        while(SystemClock.uptimeMillis()<end){var ok=false;s.onActivity{ok=check(it)};if(ok)return;SystemClock.sleep(100)}
        s.onActivity{assertTrue("Heartbeat/API/cache state did not converge",check(it))}
    }
    private fun fresh(s:ActivityScenario<MainActivity>,server:Server,count:Int){
        await(s){server.requests.get()==count&&it.model.status=="ready"&&it.model.weather?.fetchedAt==server.clock.wall.get()}
        s.onActivity{a->val raw=JSONObject(a.repository.rawFor(a.model.place)!!);assertEquals(server.clock.wall.get(),raw.getLong("fetchedAt"));assertFalse(a.model.source().contains("过期缓存"));assertFalse(a.model.source().contains("STALE"))}
    }
    @Test fun foregroundTimerRefreshesReplacesExpiresRetriesAndResumesWithoutUserInput(){
        seed();val clock=Clock();Server(clock).use{server->ActivityScenario.launch(MainActivity::class.java).use{s->
            s.onActivity{prepare(it,server,clock)};fresh(s,server,1)
            clock.advance(899);SystemClock.sleep(1300);assertEquals(1,server.requests.get())
            clock.advance(1);fresh(s,server,2)
            server.status=503;clock.advance(900);await(s){server.requests.get()==3&&it.model.status=="offline"};s.onActivity{assertNotNull(it.model.weather)}
            clock.advance(299);SystemClock.sleep(1300);assertEquals(3,server.requests.get())
            server.status=200;clock.advance(1);fresh(s,server,4)
            server.status=503;clock.advance(1800);await(s){server.requests.get()==5&&it.model.status=="offline"&&it.model.weather==null}
            s.onActivity{assertEquals(0L,it.repository.bytes());assertFalse(it.model.source().contains("缓存"))}
            server.status=200;clock.advance(300);fresh(s,server,6)
            s.moveToState(Lifecycle.State.CREATED);clock.advance(1800);SystemClock.sleep(1300);assertEquals(6,server.requests.get())
            s.moveToState(Lifecycle.State.RESUMED);fresh(s,server,7)
            s.onActivity{a->File(a.getExternalFilesDir(null),"heartbeat-refresh.json").writeText(JSONObject().put("controlledClocksAndHttp",true).put("scheduledRefreshSeconds",900).put("retrySeconds",300).put("successfulReplacement",true).put("expiredDiskEntryRemoved",true).put("backgroundRequests",0).put("resumeRefresh",true).put("httpRequests",server.requests.get()).toString(2))}
        }}
    }
    @Test fun aLostCompletionCannotLeaveTheHeartbeatPermanentlyLoading(){
        seed();val clock=Clock();Server(clock).use{server->ActivityScenario.launch(MainActivity::class.java).use{s->
            s.onActivity{prepare(it,server,clock)};fresh(s,server,1)
            val gate=CountDownLatch(1);server.gate=gate;clock.advance(900)
            await(s){server.requests.get()==2&&it.model.status=="loading"}
            clock.elapsed.addAndGet(45);await(s){it.model.status=="offline"};s.onActivity{assertNotNull(it.model.weather)}
            server.gate=null;gate.countDown();clock.advance(299);SystemClock.sleep(1200);assertEquals(2,server.requests.get())
            clock.advance(1);fresh(s,server,3)
        }}
    }
}
