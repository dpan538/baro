package studio.sundown.weather

import android.Manifest
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** BARO's real geocoder and location fallback on its disposable emulator. */
class NativeLocationTest {
    private val i=InstrumentationRegistry.getInstrumentation()
    @Test fun realPlaceSearchManualSelectionAndRecreationRetainTheChosenPlace(){
        val done=CountDownLatch(1);var found=emptyList<Place>()
        ActivityScenario.launch(MainActivity::class.java).use{s->
            s.onActivity{a->a.repository.search("Brisbane","en"){places,error->assertNull(error);found=places!!;done.countDown()}}
            assertTrue(done.await(15,TimeUnit.SECONDS));assertTrue(found.isNotEmpty());val selected=found.first()
            s.onActivity{a->a.setLocation(false);a.selectPlace(selected)}
            val end=SystemClock.uptimeMillis()+25000;var ready=false
            while(!ready&&SystemClock.uptimeMillis()<end){s.onActivity{ready=it.model.weather!=null&&it.model.status=="ready"};SystemClock.sleep(100)}
            assertTrue(ready);s.onActivity{a->assertFalse(a.locationEnabled());assertEquals(selected.name,a.model.place.name);assertEquals(selected.key,a.model.weather!!.place.key)}
            s.recreate();i.waitForIdleSync();s.onActivity{assertEquals(selected.name,it.model.place.name);assertFalse(it.locationEnabled())}
        }
    }
    @Test fun deniedPermissionKeepsSelectedPlaceAndManualModeRecovers(){
        val pkg=i.targetContext.packageName
        for(permission in listOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))i.uiAutomation.revokeRuntimePermission(pkg,permission)
        ActivityScenario.launch(MainActivity::class.java).use{s->
            var before:Place?=null;s.onActivity{a->before=a.model.place;a.setLocation(true)}
            fun deny(node:android.view.accessibility.AccessibilityNodeInfo?):Boolean{
                if(node==null)return false
                if(node.viewIdResourceName=="com.android.permissioncontroller:id/permission_deny_button"||node.text?.toString() in listOf("Don’t allow","Don't allow","不允许"))return node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                for(n in 0 until node.childCount)if(deny(node.getChild(n)))return true
                return false
            }
            var denied=false;val end=SystemClock.uptimeMillis()+5000
            while(!denied&&SystemClock.uptimeMillis()<end){denied=deny(i.uiAutomation.rootInActiveWindow);if(!denied)SystemClock.sleep(100)}
            // Android may suppress a repeatedly denied permission dialog and call back directly.
            i.waitForIdleSync();SystemClock.sleep(300)
            s.onActivity{a->assertEquals("denied",a.model.locationStatus);assertEquals(before!!.key,a.model.place.key);a.selectPlace(before!!);assertFalse(a.locationEnabled());assertEquals("off",a.model.locationStatus)}
        }
    }
}
