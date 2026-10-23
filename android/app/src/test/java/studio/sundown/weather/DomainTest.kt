package studio.sundown.weather
import org.junit.Test
import org.junit.Assert.*
import java.time.*

class DomainTest {
    private fun fixture()=Weather(Place("Test",0.0,0.0),1000,1000,mapOf("weather_code" to 0.0,"is_day" to 1.0),Series(emptyList(),emptyMap()),emptyList(),null,null,null)
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
    @Test fun missingValuesNeverBecomeReadings(){val w=fixture();assertNull(w.value("unmeasured"));assertNull(Physics.glow(null,1.0,2.0,10000.0,50.0));assertEquals("clear",w.scene());assertTrue(w.stale(w.fetchedAt+999999))}
    @Test fun noInvalidCoordinates(){for(a in listOf(Double.NaN,Double.POSITIVE_INFINITY,91.0))assertThrows(IllegalArgumentException::class.java){Place("x",a,0.0)}}
    @Test fun moonCycleAndIlluminationStayBounded(){for(day in 0..60){val m=Astronomy.moon(946684800+day*86400L);assertTrue(m.age>=0&&m.age<29.531);assertTrue(m.illumination in 0.0..1.0)}}
    @Test fun staleTimestampRemainsStale(){val w=fixture().copy(fetchedAt=1000,validAt=1000);assertTrue(w.stale(2800));assertTrue(w.stale(100000));assertFalse(w.copy(fetchedAt=1000,validAt=1000).stale(1100))}
}
