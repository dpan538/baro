package studio.sundown.weather

import org.junit.Test
import org.junit.Assert.*

class FieldFeedbackTest {
    @Test fun cachedLocationMustBeRecentFiniteAndInRange(){
        assertTrue(validLocationFix(-27.47,153.03,90_000,4_000.0))
        assertFalse(validLocationFix(-27.47,153.03,120_001,10.0))
        assertFalse(validLocationFix(-27.47,153.03,-1,10.0))
        assertFalse(validLocationFix(Double.NaN,153.03,0,10.0))
        assertFalse(validLocationFix(-91.0,153.03,0,10.0))
        assertFalse(validLocationFix(-27.47,181.0,0,10.0))
        assertFalse(validLocationFix(-27.47,153.03,0,Double.NaN))
    }
    @Test fun currentRainSurvivesAnOngoingLightWindowAndZeroModelAmount(){
        val w=Weather(Place("Rain",-27.47,153.03),10_000,10_000,
            mapOf("weather_code" to 80.0,"precipitation" to 0.0,"is_day" to 1.0),
            Series(listOf(9_000,12_600,16_200),mapOf("weather_code" to listOf(0.0,0.0,0.0),"is_day" to listOf(1.0,1.0,1.0))),emptyList(),null,null,null)
        assertEquals("clear",w.scene(10_900))
        val live=w.atmosphereTime(10_900)
        assertEquals("rain",w.scene(live))
        assertTrue(w.rainStrength(live)>.3)
        assertEquals(16_200,w.atmosphereTime(16_200))
        assertEquals(0.0,w.rainStrength(16_200),0.0)
    }
}
