package studio.sundown.weather

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ConditionsAdviceTest {
    private val place=Place("Brisbane",-27.47,153.03,"Australia/Brisbane")
    private fun epoch(time:String)=LocalDateTime.parse("2026-10-25T$time").atZone(ZoneId.of(place.zone)).toEpochSecond()
    private val dawn=epoch("05:30")
    private val noon=epoch("12:00")
    private val ordinary=mapOf("temperature_2m" to 20.0,"relative_humidity_2m" to 55.0,"dew_point_2m" to 10.0,
        "wind_speed_10m" to 5.0,"wind_gusts_10m" to 8.0,"cloud_cover" to 20.0,"cloud_cover_low" to 10.0,
        "visibility" to 20000.0,"soil_moisture_0_to_1cm" to .2,"weather_code" to 0.0,"precipitation_probability" to 0.0,
        "direct_radiation_instant" to 300.0,"diffuse_radiation_instant" to 40.0)
    private fun weather(fields:Map<String,Double?> = ordinary,at:Long=noon):Weather {
        val times=(0L..3L).map{at+it*3600}
        return Weather(place,at,at,fields,Series(times,fields.mapValues{(_,v)->times.map{v}}),emptyList(),null,null,null)
    }
    private fun advice(w:Weather?,p:Int,at:Long=w?.validAt?:noon,lang:String="zh")=conditionsAdvice(w,p,at,lang)
    @Test fun wetSoilExplainsSittingCampingAndShoesDifferently(){
        val w=weather(ordinary+mapOf("soil_moisture_0_to_1cm" to .42))
        assertTrue(advice(w,3).text.contains("坐垫"));assertTrue(advice(w,9).text.contains("防潮垫"));assertTrue(advice(w,1).text.contains("鞋"))
        assertFalse(advice(w,11).reasons.contains("wet_soil"));assertFalse(advice(w,10).reasons.contains("wet_soil"))
        assertEquals(12,(0..11).map{advice(w,it).text}.distinct().size)
    }
    @Test fun dewNeedsTimeMoistureWindAndCloudEvidence(){
        val fields=ordinary+mapOf("temperature_2m" to 18.0,"dew_point_2m" to 17.0,"relative_humidity_2m" to 94.0)
        val w=weather(fields,dawn)
        assertTrue(advice(w,1).reasons.contains("dew"));assertTrue(advice(w,1).text.contains("鞋袜"))
        assertTrue(advice(w,5).text.contains("镜头"));assertTrue(advice(w,9).text.contains("外帐"))
        for(override in listOf(mapOf("wind_speed_10m" to 25.0),mapOf("cloud_cover" to 95.0),mapOf("wind_speed_10m" to null),mapOf("dew_point_2m" to null,"relative_humidity_2m" to null)))
            assertFalse(advice(weather(fields+override,dawn),1).reasons.contains("dew"))
        assertFalse(advice(weather(fields,noon),1).reasons.contains("dew"))
    }
    @Test fun saturatedAirIsNotAnAssertionOfWetGrassOrDew(){
        val w=weather(ordinary+mapOf("relative_humidity_2m" to 100.0,"dew_point_2m" to 20.0,"soil_moisture_0_to_1cm" to null))
        assertFalse(advice(w,1).reasons.contains("wet_soil"));assertFalse(advice(w,1).reasons.contains("dew"))
    }
    @Test fun frostAndFreezingRainDoNotBecomeMorningDew(){
        val frost=weather(ordinary+mapOf("temperature_2m" to 1.0,"dew_point_2m" to 0.0,"soil_temperature_0cm" to -.5),dawn)
        assertTrue(advice(frost,1).reasons.contains("frost"));assertFalse(advice(frost,1).reasons.contains("dew"))
        val ice=frost.copy(current=frost.current+mapOf("weather_code" to 66.0))
        assertEquals("freezing_rain",advice(ice,2).reasons.first());assertFalse(advice(ice,2).reasons.contains("rain"))
    }
    @Test fun thunderstormsTakePrecedenceOverDampGroundAndPhotoQuality(){
        val w=weather(ordinary+mapOf("weather_code" to 95.0,"soil_moisture_0_to_1cm" to .5,"wind_gusts_10m" to 50.0))
        for(p in 0..11)assertEquals("thunder",advice(w,p).reasons.first())
        assertTrue(advice(w,9).text.contains("帐篷"));assertTrue(advice(w,6).text.contains("室内"))
    }
    @Test fun forecastProbabilityDoesNotAssertCurrentRain(){
        val w=weather(ordinary+mapOf("precipitation_probability" to 90.0))
        assertEquals("rain_chance",advice(w,0).reasons.first());assertFalse(advice(w,0).text.contains("有降雨"))
        val rain=w.copy(current=w.current+mapOf("weather_code" to 80.0,"precipitation_probability" to 0.0))
        assertEquals("rain",advice(rain,0).reasons.first())
    }
    @Test fun snowRemainsSnowAcrossPurposesAndLanguages(){
        val w=weather(ordinary+mapOf("weather_code" to 73.0))
        for(p in 0..11)for(lang in listOf("zh","en")){
            val a=advice(w,p,lang=lang);assertEquals("snow",a.reasons.first());assertFalse(a.reasons.contains("rain"))
        }
    }
    @Test fun everyPurposeUsesItsSelectedForecastRatherThanCurrentConditions(){
        val w=weather();val at=noon+3600
        val future=w.copy(current=w.current+mapOf("soil_moisture_0_to_1cm" to .1),hourly=w.hourly.copy(fields=w.hourly.fields+mapOf("soil_moisture_0_to_1cm" to listOf(.1,.45,.45,.45))))
        assertFalse(advice(future,3).reasons.contains("wet_soil"));assertTrue(advice(future,3,at).reasons.contains("wet_soil"))
        assertFalse(advice(future,3,noon+5*3600).reasons.contains("wet_soil"))
    }
    @Test fun ongoingPhotoWindowUsesCurrentRainEvenWhenHourlyCodeDiffers(){
        val w=weather().copy(current=ordinary+mapOf("weather_code" to 80.0))
        assertEquals("rain",advice(w,4,noon+600).reasons.first())
        assertFalse(advice(w,4,noon+3600).reasons.contains("rain"))
    }
    @Test fun missingDataDoesNotCreateFavourableWeather(){
        val w=weather(emptyMap())
        for(p in 0..11){val a=advice(w,p);assertEquals(listOf("local_check"),a.reasons);assertFalse(a.text.contains("温和"))}
        assertEquals(listOf("unavailable"),advice(null,0).reasons)
        assertEquals(listOf("no_window"),conditionsAdvice(w,5,dawn,"en",false).reasons)
    }
    @Test fun cloudRecommendationsSeparateSunsetDawnBlueAndStars(){
        val w=weather(ordinary+mapOf("cloud_cover" to 95.0,"cloud_cover_low" to 90.0))
        val copies=(4..8).map{advice(w,it).text};assertEquals(5,copies.distinct().size)
        assertTrue(copies[0].contains("西侧"));assertTrue(copies[1].contains("东侧"));assertTrue(copies[3].contains("星区"))
    }
    @Test fun indoorAndStorageAdviceNeverPretendsToMeasureIndoorHumidity(){
        val w=weather(ordinary+mapOf("relative_humidity_2m" to 90.0))
        assertTrue(advice(w,10).text.contains("衣物"));assertTrue(advice(w,11).text.contains("收纳"))
        for(p in 4..8)assertFalse(advice(w,p).text.contains("体感"))
    }
    @Test fun radiationKeepsZeroMissingAndDominanceDistinct(){
        assertEquals("direct",radiationState(900.0,100.0,45.0).kind)
        assertEquals("diffuse",radiationState(10.0,100.0,45.0).kind)
        assertEquals("mixed",radiationState(100.0,100.0,45.0).kind)
        assertEquals("night",radiationState(0.0,0.0,-15.0).kind)
        assertEquals("zero",radiationState(0.0,0.0,45.0).kind)
        assertEquals("missing",radiationState(null,0.0,45.0).kind)
        assertNull(radiationState(0.0,0.0,-15.0).diffuseShare)
        assertEquals("missing",radiationState(Double.NaN,100.0,45.0).kind)
    }
    @Test fun compensationGivesAnAdjustableStartingPointWithoutInventingKelvin(){
        val direct=weather();val cloudy=weather(ordinary+mapOf("direct_radiation_instant" to 10.0,"diffuse_radiation_instant" to 200.0))
        assertEquals("先用日光",whiteBalanceHint(direct,noon,"zh").direction)
        assertEquals("可略加暖",whiteBalanceHint(cloudy,noon,"zh").direction)
        for(w in listOf(null,direct,cloudy))for(lang in listOf("zh","en")){
            val hint=whiteBalanceHint(w,noon,lang);assertFalse(Regex("[0-9]+\\s*K").containsMatchIn(hint.direction+hint.note));assertTrue(hint.note.isNotEmpty())
        }
    }
    @Test fun allConditionPurposeAndLanguageCombinationsFitCompleteReadingBudget(){
        val cases=listOf(emptyMap(),ordinary,ordinary+mapOf("soil_moisture_0_to_1cm" to .5),ordinary+mapOf("weather_code" to 95.0,"wind_gusts_10m" to 50.0),ordinary+mapOf("weather_code" to 73.0),ordinary+mapOf("weather_code" to 66.0),ordinary+mapOf("weather_code" to 80.0),ordinary+mapOf("precipitation_probability" to 85.0),ordinary+mapOf("visibility" to 2000.0),ordinary+mapOf("relative_humidity_2m" to 90.0),ordinary+mapOf("temperature_2m" to 31.0,"relative_humidity_2m" to 90.0),ordinary+mapOf("temperature_2m" to 4.0),ordinary+mapOf("cloud_cover_low" to 90.0),ordinary+mapOf("dew_point_2m" to 19.0))
        for(fields in cases)for(p in 0..11)for(lang in listOf("zh","en"))for(at in listOf(dawn,noon)){
            val a=advice(weather(fields,at),p,lang=lang)
            assertTrue(a.text.isNotEmpty());assertTrue(a.text.length<=if(lang=="zh")84 else 180)
            assertTrue(a.text.endsWith(if(lang=="zh")"。"else "."));assertFalse(a.text.contains("\n"));assertEquals(a.reasons.size,a.reasons.distinct().size)
        }
    }
}
