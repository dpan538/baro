package studio.sundown.weather

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.view.*
import android.view.animation.LinearInterpolator
import android.widget.*
import kotlin.math.min

/** Native city search follows the same type, surface and control system as Settings. */
class PlaceDialog(private val activity:MainActivity):Dialog(activity){
    private val model get()=activity.model
    private val font=Typeface.createFromAsset(context.assets,"fonts/MiSansVF.ttf")
    private val mono=Typeface.createFromAsset(context.assets,"fonts/RobotoMono.ttf")
    private var job:NetworkJob?=null
    private lateinit var card:LinearLayout
    private lateinit var query:EditText
    private lateinit var search:Button
    private lateinit var status:TextView
    private lateinit var results:LinearLayout
    private var closing=false
    private fun dp(n:Int)=(n*context.resources.displayMetrics.density).toInt()
    private fun shape(color:Int,radius:Int=9)=GradientDrawable().apply{setColor(color);cornerRadius=dp(radius).toFloat()}
    private fun text(value:String,size:Float,color:Int=INK)=TextView(context).apply{text=value;textSize=size;setTextColor(color);typeface=font;includeFontPadding=false}
    private fun button(label:String,primary:Boolean=false,action:()->Unit)=Button(context).apply{
        text=label;isAllCaps=false;textSize=18f;typeface=font;minHeight=dp(56);minimumHeight=dp(56)
        setTextColor(if(primary)PAPER else INK);background=RippleDrawable(ColorStateList.valueOf(0x24989890),shape(if(primary)INK else 0xfff0f0eb.toInt()),null)
        setPadding(dp(12),dp(8),dp(12),dp(8));PressFeedback.install(this);setOnClickListener{PressFeedback.click(this);action()}
    }
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState);requestWindowFeature(Window.FEATURE_NO_TITLE)
        card=LinearLayout(context).apply{tag="baro-place-dialog";orientation=LinearLayout.VERTICAL;background=shape(PAPER,16);setPadding(dp(24),dp(20),dp(24),dp(20))}
        val header=LinearLayout(context).apply{gravity=Gravity.CENTER_VERTICAL}
        header.addView(text(model.tr("选择地点","CHOOSE PLACE"),26f),LinearLayout.LayoutParams(0,-2,1f))
        header.addView(button("×"){dismiss()}.apply{contentDescription=model.tr("关闭地点选择","Close place picker");background=null;textSize=32f},LinearLayout.LayoutParams(dp(56),dp(56)))
        card.addView(header)
        card.addView(text(model.tr("当前地点：","Selected: ")+model.placeName(),16f,0xff62635d.toInt()),LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(18)})
        query=EditText(context).apply{
            tag="baro-place-query";hint=model.tr("输入城市名称","Enter a city name");setSingleLine(true);maxLines=1;textSize=20f;typeface=font
            setTextColor(INK);setHintTextColor(0xff74756f.toInt());setPadding(dp(14),dp(12),dp(14),dp(12));background=shape(0xfff1f1ec.toInt()).apply{setStroke(dp(1),0xffb8b9b0.toInt())}
            filters=arrayOf(android.text.InputFilter.LengthFilter(80));imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener{_,action,_->if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){findPlaces();true}else false}
        }
        card.addView(query,LinearLayout.LayoutParams(-1,dp(60)))
        val actions=LinearLayout(context)
        search=button(model.tr("搜索","Search"),true){findPlaces()}.apply{tag="baro-place-search"}
        actions.addView(search,LinearLayout.LayoutParams(0,dp(56),1f).apply{rightMargin=dp(10)})
        actions.addView(button(model.tr("使用当前位置","Use current location")){activity.setLocation(true);dismiss()},LinearLayout.LayoutParams(0,dp(56),1f))
        card.addView(actions,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(12)})
        status=text(model.tr("选择搜索结果后会自动更新天气。","Choose a result to update the weather."),16f,0xff62635d.toInt()).apply{tag="baro-place-status";accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE}
        card.addView(status,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(12);bottomMargin=dp(8)})
        results=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL;tag="baro-place-results"}
        card.addView(ScrollView(context).apply{addView(results);isFillViewport=false},LinearLayout.LayoutParams(-1,0,1f))
        card.addView(button(model.tr("重新获取所选地点天气","Refresh selected place")){activity.refresh(true);dismiss()},LinearLayout.LayoutParams(-1,dp(56)).apply{topMargin=dp(12)})
        setContentView(card);window?.apply{setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT));setDimAmount(.20f);setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)}
    }
    override fun onStart(){
        super.onStart();val d=context.resources.displayMetrics.density
        val width=min(dp(720),(activity.root.width-dp(32)).coerceAtLeast(dp(280)))
        val height=min(dp(640),(activity.root.height*.9f).toInt()).coerceAtLeast(dp(340))
        window?.setLayout(width,height)
        if(android.animation.ValueAnimator.areAnimatorsEnabled()){card.alpha=0f;card.translationY=12*d;card.animate().alpha(1f).translationY(0f).setDuration(280).setInterpolator(LinearInterpolator()).start()}
    }
    private fun findPlaces(){
        val value=query.text.toString().trim()
        if(value.length<2){status.text=model.tr("请至少输入两个字符。","Enter at least two characters.");return}
        job?.cancel();search.isEnabled=false;status.text=model.tr("正在搜索地点…","Searching places…");results.removeAllViews()
        (context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(query.windowToken,0)
        job=activity.repository.search(value,model.lang){places,error->
            if(!isShowing||closing)return@search
            search.isEnabled=true;status.text=when{error!=null->model.tr("搜索失败，请重试。","Search failed. Please retry.");places.isNullOrEmpty()->model.tr("没有匹配地点，请换一个名称。","No matching places. Try another name.");else->model.tr("请选择地点","Choose a place")}
            places?.forEach{place->
                val row=LinearLayout(context).apply{
                    tag="baro-place-result";orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(12),dp(14),dp(12));minimumHeight=dp(80);isFocusable=true;isClickable=true
                    background=RippleDrawable(ColorStateList.valueOf(0x24989890),shape(0xfff1f1ec.toInt()),null)
                    addView(text(place.name,20f));addView(text(String.format(java.util.Locale.US,"%.3f°, %.3f° · %s",place.latitude,place.longitude,place.zone),14f,0xff62635d.toInt()).apply{typeface=mono},LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(6)})
                    PressFeedback.install(this);setOnClickListener{PressFeedback.click(this);activity.selectPlace(place);dismiss()}
                };results.addView(row,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(8)})
            }
        }
    }
    fun cancelSearch(){job?.cancel();if(::search.isInitialized){search.isEnabled=true;if(::status.isInitialized)status.text=model.tr("搜索已暂停，可重试。","Search paused. You can retry.")}}
    fun dismissFromHost(){job?.cancel();closing=true;if(::card.isInitialized)card.animate().cancel();super.dismiss()}
    override fun dismiss(){
        if(closing)return;job?.cancel();closing=true
        if(isShowing&&::card.isInitialized&&android.animation.ValueAnimator.areAnimatorsEnabled()&&!activity.isFinishing){
            card.animate().alpha(0f).translationY(dp(10).toFloat()).setDuration(220).setInterpolator(LinearInterpolator()).withEndAction{super.dismiss()}.start()
        }else super.dismiss()
    }
}
