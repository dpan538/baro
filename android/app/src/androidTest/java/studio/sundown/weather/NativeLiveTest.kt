package studio.sundown.weather
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*

/** Opt-in live network tests; require the dedicated local BARO relay on port 8787. */
class NativeLiveTest {
    @Test fun emulatorToDedicatedBackendToRealProviders(){
        val health=NetworkJob().request("http://10.0.2.2:8787/healthz")
        assertTrue(health.contains("baro"))
        val raw=NetworkJob().request("http://10.0.2.2:8787/v1/weather?lat=-27.47&lon=153.03")
        val w=WeatherJson.parse(raw,Place("Brisbane",-27.47,153.03))
        assertNotNull(w.value("temperature_2m"));assertNotNull(w.aqi);assertEquals("Australia/Brisbane",w.place.zone)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.getExternalFilesDir(null),"native-live-response.json").writeText(raw)
    }
}
