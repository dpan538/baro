package studio.sundown.weather

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Actual HTTP and persisted JSON, with injected clocks; no provider quota or wall-clock waits. */
class NativeCacheTest {
    private val i=InstrumentationRegistry.getInstrumentation()
    private val place=Place("Brisbane",-27.47,153.03,"Australia/Brisbane")
    private class Server(private val response:()->String):AutoCloseable{
        private val socket=ServerSocket(0)
        val origin="http://127.0.0.1:${socket.localPort}"
        val requests=AtomicInteger()
        @Volatile var status=200
        @Volatile var gate:CountDownLatch?=null
        private val worker=Thread{
            while(!socket.isClosed)try{socket.accept().use{client->
                client.soTimeout=5000;val input=client.getInputStream().bufferedReader()
                while(true){val line=input.readLine()?:break;if(line.isEmpty())break}
                requests.incrementAndGet();gate?.await(8,TimeUnit.SECONDS)
                val bytes=response().toByteArray();client.getOutputStream().apply{write("HTTP/1.1 $status Test\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray());write(bytes);flush()}
            }}catch(_:Exception){}
        }.apply{isDaemon=true;start()}
        override fun close(){gate?.countDown();socket.close();worker.join(1000)}
    }
    private class Clock {val wall=AtomicLong(System.currentTimeMillis()/1000);val monotonic=AtomicLong(1000);fun advance(seconds:Long){wall.addAndGet(seconds);monotonic.addAndGet(seconds)}}
    private fun server(clock:Clock):Server{
        val raw=i.context.assets.open("live-brisbane.json").bufferedReader().use{it.readText()}
        return Server{JSONObject(raw).put("fetchedAt",clock.wall.get()).apply{getJSONObject("forecast").getJSONObject("current").put("time",clock.wall.get())}.toString()}
    }
    private fun repository(server:Server,clock:Clock):WeatherRepository{
        lateinit var result:WeatherRepository;i.runOnMainSync{result=WeatherRepository(i.targetContext,server.origin,{clock.wall.get()},{clock.monotonic.get()})};return result
    }
    private fun read(repo:WeatherRepository,force:Boolean=false):Pair<Weather?,String?>{
        val latch=CountDownLatch(1);var result:Pair<Weather?,String?>?=null
        i.runOnMainSync{repo.refresh(place,force){w,e->result=w to e;latch.countDown()}}
        assertTrue("HTTP callback timed out",latch.await(12,TimeUnit.SECONDS));return result!!
    }
    private fun awaitRequest(server:Server,count:Int){val end=SystemClock.uptimeMillis()+3000;while(server.requests.get()<count&&SystemClock.uptimeMillis()<end)SystemClock.sleep(10);assertEquals(count,server.requests.get())}
    @Test fun coalescesInFlightAndSharesParsedMemoryAcrossRepositoryOwners(){
        val clock=Clock();server(clock).use{server->val repo=repository(server,clock);val other=repository(server,clock)
            try{
                i.runOnMainSync{repo.clear()};val gate=CountDownLatch(1);server.gate=gate;val done=CountDownLatch(2);val readings=mutableListOf<Weather>()
                i.runOnMainSync{repeat(2){repo.refresh(place){w,e->assertNull(e);readings.add(w!!);done.countDown()}}}
                awaitRequest(server,1);gate.countDown();assertTrue(done.await(8,TimeUnit.SECONDS));assertEquals(1,server.requests.get());assertSame(readings[0],readings[1])
                i.runOnMainSync{assertSame(readings[0],other.cached(place));assertEquals(repo.rawFor(place),other.rawFor(place))}
                i.runOnMainSync{assertEquals("Selected station",other.cached(place.copy(name="Selected station"))!!.place.name)}
                assertSame(readings[0],read(other).first);assertEquals(1,server.requests.get())
            }finally{i.runOnMainSync{repo.clear();repo.close();other.close()}}
        }
    }
    @Test fun normalThirtyMinuteReuseAndManualFiveMinuteMinimum(){
        val clock=Clock();server(clock).use{server->val repo=repository(server,clock)
            try{
                i.runOnMainSync{repo.clear()};val first=read(repo).first!!;assertEquals(1,server.requests.get())
                clock.advance(60);assertSame(first,read(repo,true).first);assertEquals(1,server.requests.get())
                clock.advance(240);assertNotSame(first,read(repo,true).first);assertEquals(2,server.requests.get())
                clock.advance(1799);assertNull(read(repo).second);assertEquals(2,server.requests.get())
                clock.advance(1);assertNull(read(repo).second);assertEquals(3,server.requests.get())
            }finally{i.runOnMainSync{repo.clear();repo.close()}}
        }
    }
    @Test fun offlineKeepsTheLastReadingAndDebouncesFailures(){
        val clock=Clock();server(clock).use{server->val repo=repository(server,clock)
            try{
                i.runOnMainSync{repo.clear()};val first=read(repo).first;clock.advance(1800);server.status=503
                val failed=read(repo);assertSame(first,failed.first);assertNotNull(failed.second);assertEquals(2,server.requests.get())
                repeat(8){assertSame(first,read(repo,true).first)};assertEquals(2,server.requests.get())
                clock.advance(300);server.status=200;assertNull(read(repo).second);assertEquals(3,server.requests.get())
            }finally{i.runOnMainSync{repo.clear();repo.close()}}
        }
    }
    @Test fun clearingWeatherPreservesOtherCachesPreferencesAndDraft(){
        val clock=Clock();server(clock).use{server->val repo=repository(server,clock)
            try{
                i.runOnMainSync{repo.clear()};read(repo);assertTrue(repo.bytes()>0)
                val protected=java.io.File(i.targetContext.cacheDir,"cache-regression-other").apply{writeText("keep")}
                val prefs=i.targetContext.getSharedPreferences("baro-native",0);prefs.edit().putString("wind","kts").putString("feedback_draft","preserved").commit()
                i.runOnMainSync{repo.clear();assertNull(repo.cached(place));assertEquals(0L,repo.bytes())}
                assertEquals("keep",protected.readText());protected.delete();assertEquals("kts",prefs.getString("wind",null));assertEquals("preserved",prefs.getString("feedback_draft",null))
            }finally{i.runOnMainSync{repo.clear();repo.close()}}
        }
    }
    @Test fun cancelledInitialReadCanResumeWithoutFiveMinuteBlankScreen(){
        val clock=Clock();server(clock).use{server->val repo=repository(server,clock)
            try{
                i.runOnMainSync{repo.clear()};val gate=CountDownLatch(1);server.gate=gate
                i.runOnMainSync{repo.refresh(place){_,_->fail("Cancelled owner received a result")}}
                awaitRequest(server,1);i.runOnMainSync{repo.cancelWeather()};server.gate=null;gate.countDown()
                val result=read(repo);assertNotNull("Rotation/resume must restart an interrupted initial request",result.first);assertNull(result.second);assertEquals(2,server.requests.get())
            }finally{i.runOnMainSync{repo.clear();repo.close()}}
        }
    }
    @Test fun interruptedStaleRefreshKeepsDisclosedCacheAndTheReadLimit(){
        val clock=Clock();server(clock).use{server->val repo=repository(server,clock);val resumed=repository(server,clock)
            try{
                i.runOnMainSync{repo.clear()};val first=read(repo).first!!;clock.advance(1800)
                val gate=CountDownLatch(1);server.gate=gate
                i.runOnMainSync{repo.refresh(place){_,_->fail("Stopped owner received a refresh")}}
                awaitRequest(server,2);i.runOnMainSync{repo.cancelWeather()};server.gate=null;gate.countDown()
                val fallback=read(resumed);assertSame(first,fallback.first);assertEquals("refresh_interval",fallback.second)
                assertTrue(first.stale(clock.wall.get()));assertEquals(2,server.requests.get())
                val model=AppModel(i.targetContext).apply{weather=fallback.first;status="offline"}
                for(language in listOf("zh","en")){model.lang=language;assertTrue(model.source().contains(if(language=="zh")"过期缓存"else "STALE"))}
                clock.advance(299);assertSame(first,read(resumed).first);assertEquals(2,server.requests.get())
                clock.advance(1);val fresh=read(resumed);assertNull(fresh.second);assertNotSame(first,fresh.first);assertEquals(3,server.requests.get())
            }finally{i.runOnMainSync{repo.clear();repo.close();resumed.close()}}
        }
    }
}
