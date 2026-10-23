package studio.sundown.weather
import org.junit.Test
import org.junit.Assert.*
import java.time.*

class DomainTest {
    private fun fixture()=Weather(Place("Test",0.0,0.0),1000,1000,mapOf("weather_code" to 0.0,"is_day" to 1.0),Series(emptyList(),emptyMap()),emptyList(),null,null,null)
    @Test fun dialHysteresisRejectsBoundaryNoiseAndReverses(){
        val dial=DialDetents(11);dial.begin(11)
        assertTrue(dial.move(.57f).isEmpty());assertEquals(listOf(12),dial.move(.59f));assertEquals(0,dial.index)
        assertTrue(dial.move(.48f).isEmpty());assertTrue(dial.move(.44f).isEmpty())
        assertEquals(listOf(11),dial.move(.40f));assertEquals(11,dial.index)
        assertEquals(listOf(10,9,8),dial.move(-3.2f));assertEquals(8,dial.index)
        assertTrue(dial.move(Float.NaN).isEmpty());assertEquals(7.8f,dial.position,.001f)
        dial.cancel();assertEquals(11,dial.index);assertEquals(11f,dial.position,0f)
    }
    @Test fun dialCrossesEveryToothAndWrapsBothWays(){
        val dial=DialDetents();dial.begin(0)
        assertEquals((-1 downTo -13).toList(),dial.move(-13f));assertEquals(11,dial.index)
        assertEquals((-12..14).toList(),dial.move(14f));assertEquals(2,dial.index)
        assertEquals((15..24).toList(),dial.move(1000f));assertEquals(24f,dial.position,0f)
    }
    @Test fun memoryReusesTheParsedReadingAndEvictsIdleOrLeastRecentlyUsed(){
        val memory=ForecastMemory(2);val weather=fixture()
        memory.put("a",weather,"raw-a",0);memory.put("b",weather,"raw-b",10)
        assertSame(weather,memory.get("a",20)!!.weather)
        memory.put("c",weather,"raw-c",30);assertNull(memory.get("b",30));assertEquals(2,memory.count())
        assertEquals("raw-a",memory.get("a",3590)!!.raw)
        assertNull(memory.get("c",3630));assertNotNull(memory.get("a",3630))
        assertNull(memory.get("a",7230));assertEquals(0,memory.count())
    }
    @Test fun requestCooldownUsesElapsedTimeAndClearsWithExplicitCacheClear(){
        val memory=ForecastMemory();assertTrue(memory.mayRead("a",0));memory.attempted("a",100)
        assertFalse(memory.mayRead("a",399));assertTrue(memory.mayRead("a",400));assertTrue(memory.mayRead("b",101))
        memory.clear();assertTrue(memory.mayRead("a",101));assertEquals(0,memory.count())
    }
    @Test fun interruptedInitialLoadCanRetryButCachedAndFailedAttemptsStayLimited(){
        val memory=ForecastMemory();memory.attempted("initial",100);memory.abandonInitial("initial",99);assertFalse(memory.mayRead("initial",101))
        memory.abandonInitial("initial",100);assertTrue(memory.mayRead("initial",101))
        memory.put("cached",fixture(),"raw",100);memory.attempted("cached",100);memory.abandonInitial("cached",100);assertFalse(memory.mayRead("cached",101))
    }
    @Test fun bothDirectionsToggleFromBothPages(){
        for(page in 0..1)for(direction in listOf(-1,1)){val s=SwipeDecision(8f,24f);s.begin(500f,100f,1000f,false);val dx=s.move(500f+direction*250,105f);assertTrue(s.finish(dx,direction*800f,1000f,false))}
    }
    @Test fun distanceVelocityCancelAndConflictGates(){
        val s=SwipeDecision(8f,24f)
        s.begin(500f,100f,1000f,false);assertEquals(0f,s.move(506f,104f));assertFalse(s.finish(6f,900f,1000f,false))
        s.begin(500f,100f,1000f,false);assertEquals(0f,s.move(510f,150f));assertFalse(s.finish(200f,900f,1000f,false))
        s.begin(12f,100f,1000f,false);assertEquals(0f,s.move(500f,100f))
        s.begin(500f,100f,1000f,true);assertEquals(0f,s.move(800f,100f))
        s.begin(500f,100f,1000f,false);s.move(800f,100f);assertFalse(s.finish(300f,900f,1000f,true));s.cancel();assertFalse(s.finish(300f,900f,1000f,false))
        s.begin(500f,100f,1000f,false);s.move(525f,100f);assertTrue(s.finish(25f,800f,1000f,false));assertFalse(s.finish(25f,-800f,1000f,false))
    }
    @Test fun hourlyMatchingNeverBorrowsAFutureReading(){val s=Series(listOf(100L,3700L),mapOf("x" to listOf(0.0,null)));assertNull(s.at("x",99));assertEquals(0.0,s.at("x",100));assertEquals(0.0,s.at("x",3699));assertNull(s.at("x",3700));assertNull(s.at("x",7300));assertEquals(3700L,s.ending("x",200)?.first)}
    @Test fun temperatureDeltasDoNotGetAbsoluteOffset(){assertEquals(32.0,Units.temperature(0.0,true));assertEquals(0.0,Units.temperature(0.0,true,true));assertEquals(18.0,Units.temperature(10.0,true,true));assertEquals(1.0,Units.wind(3.6,"ms"));assertEquals("0.0",Units.number(-.0001,1))}
    @Test fun moistureApproximationsRespectDomains(){assertNull(Physics.dew(20.0,0.0));assertEquals(20.0,Physics.dew(20.0,100.0)!!,1e-9);assertEquals(0.0,Physics.vpd(20.0,100.0)!!,1e-9);assertNull(Physics.wetBulb(-30.0,40.0));assertNull(Physics.wetBulb(20.0,1.0));assertEquals(20.0,Physics.wetBulb(20.0,100.0));assertEquals(0.0,Physics.lcl(20.0,22.0));assertEquals(1250.0,Physics.lcl(30.0,20.0))}
    @Test fun windDifferenceIsVectorMagnitude(){assertEquals(0.0,Physics.windDelta(20.0,0.0,20.0,0.0)!!,1e-9);assertEquals(40.0,Physics.windDelta(20.0,0.0,20.0,180.0)!!,1e-9);assertNull(Physics.windDelta(20.0,null,20.0,0.0))}
    @Test fun equinoxAndPolarCrossings(){val p=Place("Equator",0.0,0.0);val at=Instant.parse("2026-03-20T12:00:00Z").epochSecond;assertTrue(Astronomy.sun(at,p).elevation>87);assertEquals(2,Astronomy.crossings(LocalDate.parse("2026-03-20"),p,-.833).size);assertTrue(Astronomy.crossings(LocalDate.parse("2026-06-21"),Place("Pole",89.0,0.0),-.833).isEmpty())}
    @Test fun dstCivilDaysAreNotFixed24Hours(){val p=Place("London",51.5,-.12,"Europe/London");for(d in listOf("2026-03-29","2026-10-25")){val events=Astronomy.crossings(LocalDate.parse(d),p,-.833);assertEquals(2,events.size);events.forEach{assertEquals(LocalDate.parse(d),Instant.ofEpochSecond(it.first).atZone(ZoneId.of(p.zone)).toLocalDate())}}}
    @Test fun missingValuesNeverBecomeReadings(){val w=fixture();assertNull(w.value("unmeasured"));assertNull(w.nextSolar());assertNull(Physics.glow(null,1.0,2.0,10000.0,50.0));assertEquals("clear",w.scene());assertTrue(w.stale(w.fetchedAt+999999))}
    @Test fun noInvalidCoordinates(){for(a in listOf(Double.NaN,Double.POSITIVE_INFINITY,91.0))assertThrows(IllegalArgumentException::class.java){Place("x",a,0.0)}}
    @Test fun moonCycleAndIlluminationStayBounded(){for(day in 0..60){val m=Astronomy.moon(946684800+day*86400L);assertTrue(m.age>=0&&m.age<29.531);assertTrue(m.illumination in 0.0..1.0)}}
    @Test fun staleTimestampRemainsStale(){val w=fixture().copy(fetchedAt=1000,validAt=1000);assertTrue(w.stale(2800));assertTrue(w.stale(100000));assertFalse(w.copy(fetchedAt=1000,validAt=1000).stale(1100))}
    @Test fun photoCountdownTargetsTheSelectedPurposeAtNight(){
        val place=Place("Brisbane",-27.47,153.03,"Australia/Brisbane")
        val now=LocalDate.parse("2026-10-24").atTime(1,0).atZone(ZoneId.of(place.zone)).toEpochSecond()
        val w=fixture().copy(place=place,validAt=now-86400)
        for((purpose,angle,rising) in listOf(Triple(4,6.0,false),Triple(5,-6.0,true),Triple(6,-4.0,false))){
            val event=lightTarget(w,purpose,now)!!
            assertTrue(event.epoch>now);assertEquals(angle,Astronomy.sun(event.epoch,place).elevation,.05)
            assertEquals(rising,Astronomy.sun(event.epoch+60,place).elevation>Astronomy.sun(event.epoch-60,place).elevation)
            if(purpose!=5)assertTrue(Instant.ofEpochSecond(event.epoch).atZone(ZoneId.of(place.zone)).hour>=16)
        }
    }
    @Test fun anActivePhotoWindowCountsToItsEnd(){
        val p=Place("Brisbane",-27.47,153.03,"Australia/Brisbane");val now=LocalDate.parse("2026-10-24").atTime(1,0).atZone(ZoneId.of(p.zone)).toEpochSecond();val w=fixture().copy(place=p,validAt=now)
        val window=observationWindow(w,4,now);val target=lightTarget(w,4,window.start+300)!!
        assertEquals("end",target.kind);assertEquals(window.end,target.epoch)
    }
    @Test fun photoWindowsCrossCivilMidnightAndKeepPolarAbsence(){
        val p=Place("Helsinki",60.17,24.94,"Europe/Helsinki");val date=LocalDate.parse("2026-06-20");val now=date.atTime(12,0).atZone(ZoneId.of(p.zone)).toEpochSecond();val w=fixture().copy(place=p,validAt=now)
        val window=observationWindow(w,4,now);assertTrue(window.available)
        assertEquals(date,Instant.ofEpochSecond(window.start).atZone(ZoneId.of(p.zone)).toLocalDate());assertEquals(date.plusDays(1),Instant.ofEpochSecond(window.end).atZone(ZoneId.of(p.zone)).toLocalDate())
        val pole=w.copy(place=Place("Pole",89.0,0.0,"UTC"));assertNull(lightTarget(pole,4,now));assertNull(lightTarget(pole,5,now));assertNull(lightTarget(pole,6,now))
    }

}
