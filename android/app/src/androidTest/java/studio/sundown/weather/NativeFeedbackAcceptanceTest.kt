package studio.sundown.weather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.ConnectivityManager
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Explicit opt-in only. Actual UI -> production Vercel -> real Issue -> verified native success. */
class NativeFeedbackAcceptanceTest {
    private val i=InstrumentationRegistry.getInstrumentation()
    private val ctx get()=i.targetContext
    private fun find(root:View,label:String):View?{
        if(root is TextView&&root.text.toString()==label)return root
        if(root is ViewGroup)for(n in 0 until root.childCount)find(root.getChildAt(n),label)?.let{return it}
        return null
    }
    private fun input(root:View):EditText?{
        if(root is EditText)return root
        if(root is ViewGroup)for(n in 0 until root.childCount)input(root.getChildAt(n))?.let{return it}
        return null
    }
    private fun await(timeout:Long=10000,check:()->Boolean){val end=SystemClock.uptimeMillis()+timeout;while(SystemClock.uptimeMillis()<end){if(check())return;SystemClock.sleep(100)};assertTrue("Timed out awaiting actual native feedback state",check())}
    private fun tap(s:ActivityScenario<MainActivity>,label:String){
        val r=Rect();s.onActivity{a->(find(a.settings,label)?:error("Missing $label")).let{it.requestRectangleOnScreen(Rect(0,0,it.width,it.height),true)}}
        SystemClock.sleep(250);s.onActivity{a->assertTrue(find(a.settings,label)!!.getGlobalVisibleRect(r))}
        val time=SystemClock.uptimeMillis()
        for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)){val e=MotionEvent.obtain(time,SystemClock.uptimeMillis(),action,r.exactCenterX(),r.exactCenterY(),0);i.sendPointerSync(e);e.recycle()}
        SystemClock.sleep(250)
    }
    private fun capture(name:String){val bitmap=i.uiAutomation.takeScreenshot()!!;File(ctx.getExternalFilesDir(null),name).outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
    private fun shell(command:String){i.uiAutomation.executeShellCommand(command).use{android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()}}
    @Test fun publicSubmissionShowsVerifiedReceiptAndOfflineFailureRetainsANewDraft(){
        val args=InstrumentationRegistry.getArguments();assertEquals("yes",args.getString("publicFeedback"))
        assertEquals(args.getString("expectedOrigin"),BuildConfig.FEEDBACK_URL);assertTrue(BuildConfig.FEEDBACK_URL.startsWith("https://baro-feedback"))
        val lang=args.getString("language")?:"en";assertTrue(lang in listOf("zh","en"))
        fun tr(zh:String,en:String)=if(lang=="zh")zh else en
        val submit=tr("提交反馈","Submit feedback")
        ctx.getSharedPreferences("baro-native",Context.MODE_PRIVATE).edit().putString("language",lang).putBoolean("motion",false).putInt("page",0).commit()
        val file=File(ctx.getExternalFilesDir(null),"feedback-public-acceptance.json")
        val old=if(file.exists())JSONObject(file.readText()).takeIf{it.optString("version")==BuildConfig.VERSION_NAME}else null
        val body="BARO preview.6 end-to-end feedback acceptance. Native app -> Vercel -> GitHub Issue -> verified receipt. Test only; no personal data."
        ActivityScenario.launch(MainActivity::class.java).use{s->
            s.onActivity{it.settings.open()};SystemClock.sleep(450);tap(s,"···");tap(s,tr("意见反馈","Feedback"))
            var id="";var version=""
            s.onActivity{a->input(a.settings)!!.setText(body)
                // Reuse a recorded identity when rerunning after a later-stage
                // failure, so this opt-in acceptance never adds duplicate Issues.
                if(old!=null)a.model.prefs.edit().putString("feedback_id",old.getString("requestId")).putString("feedback_version",old.getString("version")).commit()
                id=a.model.prefs.getString("feedback_id",null)!!;version=a.model.prefs.getString("feedback_version",null)!!
            }
            capture("feedback-public-form.png");tap(s,submit)
            var receipt=""
            await(65000){var success=false;s.onActivity{a->receipt=a.model.prefs.getString("feedback_receipt","")?:"";success=a.feedback.draft.isEmpty()&&receipt.matches(Regex("https://github.com/dpan538/baro/issues/[1-9][0-9]*"))};success}
            await{var shown=false;s.onActivity{a->shown=find(a.settings,tr("已提交，感谢你的反馈。","Submitted. Thank you for your feedback."))!=null&&find(a.settings,tr("查看反馈回执","View feedback receipt"))?.visibility==View.VISIBLE};shown}
            capture("feedback-public-success.png")
            val record=JSONObject().put("sourceCommit",BuildConfig.SOURCE_COMMIT).put("issueCreatedBySourceCommit",old?.optString("issueCreatedBySourceCommit")?:BuildConfig.SOURCE_COMMIT).put("origin",BuildConfig.FEEDBACK_URL).put("body",body).put("requestId",id).put("version",version).put("receipt",receipt)
                .put("language",lang).put("nativeUiSubmitted",true).put("nativeReceiptVerified",true).put("nativeInlineSuccessShown",true).put("nativeSuccessDialogShown",false).put("nativeReceiptControlShown",true).put("draftClearedAfterVerification",true)
            file.writeText(record.toString(2))
            val offline="Offline test draft stays on this device; do not submit publicly."
            var offlineId=""
            s.onActivity{a->input(a.settings)!!.setText(offline);offlineId=a.model.prefs.getString("feedback_id",null)!!}
            val wifi=Settings.Global.getInt(ctx.contentResolver,"wifi_on",1)
            val mobile=Settings.Global.getInt(ctx.contentResolver,"mobile_data",1)
            try{
                shell("svc wifi disable");shell("svc data disable")
                val cm=ctx.getSystemService(ConnectivityManager::class.java)
                i.uiAutomation.adoptShellPermissionIdentity(android.Manifest.permission.ACCESS_NETWORK_STATE)
                try{await(15000){cm.activeNetwork==null}}
                finally{i.uiAutomation.dropShellPermissionIdentity()}
                tap(s,submit)
                await(60000){var failed=false;s.onActivity{a->failed=find(a.settings,submit)?.isEnabled==true&&find(a.settings,tr("请联网刷新后重试，草稿已保留。","Connect to the internet, refresh and retry. Draft retained."))!=null};failed}
                s.onActivity{a->assertEquals(offline,a.feedback.draft);assertEquals(offlineId,a.model.prefs.getString("feedback_id",null));assertEquals(receipt,a.model.prefs.getString("feedback_receipt",null))}
                capture("feedback-offline-draft-retained.png")
                record.put("offlineFailureShown",true).put("offlineDraftRetained",true).put("offlineRequestIdentityRetained",true).put("failedDraftNotPublished",true)
                file.writeText(record.toString(2))
            }finally{shell("svc wifi ${if(wifi==0)"disable"else"enable"}");shell("svc data ${if(mobile==0)"disable"else"enable"}")}
        }
    }
}
