package studio.sundown.weather

import android.animation.ValueAnimator
import android.content.Context
import android.view.*
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import kotlin.math.*

class TwoPageHost(context:Context,val model:AppModel,val weatherPage:NativePage,val observationPage:NativePage):FrameLayout(context){
    private val density=resources.displayMetrics.density
    private var decision=SwipeDecision(ViewConfiguration.get(context).scaledTouchSlop.toFloat(),24*density)
    private var tracker:VelocityTracker?=null
    private var dx=0f;private var sign=1
    private var animator:ValueAnimator?=null
    var overlayOpen=false
    var onPageChanged:(()->Unit)?=null
    var onTransition:((Boolean)->Unit)?=null
    val currentPage get()=model.page
    var lastSettleDirection=0;private set
    init{addView(weatherPage,LayoutParams(-1,-1));addView(observationPage,LayoutParams(-1,-1));post{reset()}}
    fun syncPage(){if(animator==null&&dx==0f)reset()}
    private fun current()=if(model.page==0)weatherPage else observationPage
    private fun other()=if(model.page==0)observationPage else weatherPage
    private fun reset(){current().translationX=0f;other().translationX=width.toFloat();onTransition?.invoke(false)}
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){super.onSizeChanged(w,h,oldw,oldh);animator?.cancel();animator=null;reset()}
    override fun onInterceptTouchEvent(event:MotionEvent):Boolean {
        if(overlayOpen)return false
        when(event.actionMasked){
            MotionEvent.ACTION_DOWN->{animator?.end();animator=null;tracker?.recycle();tracker=VelocityTracker.obtain();tracker?.addMovement(event);dx=0f
                val board=current().dashboard;val x=event.x/density;val y=event.y/density
                val excluded=board.dialRect.contains(x,y)||board.gearRect.contains(x,y)||board.paletteRect.contains(x,y)||board.placeRect.contains(x,y)
                val insets=if(android.os.Build.VERSION.SDK_INT>=30)rootWindowInsets?.getInsets(android.view.WindowInsets.Type.systemGestures()) else null
                val edge=max(24*density,max(insets?.left?:0,insets?.right?:0).toFloat())
                decision=SwipeDecision(ViewConfiguration.get(context).scaledTouchSlop.toFloat(),edge);decision.begin(event.x,event.y,width.toFloat(),excluded)
            }
            MotionEvent.ACTION_POINTER_DOWN->{decision.cancel();if(dx!=0f)settle(false);return false}
            MotionEvent.ACTION_MOVE->{tracker?.addMovement(event);dx=decision.move(event.x,event.y);if(decision.locked){parent?.requestDisallowInterceptTouchEvent(true);onTransition?.invoke(true);position(dx);return true}}
            MotionEvent.ACTION_CANCEL,MotionEvent.ACTION_UP->{tracker?.recycle();tracker=null}
        };return false
    }
    private fun position(offset:Float){dx=offset.coerceIn(-width.toFloat(),width.toFloat());sign=if(dx>=0)1 else -1;current().translationX=dx;other().translationX=dx-sign*width}
    override fun onTouchEvent(event:MotionEvent):Boolean {
        tracker?.addMovement(event)
        when(event.actionMasked){
            MotionEvent.ACTION_MOVE->position(decision.move(event.x,event.y))
            MotionEvent.ACTION_UP->{tracker?.computeCurrentVelocity(1000);val velocity=tracker?.xVelocity?:0f;val commit=decision.finish(dx,velocity/density,width.toFloat(),false);tracker?.recycle();tracker=null;settle(commit)}
            MotionEvent.ACTION_POINTER_DOWN,MotionEvent.ACTION_CANCEL->{decision.cancel();tracker?.recycle();tracker=null;settle(false)}
        };return true
    }
    private fun settle(commit:Boolean){
        val from=dx;val to=if(commit)sign*width.toFloat() else 0f
        val old=current();val next=other();val direction=sign
        animator?.cancel()
        fun finish(){if(commit){model.page=1-model.page;lastSettleDirection=direction};old.translationX=if(commit)width.toFloat() else 0f;next.translationX=if(commit)0f else width.toFloat();dx=0f;animator=null;onPageChanged?.invoke();onTransition?.invoke(false)}
        if(!ValueAnimator.areAnimatorsEnabled()){finish();return}
        animator=ValueAnimator.ofFloat(from,to).apply{duration=180;interpolator=LinearInterpolator();addUpdateListener{val x=it.animatedValue as Float;old.translationX=x;next.translationX=x-direction*width};addListener(object:android.animation.AnimatorListenerAdapter(){var cancelled=false;override fun onAnimationCancel(a:android.animation.Animator){cancelled=true};override fun onAnimationEnd(a:android.animation.Animator){if(!cancelled)finish()}});start()}
    }
}
