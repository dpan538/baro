package studio.sundown.weather

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors

class NativeFeedbackStateTest {
    private val i=InstrumentationRegistry.getInstrumentation()
    @Test fun originalDraftVersionAndIdentitySurviveRetryAndRecreation(){
        val prefs=i.targetContext.getSharedPreferences("baro-feedback-state-test",0);prefs.edit().clear().commit()
        val pool=Executors.newSingleThreadExecutor()
        try{i.runOnMainSync{
            val first=Feedback(prefs,pool,"");first.draft="persistent draft"
            val id=prefs.getString("feedback_id",null);prefs.edit().putString("feedback_version","0.10.0-original").commit()
            val recreated=Feedback(prefs,pool,"");recreated.send{ok,error->assertFalse(ok);assertEquals("unconfigured",error)}
            assertEquals(id,prefs.getString("feedback_id",null));assertEquals("0.10.0-original",prefs.getString("feedback_version",null));assertEquals("persistent draft",recreated.draft)
            recreated.draft="edited draft";assertNotEquals(id,prefs.getString("feedback_id",null));assertEquals(BuildConfig.VERSION_NAME,prefs.getString("feedback_version",null))
        }}finally{pool.shutdownNow();prefs.edit().clear().commit()}
    }
    @Test fun unicodeLimitCountsCodepointsAndFailureKeepsDraft(){
        val prefs=i.targetContext.getSharedPreferences("baro-feedback-unicode-test",0);prefs.edit().clear().commit();val pool=Executors.newSingleThreadExecutor()
        try{i.runOnMainSync{
            val sender=Feedback(prefs,pool,"");sender.draft="🌧".repeat(1000);sender.send{ok,error->assertFalse(ok);assertEquals("unconfigured",error)}
            val id=prefs.getString("feedback_id",null);assertEquals(2000,sender.draft.length);assertNotNull(id)
            sender.draft="🌧".repeat(1001);sender.send{ok,error->assertFalse(ok);assertEquals("invalid",error)};assertEquals(2002,sender.draft.length)
        }}finally{pool.shutdownNow();prefs.edit().clear().commit()}
    }
}
