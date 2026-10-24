package studio.sundown.weather

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.CompoundButton
import android.widget.Button
import androidx.core.graphics.ColorUtils
import kotlin.math.min

/** Native drawing of the frozen settings SVGs and switch, retaining native
 * CompoundButton semantics, keyboard operation and accessibility state. */
class SettingsToggle(context:Context,private val trackWidth:Int,private val trackHeight:Int):CompoundButton(context){
    private val density=resources.displayMetrics.density
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private var fraction=0f
    private var animator:ValueAnimator?=null
    private var ready=false
    init{buttonDrawable=null;isClickable=true;isFocusable=true;minHeight=trackHeight;minimumHeight=trackHeight;ready=true;PressFeedback.install(this)}
    override fun getAccessibilityClassName():CharSequence="android.widget.Switch"
    override fun performClick():Boolean{PressFeedback.click(this);return super.performClick()}
    override fun onMeasure(w:Int,h:Int){setMeasuredDimension(resolveSize(trackWidth,w),resolveSize(trackHeight,h))}
    override fun setChecked(value:Boolean){
        val changed=value!=isChecked;super.setChecked(value)
        if(!ready)return
        animator?.cancel();val target=if(value)1f else 0f
        if(changed&&isAttachedToWindow&&ValueAnimator.areAnimatorsEnabled()){
            animator=ValueAnimator.ofFloat(fraction,target).apply{duration=180;interpolator=LinearInterpolator();addUpdateListener{fraction=it.animatedValue as Float;invalidate()};start()}
        }else{fraction=target;invalidate()}
    }
    override fun onDraw(c:Canvas){
        val r=height/2f;paint.style=Paint.Style.FILL
        paint.color=ColorUtils.blendARGB(0xffddded6.toInt(),0xff201f1b.toInt(),fraction)
        c.drawRoundRect(0f,0f,width.toFloat(),height.toFloat(),r,r,paint)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=density;paint.color=ColorUtils.blendARGB(0xffcecec6.toInt(),0xff201f1b.toInt(),fraction)
        c.drawRoundRect(density/2,density/2,width-density/2,height-density/2,r-density/2,r-density/2,paint)
        val radius=(height-10*density)/2;val x=5*density+radius+(width-height)*fraction
        paint.style=Paint.Style.FILL;paint.color=PAPER;paint.setShadowLayer(3*density,0f,density,0x20151d1c);c.drawCircle(x,height/2f,radius,paint);paint.clearShadowLayer()
    }
    override fun onDetachedFromWindow(){animator?.cancel();super.onDetachedFromWindow()}
}

/** Center the SVG and label as one group, as the reference's flex button does. */
class SettingsPixelButton(context:Context,fine:Boolean,private val iconWidth:Int,private val iconHeight:Int,private val gap:Int):Button(context){
    private val icon=SettingsPixelIcon(fine)
    override fun onDraw(c:Canvas){
        val label=text.toString();val p=paint;p.color=currentTextColor
        val left=(width-iconWidth-gap-p.measureText(label))/2f
        icon.tintColor=if(isSelected)YELLOW else 0xff201f1b.toInt()
        icon.setBounds(left.toInt(),(height-iconHeight)/2,left.toInt()+iconWidth,(height+iconHeight)/2);icon.draw(c)
        val fm=p.fontMetrics;c.drawText(label,left+iconWidth+gap,height/2f-(fm.ascent+fm.descent)/2,p)
    }
}

class SettingsMark(private val size:Float,private val back:Boolean=false):Drawable(){
    private val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=0xff201f1b.toInt();style=Paint.Style.STROKE;strokeCap=Paint.Cap.ROUND}
    override fun draw(c:Canvas){
        c.save();c.translate(bounds.exactCenterX()-size/2,bounds.exactCenterY()-size/2);c.scale(size/32,size/32);p.strokeWidth=1.8f
        if(back){val path=Path().apply{moveTo(20f,7f);lineTo(11f,16f);lineTo(20f,25f)};c.drawPath(path,p)}else{c.drawLine(8f,8f,24f,24f,p);c.drawLine(24f,8f,8f,24f,p)}
        c.restore()
    }
    override fun setAlpha(alpha:Int){p.alpha=alpha}
    override fun setColorFilter(filter:ColorFilter?){p.colorFilter=filter}
    @Deprecated("Deprecated in Java") override fun getOpacity()=PixelFormat.TRANSLUCENT
}

class SettingsPixelIcon(private val fine:Boolean):Drawable(){
    var tintColor=0xff201f1b.toInt();set(value){field=value;invalidateSelf()}
    private val p=Paint().apply{isAntiAlias=false}
    override fun draw(c:Canvas){
        val s=min(bounds.width()/52f,bounds.height()/30f);c.save();c.translate(bounds.exactCenterX()-26*s,bounds.exactCenterY()-15*s);c.scale(s,s)
        val outline=Path().apply{
            if(fine){moveTo(0f,18f);lineTo(8f,18f);lineTo(8f,10f);lineTo(16f,10f);lineTo(16f,2f);lineTo(36f,2f);lineTo(36f,10f);lineTo(44f,10f);lineTo(44f,18f);lineTo(52f,18f);lineTo(52f,30f)}
            else{moveTo(0f,20f);lineTo(10f,20f);lineTo(10f,10f);lineTo(20f,10f);lineTo(20f,0f);lineTo(40f,0f);lineTo(40f,10f);lineTo(50f,10f);lineTo(50f,30f)}
            lineTo(0f,30f);close()
        }
        p.color=tintColor
        if(fine){c.clipPath(outline);for(y in 0..7)for(x in 0..12)c.drawRect(x*4+.5f,y*4+.5f,x*4+3.5f,y*4+3.5f,p)}else c.drawPath(outline,p)
        c.restore()
    }
    override fun setAlpha(alpha:Int){p.alpha=alpha}
    override fun setColorFilter(filter:ColorFilter?){p.colorFilter=filter}
    @Deprecated("Deprecated in Java") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
