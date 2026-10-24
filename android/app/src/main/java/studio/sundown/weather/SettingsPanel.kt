package studio.sundown.weather

import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.view.animation.LinearInterpolator
import android.widget.*
import kotlin.math.*

class SettingsPanel(private val activity:MainActivity):FrameLayout(activity){
    private val model get()=activity.model
    private val tr get()=model::tr
    private fun dp(value:Number)=(value.toFloat()*resources.displayMetrics.density).roundToInt()
    private val font=Typeface.createFromAsset(context.assets,"fonts/MiSansVF.ttf")
    private val mono=Typeface.createFromAsset(context.assets,"fonts/RobotoMono.ttf")
    private val inter=Typeface.createFromAsset(context.assets,"fonts/Inter.ttf")
    private val ink=0xff201f1b.toInt()
    private val muted=0xff62635d.toInt()
    private var cw=8.4f
    private var activeSection=0
    private var listScrollY=0
    private var navIndicator:View?=null
    private val navButtons=mutableListOf<Button>()
    private var panel:LinearLayout?=null
    private var scroller:ScrollView?=null
    private var scrollAnimator:ValueAnimator?=null
    private var detail=""
    private var updateCard:ScrollView?=null
    private var updateMessage:TextView?=null
    private var updateAction:Button?=null
    private var updateProgress:ProgressBar?=null
    private var updateLatest:TextView?=null
    private var cardAvailableHeight=Int.MAX_VALUE
    private var locationText:TextView?=null
    private var locationSwitch:CompoundButton?=null
    private var feedbackSend:Button?=null
    private var feedbackStatus:TextView?=null
    private var feedbackInput:EditText?=null
    private var uiGeneration=0L
    init{visibility=GONE;isFocusable=true;isClickable=true}
    private fun text(value:String,size:Float=22f)=TextView(context).apply{text=value;textSize=size;setTextColor(ink);typeface=font;includeFontPadding=false;letterSpacing=-.02f}
    private fun button(value:String,selected:Boolean=false,flat:Boolean=false,primary:Boolean=false,control:Button=Button(context),click:()->Unit):Button=control.apply{
        text=value;textSize=18f;typeface=font;isAllCaps=false;minHeight=dp(56);minimumHeight=dp(56);setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled),intArrayOf()),intArrayOf(0xff88887f.toInt(),ink)));isSelected=selected
        setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled),intArrayOf()),intArrayOf(0xff88887f.toInt(),if(primary)PAPER else ink)));includeFontPadding=false;letterSpacing=-.015f
        val shape=GradientDrawable().apply{setColor(if(primary)ink else if(flat)android.graphics.Color.TRANSPARENT else if(selected)0xffdeded6.toInt() else 0xfff2f2ed.toInt());cornerRadius=dp(if(flat)0 else 9).toFloat();if(selected)setStroke(dp(1),ink)}
        background=RippleDrawable(ColorStateList.valueOf(0x24000000),shape,null)
        setPadding(dp(14),dp(10),dp(14),dp(10));PressFeedback.install(this);setOnClickListener{PressFeedback.click(this);click()}
    }
    private fun column()=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL}
    private fun row()=LinearLayout(context).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
    private fun gap(target:LinearLayout,height:Number){target.addView(View(context),LinearLayout.LayoutParams(1,dp(height)))}
    private fun line(target:LinearLayout){target.addView(View(context).apply{setBackgroundColor(0xffcecec6.toInt())},LinearLayout.LayoutParams(-1,dp(1)))}
    private fun cross(back:Boolean=false,close:()->Unit)=button(if(back)"‹"else"×",flat=true,click=close).apply{
        setPadding(0,0,0,0);setTextColor(android.graphics.Color.TRANSPARENT)
        val portrait=this@SettingsPanel.height>this@SettingsPanel.width
        foreground=SettingsMark(dp(if(portrait)3.8f*cw else 4.62f*cw).toFloat(),back)
        contentDescription=if(back)tr("返回设置","Back to settings")else tr("关闭","Close")
    }
    private fun dots(size:Float,gap:Float)=row().apply{
        for((n,color)in listOf(ORANGE,YELLOW,BLUE).withIndex())addView(View(context).apply{background=GradientDrawable().apply{shape=GradientDrawable.OVAL;setColor(color)}},LinearLayout.LayoutParams(dp(size),dp(size)).apply{leftMargin=if(n==0)0 else dp(gap)})
    }
    private fun setSection(index:Int){
        activeSection=index;navButtons.forEachIndexed{n,b->b.setTextColor(if(n==index)ink else muted);b.isSelected=n==index}
        val b=navButtons.getOrNull(index)?:return;val marker=navIndicator?:return
        marker.layoutParams=marker.layoutParams.apply{width=b.width};marker.animate().cancel()
        if(ValueAnimator.areAnimatorsEnabled())marker.animate().translationX(b.left.toFloat()).setDuration(220).setInterpolator(LinearInterpolator()).start()else marker.translationX=b.left.toFloat()
    }
    fun open(){
        animate().cancel();detail="";visibility=VISIBLE;activity.pager.overlayOpen=true;build();alpha=0f
        translationX=dp(12).toFloat()
        if(ValueAnimator.areAnimatorsEnabled())animate().alpha(1f).translationX(0f).setDuration(220).setInterpolator(LinearInterpolator()).start()else{alpha=1f;translationX=0f}
        requestFocus();activity.redraw()
    }
    fun close(){
        uiGeneration++;val generation=uiGeneration;closeUpdate();activity.feedback.cancel();scrollAnimator?.cancel();animate().cancel();activity.pager.overlayOpen=false;detail="";feedbackInput=null;activity.redraw()
        if(ValueAnimator.areAnimatorsEnabled())animate().alpha(0f).translationX(-dp(8).toFloat()).setDuration(160).setInterpolator(LinearInterpolator()).withEndAction{if(generation==uiGeneration)visibility=GONE}.start()else visibility=GONE
    }
    fun back(){if(updateCard!=null)closeUpdate()else if(detail.isNotEmpty()){detail="";build()}else close()}
    fun cancelRequests(){activity.updates.cancel();activity.feedback.cancel();feedbackSend?.isEnabled=true
        if(updateCard!=null){updateMessage?.text=tr("请求已暂停，可重试","Request paused. Retry when ready.");updateAction?.isEnabled=true;updateAction?.text=tr("重试","Retry");updateAction?.setOnClickListener{checkUpdate()}}
    }
    fun dispose(){uiGeneration++;animate().cancel();scrollAnimator?.cancel();activity.updates.cancel();activity.feedback.cancel()}
    private fun options(labels:List<String>,chosen:Int,language:Boolean=false,pixels:Boolean=false,action:(Int)->Unit):View{
        val box=FrameLayout(context).apply{setPadding(dp(5),dp(5),dp(5),dp(5));background=GradientDrawable().apply{setColor(0xfff2f2ed.toInt());cornerRadius=dp(12).toFloat();setStroke(dp(1),0xffcecec6.toInt())}}
        val pill=View(context).apply{tag="baro-segment-indicator";importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO;background=GradientDrawable().apply{setColor(ink);cornerRadius=dp(8).toFloat()}}
        box.addView(pill,LayoutParams(0,-1))
        val buttons=mutableListOf<Button>();val choices=row();var selected=chosen;var animation:ValueAnimator?=null
        val controlHeight=max(56f,cw*(if(pixels)7.1f else 7f))
        fun position(index:Int):Float{val available=box.width-box.paddingLeft-box.paddingRight;val step=(available-dp(6)*(labels.size-1)).toFloat()/labels.size;return index*(step+dp(6))}
        labels.forEachIndexed{index,label->
            val control=button(label,control=if(pixels)SettingsPixelButton(context,index==0,dp(6.6f*cw),dp(4.3f*cw),dp(2*cw))else Button(context)){ }.apply{
                textSize=(2.8f*cw).coerceIn(18f,26f);typeface=Typeface.create(if(language&&index==0)font else inter,450,false);setPadding(dp(.6f*cw),0,dp(.6f*cw),0)
                isSelected=index==chosen;setTextColor(if(isSelected)PAPER else ink)
                background=RippleDrawable(ColorStateList.valueOf(0x24989890),null,GradientDrawable().apply{setColor(-1);cornerRadius=dp(8).toFloat()})
                setOnClickListener{
                    PressFeedback.click(this)
                    if(index!=selected){
                        animation?.cancel();val start=pill.translationX;val colors=buttons.map{it.currentTextColor};selected=index
                        buttons.forEachIndexed{n,b->b.isSelected=n==index;(b.compoundDrawables[0] as? SettingsPixelIcon)?.tintColor=if(n==index)YELLOW else ink};action(index);activity.redraw()
                        fun apply(f:Float){pill.translationX=start+(position(index)-start)*f;buttons.forEachIndexed{n,b->b.setTextColor(androidx.core.graphics.ColorUtils.blendARGB(colors[n],if(n==index)PAPER else ink,f))}}
                        if(android.animation.ValueAnimator.areAnimatorsEnabled())animation=ValueAnimator.ofFloat(0f,1f).apply{duration=200;interpolator=LinearInterpolator();addUpdateListener{apply(it.animatedValue as Float)};start()}else apply(1f)
                        if(language){val generation=uiGeneration;postDelayed({if(generation==uiGeneration&&visibility==VISIBLE)build()},if(ValueAnimator.areAnimatorsEnabled())200 else 0)}
                    }
                }
            }
            buttons.add(control);choices.addView(control,LinearLayout.LayoutParams(0,dp(controlHeight),1f).apply{rightMargin=if(index<labels.lastIndex)dp(6)else 0})
        }
        box.addView(choices,LayoutParams(-1,dp(controlHeight)))
        box.addOnLayoutChangeListener{_,l,_,r,_,oldL,_,oldR,_->if(r-l!=oldR-oldL){animation?.cancel();pill.layoutParams=pill.layoutParams.apply{width=((box.width-box.paddingLeft-box.paddingRight-dp(6)*(labels.size-1))/labels.size).coerceAtLeast(0)};pill.translationX=position(selected)}}
        box.addOnAttachStateChangeListener(object:View.OnAttachStateChangeListener{override fun onViewAttachedToWindow(v:View){};override fun onViewDetachedFromWindow(v:View){animation?.cancel()}})
        return box
    }
    fun refreshLocation(){
        locationText?.text=when(model.locationStatus){"fix"->tr("已使用当前位置","Using current location");"waiting"->tr("定位中；暂用所选地点","Locating; using selected place");"permission"->tr("等待定位权限","Waiting for location permission");"denied"->tr("定位被拒绝；使用所选地点","Location denied; using selected place");"system_off"->tr("系统定位已关闭；使用所选地点","System location is off; using selected place");"timeout"->tr("定位超时；使用所选地点","Location timed out; using selected place");"unavailable"->tr("定位不可用；使用所选地点","Location unavailable; using selected place");else->tr("定位未开启","Location is off")}
        locationSwitch?.setOnCheckedChangeListener(null);locationSwitch?.isChecked=activity.locationEnabled();locationSwitch?.setOnCheckedChangeListener{_,on->activity.setLocation(on)}
    }
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){
        super.onSizeChanged(w,h,oldw,oldh)
        if(visibility==VISIBLE)post{
            if(w!=oldw)build()else if(h!=oldh){
                // Resize around IME insets without recreating the active editor.
                val density=resources.displayMetrics.density
                val landscape=resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE
                val m=DesignMetrics(activity,"weather-${if(landscape)"landscape"else"portrait"}-${model.lang}");m.configure(width/density,height/density,activity.root.paddingTop/density)
                val b=m.rect(".information-panel")
                panel?.layoutParams=LayoutParams(dp(b.width()),dp(b.height()),Gravity.START or Gravity.TOP).apply{leftMargin=dp(b.left);topMargin=dp(b.top)}
                updateCard?.layoutParams=cardLayout()
            }
        }
    }
    private fun showDetail(value:String){
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(windowToken,0)
        closeUpdate();activity.feedback.cancel();detail=value;build()
    }
    private fun build(){
        uiGeneration++;scrollAnimator?.cancel();navButtons.clear();navIndicator=null
        val y=listScrollY
        val card=updateCard
        removeAllViews();updateCard=null
        val landscape=resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val density=resources.displayMetrics.density
        val metrics=DesignMetrics(activity,"weather-${if(landscape)"landscape"else"portrait"}-${model.lang}")
        metrics.configure(width/density,height/density,activity.root.paddingTop/density)
        val bounds=metrics.rect(".information-panel");cw=bounds.width()/100
        val pane=column().apply{
            tag="baro-settings-pane";setBackgroundColor(PAPER)
            background=GradientDrawable().apply{setColor(PAPER);val radius=dp((min(this@SettingsPanel.width/density,this@SettingsPanel.height/density)*.014f).coerceIn(14f,26f)).toFloat();cornerRadii=if(landscape)floatArrayOf(radius,radius,0f,0f,0f,0f,radius,radius)else floatArrayOf(radius,radius,radius,radius,0f,0f,0f,0f)}
        };panel=pane
        addView(pane,LayoutParams(dp(bounds.width()),dp(bounds.height()),Gravity.START or Gravity.TOP).apply{leftMargin=dp(bounds.left);topMargin=dp(bounds.top)})
        val header=column().apply{setPadding(dp(4.4f*cw),dp((if(landscape)3.4f else 2.8f)*cw),dp(4.4f*cw),0)}
        val heading=row();val titleGroup=row()
        if(detail.isNotEmpty())titleGroup.addView(cross(true){detail="";build()},LinearLayout.LayoutParams(dp(56),dp(56)))
        titleGroup.addView(text(if(detail.isEmpty())tr("设置","SETTINGS")else when(detail){"feedback"->tr("意见反馈","FEEDBACK");"licenses"->tr("数据来源与许可","SOURCES & LICENSES");else->tr("了解更多","MORE")},cw*(if(landscape){if(model.lang=="en")3.5f else 3.6f}else 3.4f)).apply{typeface=if(model.lang=="en")mono else Typeface.create(font,450,false)})
        titleGroup.addView(dots(1.65f*cw,.8f*cw),LinearLayout.LayoutParams(-2,-2).apply{leftMargin=dp(1.8f*cw)})
        heading.addView(titleGroup,LinearLayout.LayoutParams(0,-2,1f))
        val closeSize=max(56f,cw*(if(landscape)5.8f else 5.2f))
        heading.addView(cross{close()},LinearLayout.LayoutParams(dp(closeSize),dp(closeSize)))
        header.addView(heading,LinearLayout.LayoutParams(-1,dp(closeSize)));pane.addView(header)
        val body=column().apply{setPadding(dp(4.4f*cw),0,dp(4.4f*cw),0)}
        val scroll=ScrollView(context).apply{tag="baro-settings-scroll";isFillViewport=false;isFocusable=true;addView(body);setOnTouchListener{_,e->if(e.actionMasked==MotionEvent.ACTION_DOWN)scrollAnimator?.cancel();false}};scroller=scroll
        scroll.setOnGenericMotionListener{_,e->if(e.actionMasked==MotionEvent.ACTION_SCROLL)scrollAnimator?.cancel();false};scroll.setOnKeyListener{_,_,e->if(e.action==android.view.KeyEvent.ACTION_DOWN)scrollAnimator?.cancel();false}
        if(detail.isEmpty()){
            val navBox=FrameLayout(context);val nav=row();val anchors=mutableListOf<View>();navButtons.clear()
            val navHeight=max(56f,6.45f*cw)+2
            listOf(tr("界面","Appearance"),tr("单位","Units"),tr("关于","About")).forEachIndexed{i,label->
                val control=button(label,flat=true){
                    scrollAnimator?.cancel();val to=min(anchors.getOrNull(i)?.top?:0,max(0,body.height-scroll.height));setSection(i)
                    if(!ValueAnimator.areAnimatorsEnabled())scroll.scrollTo(0,to)else scrollAnimator=ValueAnimator.ofInt(scroll.scrollY,to).apply{duration=240;interpolator=LinearInterpolator();addUpdateListener{scroll.scrollTo(0,it.animatedValue as Int)};start()}
                }.apply{minWidth=0;minimumWidth=0;textSize=2.5f*cw;letterSpacing=-.02f;typeface=Typeface.create(font,450,false);setPadding(0,dp(1.4f*cw),0,dp(1.8f*cw))}
                navButtons.add(control);nav.addView(control,LinearLayout.LayoutParams(-2,dp(navHeight)).apply{rightMargin=dp(3*cw)})
            }
            navBox.addView(nav,LayoutParams(-1,-1));navIndicator=View(context).apply{setBackgroundColor(ink)}
            navBox.addView(navIndicator,LayoutParams(0,dp(2),Gravity.BOTTOM or Gravity.START))
            header.addView(navBox,LinearLayout.LayoutParams(-1,dp(navHeight)).apply{topMargin=dp(2*cw)})
            line(pane)
            fun category(name:String,color:Int){
                val category=column().apply{setPadding(0,dp(3*cw),0,0)};val title=row().apply{setPadding(0,dp(cw),0,dp(1.6f*cw))}
                title.addView(View(context).apply{background=GradientDrawable().apply{shape=GradientDrawable.OVAL;setColor(color)}},LinearLayout.LayoutParams(dp(8),dp(8)).apply{rightMargin=dp(1.1f*cw)})
                title.addView(text(name,(2.4f*cw).coerceIn(16f,22f)).apply{setTextColor(muted)})
                category.addView(title);line(category);anchors.add(category)
                body.addView(category,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(3*cw)})
            }
            fun caption(index:Int,name:String)=column().apply{
                addView(text(String.format(java.util.Locale.US,"%02d",index),max(14f,1.9f*cw)).apply{tag="baro-setting-index";typeface=mono;setTextColor(muted)})
                gap(this,1.1f*cw);addView(text(name,if(model.lang=="en")3*cw else (3.4f*cw).coerceIn(22f,32f)).apply{typeface=if(model.lang=="en")mono else Typeface.create(font,450,false)})
            }
            fun setting(index:Int,name:String,content:View,last:Boolean=false){
                val group=column();group.addView(caption(index,name));gap(group,2*cw);group.addView(content);gap(group,3*cw);if(!last)line(group)
                body.addView(group,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=if(last)0 else dp(3*cw)})
            }
            fun toggle(label:String,checked:Boolean,action:(Boolean)->Unit)=SettingsToggle(context,dp(max(96f,9.8f*cw)),dp(max(56f,5.6f*cw))).apply{contentDescription=label;isChecked=checked;setOnCheckedChangeListener{_,on->action(on)}}
            fun switchSetting(index:Int,name:String,control:View,notes:List<View> = emptyList(),last:Boolean=false){
                val group=column();val row=row();row.addView(caption(index,name),LinearLayout.LayoutParams(0,-2,1f));row.addView(control);group.addView(row)
                for(note in notes){gap(group,1.4f*cw);group.addView(note)}
                gap(group,3*cw);if(!last)line(group);body.addView(group,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=if(last)0 else dp(3*cw)})
            }
            category(tr("界面与视觉","APPEARANCE"),ORANGE)
            setting(1,tr("界面语言","LANGUAGE"),options(listOf("中文","English"),if(model.lang=="zh")0 else 1,language=true){model.lang=if(it==0)"zh"else"en"})
            setting(2,tr("像素画风","PIXEL ART"),options(listOf(tr("精细","Fine"),tr("方块","Block")),if(model.block)1 else 0,pixels=true){model.block=it==1})
            switchSetting(3,tr("天气动画","ANIMATION"),toggle(tr("天气动画","Weather animation"),model.motion){model.motion=it;activity.redraw()},last=true)
            category(tr("气象单位","UNITS"),YELLOW)
            setting(4,tr("温度单位","TEMPERATURE"),options(listOf("°C "+tr("摄氏","Celsius"),"°F "+tr("华氏","Fahrenheit")),if(model.fahrenheit)1 else 0){model.fahrenheit=it==1})
            setting(5,tr("风速单位","WIND SPEED"),options(listOf("km/h","m/s","mph","kts"),listOf("kmh","ms","mph","kts").indexOf(model.wind)){model.wind=listOf("kmh","ms","mph","kts")[it]},last=true)
            category(tr("系统与关于","ABOUT"),BLUE)
            locationSwitch=toggle(tr("仅在前台跟随定位","Follow in foreground"),activity.locationEnabled()){activity.setLocation(it)}
            locationText=text("",(2.2f*cw).coerceIn(16f,20f)).apply{setTextColor(muted)}
            val locationNote=text(tr("仅在应用可见时跟随定位；未开启时使用所选地点。","Follows location while the app is visible. Uses the selected place when off."),(2.2f*cw).coerceIn(16f,20f)).apply{setTextColor(muted)}
            switchSetting(6,tr("自动定位","LOCATION"),locationSwitch!!,listOf(locationText!!,locationNote));refreshLocation()
            val version=row();version.addView(text("v${BuildConfig.VERSION_NAME} · ${BuildConfig.VERSION_CODE}",2.65f*cw).apply{typeface=mono},LinearLayout.LayoutParams(0,-2,1f))
            version.addView(button(tr("检查更新","Check updates"),primary=true){openUpdate()}.apply{textSize=(2.4f*cw).coerceIn(16f,22f);typeface=inter},LinearLayout.LayoutParams(-2,dp(56)))
            setting(7,tr("软件版本","VERSION"),version,last=true)
            scroll.setOnScrollChangeListener{_,_,sy,_,_->listScrollY=sy;if(scrollAnimator?.isRunning!=true){val index=if(sy+scroll.height>=body.height-2)2 else anchors.indexOfLast{it.top<=sy+dp(32)}.coerceAtLeast(0);if(index!=activeSection)setSection(index)}}
            navBox.post{setSection(activeSection)}
        }else when(detail){
            "feedback"->{
                body.addView(text(tr("反馈会公开发布到 BARO 的 GitHub Issue。请勿填写隐私信息。","Feedback is posted publicly as a BARO GitHub issue. Leave out private information."),18f));gap(body,16)
                val input=EditText(context).apply{textSize=20f;typeface=font;minLines=6;gravity=Gravity.TOP;setText(activity.feedback.draft);hint=tr("描述问题或建议（最多 1000 字符）","Describe the issue or suggestion (1000 characters)");addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){activity.feedback.draft=s.toString()};override fun afterTextChanged(s:Editable?){} })};feedbackInput=input;body.addView(input);gap(body,16)
                feedbackStatus=text("",18f);feedbackSend=button(tr("提交反馈","Submit feedback")){
                    feedbackSend?.isEnabled=false;feedbackStatus?.text=tr("正在发送并核实回执…","Sending and verifying receipt…")
                    activity.feedback.send{ok,message->feedbackSend?.isEnabled=true
                        if(ok){if(activity.feedback.draft.isEmpty())feedbackInput?.setText("");feedbackStatus?.text=tr("已收到反馈：","Received: ")+message;AlertDialog.Builder(context).setTitle(tr("反馈已送达","Feedback received")).setMessage(message).setPositiveButton(tr("完成","Done"),null).show()}
                        else feedbackStatus?.text=when(message){"invalid"->tr("请输入 1–1000 字符，草稿已保留。","Enter 1–1000 characters. Draft retained.");"unconfigured"->tr("BARO 反馈服务尚未配置，草稿已保留。","BARO feedback service is not configured. Draft retained.");else->tr("未核实送达，请重试。草稿已保留。","Delivery could not be verified. Retry; draft retained.")}
                    }
                };body.addView(feedbackSend);gap(body,12);body.addView(feedbackStatus)
                val old=model.prefs.getString("feedback_receipt",null);if(old!=null)body.addView(button(tr("上次反馈回执","Previous receipt")){activity.external(old)})
            }
            "licenses"->{
                body.addView(text(tr("天气与空气质量：Open-Meteo / CAMS，CC BY 4.0。读数是模型估计，非现场传感器实测。位置搜索使用 GeoNames / Open-Meteo。","Weather and air quality: Open-Meteo / CAMS, CC BY 4.0. Values are model estimates, not on-site sensor measurements. Places: GeoNames / Open-Meteo."),20f));gap(body,18)
                body.addView(text(tr("太阳几何、月相与月升落为近似计算；Bortle 未测。VPD、湿球、LCL 为近地近似；霞光指数不是概率，色温不是实测。字段缺失显示 —。","Sun geometry, lunar phase and crossings are approximate. Bortle is unmeasured. VPD, wet bulb and LCL are surface approximations; glow is not a probability and CCT is not measured. Missing values show —."),20f));gap(body,18)
                body.addView(text("MiSans · Xiaomi\nInter · Rasmus Andersson · SIL OFL 1.1\nRoboto Mono · Christian Robertson · SIL OFL 1.1\nSunCalc · Vladimir Agafonkin · BSD-2-Clause",20f))
                gap(body,18);body.addView(text(tr("AndroidX、apksig（APK 签名校验）、Kotlin 标准库与协程、JetBrains Annotations、Guava ListenableFuture：Apache 2.0。天气图形由 BARO 源码实时绘制。","AndroidX, apksig (APK signature verification), Kotlin standard library and coroutines, JetBrains Annotations and Guava ListenableFuture: Apache 2.0. Weather artwork is rendered live from BARO sources."),20f))
                gap(body,18);body.addView(text(tr("GitHub API：检查版本、下载发布附件与核实反馈回执。反馈通过独立 BARO 服务转发到 GitHub Issues；服务未配置时保留草稿。定位使用 Android 系统前台位置接口。","GitHub API: release discovery, release asset downloads and feedback receipt verification. A separate BARO service relays feedback to GitHub Issues; unconfigured delivery retains the draft. Location uses Android foreground location APIs."),20f))
                listOf("Open-Meteo" to "https://open-meteo.com/","CAMS" to "https://open-meteo.com/en/docs/air-quality-api","CC BY 4.0" to "https://creativecommons.org/licenses/by/4.0/").forEach{(name,url)->gap(body,12);body.addView(button(name){activity.external(url)})}
                listOf("MiSans-License.pdf","Inter-OFL.txt","RobotoMono-OFL.txt","SunCalc-BSD-2-Clause.txt","Apache-2.0.txt","Runtime-dependencies.txt").forEach{name->gap(body,12);body.addView(button(name){if(name.endsWith(".pdf")){activity.external("https://github.com/dpan538/baro/blob/main/dist/assets/fonts/MiSans/MiSans-License.pdf")}else{val license=context.assets.open("licenses/$name").bufferedReader().use{it.readText()};AlertDialog.Builder(context).setTitle(name).setMessage(license).setPositiveButton(tr("关闭","Close"),null).show()}})}
            }
            else->{
                body.addView(text(tr("数据缓存","Data cache"),24f));gap(body,14);val size=text("${activity.repository.bytes()} B",22f);body.addView(size);gap(body,12)
                body.addView(button(tr("清除天气缓存","Clear weather cache")){activity.repository.clear();model.weather=null;size.text="${activity.repository.bytes()} B";activity.model.status="offline";activity.redraw()});gap(body,24)
                body.addView(button(tr("数据来源与许可","Sources & licenses")){showDetail("licenses")});gap(body,16)
                body.addView(button(tr("意见反馈","Feedback")){showDetail("feedback")});gap(body,16)
                body.addView(button("GitHub · dpan538/baro"){activity.external("https://github.com/dpan538/baro")})
            }
        }
        if(detail.isNotEmpty()){line(pane);body.setPadding(dp(4.4f*cw),dp(3*cw),dp(4.4f*cw),dp(3*cw))}
        pane.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val footer=row().apply{setPadding(0,dp(cw),0,dp(cw));minimumHeight=dp(7.5f*cw)}
        val footerWrap=column().apply{setPadding(dp(4.4f*cw),0,dp(4.4f*cw),0)};line(footerWrap)
        footer.addView(button(tr("数据来源 Open-Meteo · BARO","Powered by Open-Meteo · BARO"),flat=true){showDetail("licenses")}.apply{typeface=mono;textSize=max(16f,1.9f*cw);letterSpacing=-.02f;gravity=Gravity.START or Gravity.CENTER_VERTICAL;setPadding(0,0,0,0)},LinearLayout.LayoutParams(0,dp(56),1f))
        footer.addView(button("···",flat=true){showDetail("more")}.apply{textSize=3*cw;setPadding(0,0,0,0)},LinearLayout.LayoutParams(dp(56),dp(56)))
        footerWrap.addView(footer);pane.addView(footerWrap)
        scroll.post{if(detail.isEmpty())scroll.scrollTo(0,y)}
        if(card!=null){updateCard=card;addView(card,cardLayout())}
    }
    private fun cardLayout():LayoutParams {
        val density=resources.displayMetrics.density
        val landscape=resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val metrics=DesignMetrics(activity,"weather-${if(landscape)"landscape"else"portrait"}-${model.lang}")
        metrics.configure(width/density,height/density,activity.root.paddingTop/density)
        val art=metrics.rect(".visual-panel");val inset=art.width()*.044f
        cardAvailableHeight=dp(art.height()-2*inset)
        return LayoutParams(min(dp(560),dp(art.width()-2*inset)),-2,Gravity.END or Gravity.TOP).apply{
            rightMargin=this@SettingsPanel.width-dp(art.right-inset);topMargin=dp(art.top+inset)
        }
    }
    private fun openUpdate(){
        closeUpdate()
        val box=column().apply{tag="baro-update-card";setPadding(dp(28),dp(28),dp(28),dp(28));background=GradientDrawable().apply{setColor(PAPER);cornerRadius=dp(22).toFloat();setStroke(dp(1),0xffd9d9cf.toInt())};elevation=dp(10).toFloat()}
        val top=row();val brand=row();brand.addView(text("BARO",24f).apply{typeface=mono});brand.addView(dots(16f,8f),LinearLayout.LayoutParams(-2,-2).apply{leftMargin=dp(14)})
        top.addView(brand,LinearLayout.LayoutParams(0,-2,1f));top.addView(cross{closeUpdate()}.apply{foreground=SettingsMark(dp(36).toFloat())},LinearLayout.LayoutParams(dp(64),dp(64)));box.addView(top)
        gap(box,18);box.addView(text("GitHub Releases",16f).apply{typeface=mono;setTextColor(muted)});gap(box,6)
        box.addView(text(tr("软件更新","Software updates"),34f));gap(box,16)
        updateMessage=text("",20f).apply{typeface=inter;setLineSpacing(0f,1.5f);minimumHeight=dp(60)}
        val messageScroll=object:ScrollView(context){
            override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int){
                val portrait=this@SettingsPanel.height>this@SettingsPanel.width
                val available=if(portrait)this@SettingsPanel.height/2 else this@SettingsPanel.height
                val cap=min(dp(200),max(dp(64),available-dp(560)))
                super.onMeasure(widthMeasureSpec,MeasureSpec.makeMeasureSpec(cap,MeasureSpec.AT_MOST))
            }
        }.apply{addView(updateMessage)}
        box.addView(messageScroll);gap(box,12);updateProgress=ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal).apply{max=100;visibility=GONE};box.addView(updateProgress,LinearLayout.LayoutParams(-1,dp(2)));gap(box,18)
        fun versionRow(label:String,value:String):TextView{
            val row=row().apply{setPadding(0,dp(12),0,dp(12))};row.addView(text(label,18f).apply{setTextColor(muted)},LinearLayout.LayoutParams(0,-2,1f))
            val v=text(value,20f).apply{typeface=mono};row.addView(v);box.addView(row);line(box);return v
        }
        versionRow(tr("当前版本","Installed"),"v"+BuildConfig.VERSION_NAME);updateLatest=versionRow(tr("最新发布","Latest release"),"—")
        val actions=row().apply{gravity=Gravity.END};gap(box,22)
        updateAction=button(tr("检查中…","Checking…"),primary=true){checkUpdate()};updateAction!!.isEnabled=false
        actions.addView(updateAction,LinearLayout.LayoutParams(-2,-2).apply{rightMargin=dp(12)})
        actions.addView(button(tr("查看发布页 ↗","View releases ↗"),primary=true){activity.external("https://github.com/dpan538/baro/releases")})
        box.addView(actions);gap(box,18);box.addView(text("github.com/dpan538/baro",16f).apply{typeface=mono;setTextColor(muted)})
        val host=object:ScrollView(context){
            override fun onMeasure(w:Int,h:Int){super.onMeasure(w,MeasureSpec.makeMeasureSpec(max(1,cardAvailableHeight),MeasureSpec.AT_MOST))}
        }.apply{tag="baro-update-card";addView(box);background=box.background;box.background=null;elevation=dp(10).toFloat();clipToOutline=true}
        updateCard=host;addView(host,cardLayout());host.alpha=0f;if(ValueAnimator.areAnimatorsEnabled())host.animate().alpha(1f).setDuration(180).setInterpolator(LinearInterpolator()).start()else host.alpha=1f
        val pending=activity.updates.pending()
        if(pending!=null){updateMessage?.text=tr("已校验待安装包：","Verified package: ")+pending.version;installAction()}else checkUpdate()
    }
    private fun checkUpdate(){
        updateAction?.isEnabled=false;updateMessage?.text=tr("正在检查发布信息…","Checking published releases…")
        activity.updates.check(BuildConfig.DEBUG||BuildConfig.VERSION_NAME.contains("preview")){release,state->
            if(updateCard==null)return@check
            updateAction?.isEnabled=true
            updateLatest?.text=if(release!=null)"v"+release.version else if(state=="current")"v"+BuildConfig.VERSION_NAME else "—"
            updateAction?.visibility=if(release!=null||state.startsWith("error"))VISIBLE else GONE
            updateMessage?.text=when(state){"available"->tr("可更新：","Available: ")+release!!.version+" · ${release.code}\n"+release.notes;"current"->tr("已是当前通道的最新版本。","You have the latest version in this channel.");"empty"->tr("当前通道尚无发布包。","No release in this channel.");"unversioned"->tr("发布暂未提供可用的安卓更新包。","This release has no available Android update package.");else->tr("检查失败，可重试。","Check failed. Retry.")}
            if(release!=null){updateAction?.text=tr("下载更新","Download update");updateAction?.setOnClickListener{download(release)}}else{updateAction?.text=tr("重试","Retry");updateAction?.setOnClickListener{checkUpdate()}}
        }
    }
    private fun download(release:Release){
        updateProgress?.visibility=VISIBLE;updateAction?.text=tr("取消下载","Cancel download");updateAction?.setOnClickListener{activity.updates.cancel();updateMessage?.text=tr("下载已取消","Download cancelled");updateProgress?.visibility=GONE;updateAction?.text=tr("重试下载","Retry download");updateAction?.setOnClickListener{download(release)}}
        activity.updates.download(release){percent,state->if(updateCard==null)return@download;updateProgress?.progress=percent
            when(state){"verified"->{updateMessage?.text=tr("包与当前签名已校验。","Package and installed signer verified.");installAction()};"downloading"->updateMessage?.text=tr("下载中 ","Downloading ")+"$percent%";else->{updateMessage?.text=tr("下载或校验失败，可重试。","Download or verification failed. Retry.");updateAction?.text=tr("重试下载","Retry download");updateAction?.setOnClickListener{download(release)}}}
        }
    }
    private fun installAction(){updateAction?.visibility=VISIBLE;updateAction?.text=tr("交给系统安装器","Open system installer");updateAction?.isEnabled=true;updateAction?.setOnClickListener{runCatching{activity.updates.install(activity)}.onFailure{updateMessage?.text=tr("安装失败，现有数据保留。","Installation failed; existing data retained.")}}}
    fun installPermissionReturned(){if(updateCard!=null){updateMessage?.text=if(activity.packageManager.canRequestPackageInstalls())tr("已允许此来源安装，可继续。","Source allowed. Continue installation.")else tr("尚未允许安装，可重试。","Installation permission not granted. Retry.");installAction()}}
    private fun closeUpdate(){activity.updates.cancel();updateCard?.animate()?.cancel();updateCard?.let{removeView(it)};updateCard=null;updateMessage=null;updateAction=null;updateProgress=null;updateLatest=null}
}
