package studio.sundown.weather

import android.animation.ValueAnimator
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator

/** Small native press travel; the platform still owns clicks, ripples and cancellation. */
object PressFeedback {
    fun install(view:View){
        view.setOnTouchListener{v,event->
            when(event.actionMasked){
                MotionEvent.ACTION_DOWN->travel(v,.985f,65)
                MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->travel(v,1f,110)
            }
            false
        }
    }
    private fun travel(view:View,to:Float,duration:Long){
        view.animate().cancel()
        if(!ValueAnimator.areAnimatorsEnabled()){view.scaleX=1f;view.scaleY=1f;return}
        view.animate().scaleX(to).scaleY(to).setDuration(duration).setInterpolator(LinearInterpolator()).start()
    }
    fun click(view:View){view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)}
}
