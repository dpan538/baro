package studio.sundown.weather

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator

internal data class InkRegion(val bounds:RectF,val color:Int,val vertical:Boolean=false)

/** Uncover final, data-driven instrument ink without rerasterising the dashboard each frame. */
internal class PageInkReveal(context:Context):View(context){
    private val paint=Paint()
    private var animator:ValueAnimator?=null
    private var regions=emptyList<InkRegion>()
    internal var progress=1f;private set
    val running get()=animator!=null
    init{importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO;isClickable=false;visibility=INVISIBLE}
    fun start(ink:List<InkRegion>,done:()->Unit){
        clear()
        if(ink.isEmpty()||!ValueAnimator.areAnimatorsEnabled()||!isAttachedToWindow){done();return}
        regions=ink;progress=0f;visibility=VISIBLE
        animator=ValueAnimator.ofFloat(0f,1f).apply{
            duration=420;interpolator=LinearInterpolator()
            addUpdateListener{progress=it.animatedValue as Float;invalidate()}
            addListener(object:AnimatorListenerAdapter(){
                private var cancelled=false
                override fun onAnimationCancel(animation:Animator){cancelled=true}
                override fun onAnimationEnd(animation:Animator){if(!cancelled){clear();done()}}
            });start()
        }
    }
    override fun onDraw(canvas:Canvas){
        for(region in regions){
            val r=RectF(region.bounds)
            if(region.vertical)r.bottom-=r.height()*progress else r.left+=r.width()*progress
            paint.color=region.color;paint.alpha=((1-progress)*210).toInt()
            canvas.drawRect(r,paint)
        }
    }
    fun clear(){val old=animator;animator=null;old?.removeAllListeners();old?.cancel();regions=emptyList();progress=1f;visibility=INVISIBLE}
    override fun onDetachedFromWindow(){clear();super.onDetachedFromWindow()}
}
