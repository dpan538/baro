package studio.sundown.weather

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.animation.LinearInterpolator

/** A transient native snapshot blends old ink into the new layout without owning input. */
class ContentTransition(private val host:View) {
    private var animator:ValueAnimator?=null
    private var image:Bitmap?=null
    private var overlay:BitmapDrawable?=null
    val running get()=overlay!=null
    fun change(bounds:Rect=Rect(0,0,host.width,host.height),exclude:List<Rect> = emptyList(),update:()->Unit){
        if(!ValueAnimator.areAnimatorsEnabled()||!host.isAttachedToWindow||bounds.isEmpty){clear();update();return}
        // Capture the currently blended result before cancelling an interrupted
        // transition, so repeated clicks continue from what is on screen.
        val snapshot=runCatching{
            Bitmap.createBitmap(bounds.width(),bounds.height(),Bitmap.Config.ARGB_8888).also{bitmap->
                val canvas=Canvas(bitmap);canvas.translate(-bounds.left.toFloat(),-bounds.top.toFloat())
                exclude.forEach{canvas.clipOutRect(it)};host.draw(canvas)
            }
        }.getOrNull()
        clear()
        if(snapshot==null){update();return}
        image=snapshot
        val drawable=BitmapDrawable(host.resources,snapshot).apply{this.bounds=bounds;alpha=255}
        overlay=drawable;host.overlay.add(drawable)
        update()
        host.postOnAnimation{
            if(overlay!==drawable)return@postOnAnimation
            animator=ValueAnimator.ofInt(255,0).apply{
                duration=320;interpolator=LinearInterpolator()
                addUpdateListener{drawable.alpha=it.animatedValue as Int;host.invalidate()}
                addListener(object:AnimatorListenerAdapter(){override fun onAnimationEnd(animation:Animator){if(overlay===drawable)clear()}})
                start()
            }
        }
    }
    fun clear(){
        val old=animator;animator=null;old?.removeAllListeners();old?.cancel()
        overlay?.let{host.overlay.remove(it)};overlay=null
        image?.recycle();image=null;host.invalidate()
    }
}
