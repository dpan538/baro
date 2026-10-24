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
    private val dp get()=activity::dp
    private val font=Typeface.createFromAsset(context.assets,"fonts/MiSansVF.ttf")
    private var panel:LinearLayout?=null
    private var scroller:ScrollView?=null
    private var scrollAnimator:ValueAnimator?=null
    private var detail=""
    private var updateCard:LinearLayout?=null
    private var updateMessage:TextView?=null
    private var updateAction:Button?=null
    private var updateProgress:ProgressBar?=null
    private var locationText:TextView?=null
    private var locationSwitch:Switch?=null
    private var feedbackSend:Button?=null
    private var feedbackStatus:TextView?=null
    private var feedbackInput:EditText?=null
    private var uiGeneration=0L
    init{visibility=GONE;isFocusable=true}
    private fun text(value:String,size:Float=22f)=TextView(context).apply{text=value;textSize=size;setTextColor(INK);typeface=font;includeFontPadding=false}
    private fun button(value:String,selected:Boolean=false,click:()->Unit):Button=Button(context).apply{
        text=value;textSize=18f;typeface=font;isAllCaps=false;minHeight=dp(56);minimumHeight=dp(56);setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled),intArrayOf()),intArrayOf(0xff88887f.toInt(),INK)));isSelected=selected
        val shape=GradientDrawable().apply{setColor(if(selected)0xffdeded6.toInt() else 0xfff4f4ee.toInt());cornerRadius=dp(8).toFloat();if(selected)setStroke(dp(1),INK)}
        background=RippleDrawable(ColorStateList.valueOf(0x24000000),shape,null)
        setPadding(dp(14),dp(10),dp(14),dp(10));PressFeedback.install(this);setOnClickListener{PressFeedback.click(this);click()}
    }
    private fun column()=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL}
    private fun row()=LinearLayout(context).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
    private fun gap(target:LinearLayout,height:Int){target.addView(View(context),LinearLayout.LayoutParams(1,dp(height)))}
    private fun line(target:LinearLayout){target.addView(View(context).apply{setBackgroundColor(0xffdcdcd3.toInt())},LinearLayout.LayoutParams(-1,dp(1)))}
    fun open(){
        animate().cancel();detail="";visibility=VISIBLE;activity.pager.overlayOpen=true;build();alpha=0f
        if(ValueAnimator.areAnimatorsEnabled())animate().alpha(1f).setDuration(220).setInterpolator(LinearInterpolator()).start()else alpha=1f
        requestFocus();activity.redraw()
    }
    fun close(){
        uiGeneration++;closeUpdate();activity.feedback.cancel();scrollAnimator?.cancel();animate().cancel();visibility=GONE;activity.pager.overlayOpen=false;detail="";feedbackInput=null;activity.redraw()
    }
    fun back(){if(updateCard!=null)closeUpdate()else if(detail.isNotEmpty()){detail="";build()}else close()}
    fun cancelRequests(){activity.updates.cancel();activity.feedback.cancel();feedbackSend?.isEnabled=true
        if(updateCard!=null){updateMessage?.text=tr("请求已暂停，可重试","Request paused. Retry when ready.");updateAction?.isEnabled=true;updateAction?.text=tr("重试","Retry");updateAction?.setOnClickListener{checkUpdate()}}
    }
    fun dispose(){uiGeneration++;scrollAnimator?.cancel();activity.updates.cancel();activity.feedback.cancel()}
    private fun options(labels:List<String>,chosen:Int,language:Boolean=false,action:(Int)->Unit):View{
        val box=FrameLayout(context).apply{setPadding(dp(4),dp(4),dp(4),dp(4));background=GradientDrawable().apply{setColor(0xfff2f2ed.toInt());cornerRadius=dp(12).toFloat();setStroke(dp(1),0xffcecec6.toInt())}}
        val pill=View(context).apply{tag="baro-segment-indicator";importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO;background=GradientDrawable().apply{setColor(INK);cornerRadius=dp(8).toFloat()}}
        box.addView(pill,LayoutParams(0,-1))
        val buttons=mutableListOf<Button>();val choices=row();var selected=chosen;var animation:ValueAnimator?=null
        fun position(index:Int):Float{val available=box.width-box.paddingLeft-box.paddingRight;val step=(available-dp(6)*(labels.size-1)).toFloat()/labels.size;return index*(step+dp(6))}
        labels.forEachIndexed{index,label->
            val control=button(label){ }.apply{
                isSelected=index==chosen;setTextColor(if(isSelected)PAPER else INK)
                background=RippleDrawable(ColorStateList.valueOf(0x24989890),null,GradientDrawable().apply{setColor(-1);cornerRadius=dp(8).toFloat()})
                setOnClickListener{
                    PressFeedback.click(this)
                    if(index!=selected){
                        animation?.cancel();val start=pill.translationX;val colors=buttons.map{it.currentTextColor};selected=index
                        buttons.forEachIndexed{n,b->b.isSelected=n==index};action(index);activity.redraw()
                        fun apply(f:Float){pill.translationX=start+(position(index)-start)*f;buttons.forEachIndexed{n,b->b.setTextColor(androidx.core.graphics.ColorUtils.blendARGB(colors[n],if(n==index)PAPER else INK,f))}}
                        if(android.animation.ValueAnimator.areAnimatorsEnabled())animation=ValueAnimator.ofFloat(0f,1f).apply{duration=200;interpolator=LinearInterpolator();addUpdateListener{apply(it.animatedValue as Float)};start()}else apply(1f)
                        if(language){val generation=uiGeneration;postDelayed({if(generation==uiGeneration&&visibility==VISIBLE)build()},if(ValueAnimator.areAnimatorsEnabled())200 else 0)}
                    }
                }
            }
            buttons.add(control);choices.addView(control,LinearLayout.LayoutParams(0,dp(56),1f).apply{rightMargin=if(index<labels.lastIndex)dp(6)else 0})
        }
        box.addView(choices,LayoutParams(-1,dp(56)))
        box.addOnLayoutChangeListener{_,l,_,r,_,oldL,_,oldR,_->if(r-l!=oldR-oldL){animation?.cancel();pill.layoutParams=pill.layoutParams.apply{width=((box.width-box.paddingLeft-box.paddingRight-dp(6)*(labels.size-1))/labels.size).coerceAtLeast(0)};pill.translationX=position(selected)}}
        box.addOnAttachStateChangeListener(object:View.OnAttachStateChangeListener{override fun onViewAttachedToWindow(v:View){};override fun onViewDetachedFromWindow(v:View){animation?.cancel()}})
        return box
    }
    fun refreshLocation(){
        locationText?.text=when(model.locationStatus){"fix"->tr("已使用当前位置","Using current location");"waiting"->tr("定位中；暂用所选地点","Locating; using selected place");"permission"->tr("等待定位权限","Waiting for location permission");"denied"->tr("定位被拒绝；使用所选地点","Location denied; using selected place");"system_off"->tr("系统定位已关闭；使用所选地点","System location is off; using selected place");"timeout"->tr("定位超时；使用所选地点","Location timed out; using selected place");"unavailable"->tr("定位不可用；使用所选地点","Location unavailable; using selected place");else->tr("定位未开启","Location is off")}
        locationSwitch?.setOnCheckedChangeListener(null);locationSwitch?.isChecked=activity.locationEnabled();locationSwitch?.setOnCheckedChangeListener{_,on->activity.setLocation(on)}
    }
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){super.onSizeChanged(w,h,oldw,oldh);if(visibility==VISIBLE&&w!=oldw)post{build()}}
    private fun build(){
        uiGeneration++
        val y=scroller?.scrollY?:0
        val card=updateCard
        removeAllViews();updateCard=null
        val landscape=resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val paneWidth=if(landscape)max(dp(360),width/2) else width
        val pane=column().apply{setBackgroundColor(PAPER);setPadding(dp(28),dp(20),dp(28),dp(12))};panel=pane
        addView(pane,LayoutParams(paneWidth,-1,Gravity.START))
        val heading=row();heading.addView(text(if(detail.isEmpty())tr("设置","Settings")else when(detail){"feedback"->tr("意见反馈","Feedback");"licenses"->tr("数据来源与许可","Sources & licenses");else->tr("更多","More")},32f),LinearLayout.LayoutParams(0,-2,1f))
        if(detail.isNotEmpty())heading.addView(button("‹"){detail="";build()},LinearLayout.LayoutParams(dp(56),dp(56)))
        heading.addView(button("×"){close()},LinearLayout.LayoutParams(dp(56),dp(56)));pane.addView(heading)
        val body=column();val scroll=ScrollView(context).apply{isFillViewport=false;isFocusable=true;addView(body);setOnTouchListener{_,e->if(e.actionMasked==MotionEvent.ACTION_DOWN)scrollAnimator?.cancel();false}};scroller=scroll
        scroll.setOnGenericMotionListener{_,e->if(e.actionMasked==MotionEvent.ACTION_SCROLL)scrollAnimator?.cancel();false};scroll.setOnKeyListener{_,_,e->if(e.action==android.view.KeyEvent.ACTION_DOWN)scrollAnimator?.cancel();false}
        if(detail.isEmpty()){
            val nav=row();val anchors=mutableListOf<View>()
            listOf(tr("界面","Appearance"),tr("单位","Units"),tr("关于","About")).forEachIndexed{i,label->nav.addView(button(label){
                scrollAnimator?.cancel();val to=anchors.getOrNull(i)?.top?:0
                if(!ValueAnimator.areAnimatorsEnabled())scroll.scrollTo(0,to)else scrollAnimator=ValueAnimator.ofInt(scroll.scrollY,to).apply{duration=240;interpolator=LinearInterpolator();addUpdateListener{scroll.scrollTo(0,it.animatedValue as Int)};start()}
            },LinearLayout.LayoutParams(0,dp(56),1f).apply{setMargins(dp(2),dp(12),dp(2),dp(12))})};pane.addView(nav)
            fun category(name:String){val title=text(name,20f).apply{setPadding(0,dp(24),0,dp(18))};anchors.add(title);body.addView(title)}
            fun setting(index:Int,name:String,content:View){val group=column();gap(group,18);group.addView(text(String.format(java.util.Locale.US,"%02d   %s",index,name),24f));gap(group,16);group.addView(content);gap(group,20);body.addView(group);line(body)}
            category(tr("界面与视觉","Appearance"))
            setting(1,tr("界面语言","Language"),options(listOf("中文","English"),if(model.lang=="zh")0 else 1,language=true){model.lang=if(it==0)"zh" else "en"})
            setting(2,tr("像素画风","Pixel style"),options(listOf(tr("精细","Fine"),tr("大块像素","Block")),if(model.block)1 else 0){model.block=it==1})
            val motion=Switch(context).apply{text=tr("天气动画","Weather animation");typeface=font;textSize=20f;minHeight=dp(56);isChecked=model.motion;setOnCheckedChangeListener{_,on->model.motion=on;activity.redraw()}}
            setting(3,tr("天气动画","Weather animation"),motion)
            category(tr("气象单位","Weather units"))
            setting(4,tr("温度单位","Temperature"),options(listOf("°C", "°F"),if(model.fahrenheit)1 else 0){model.fahrenheit=it==1})
            setting(5,tr("风速单位","Wind speed"),options(listOf("km/h","m/s","mph","kts"),listOf("kmh","ms","mph","kts").indexOf(model.wind)){model.wind=listOf("kmh","ms","mph","kts")[it]})
            category(tr("系统与关于","System & about"))
            val loc=column();locationSwitch=Switch(context).apply{text=tr("仅在前台跟随定位","Follow in foreground");typeface=font;textSize=20f;minHeight=dp(56)};locationText=text("",16f);loc.addView(locationSwitch);gap(loc,10);loc.addView(locationText);setting(6,tr("自动定位","Location"),loc);refreshLocation()
            val about=column();about.addView(text("${BuildConfig.VERSION_NAME} · ${BuildConfig.VERSION_CODE}",20f));gap(about,14);about.addView(button(tr("检查更新","Check for updates")){openUpdate()});gap(about,12);about.addView(button(tr("数据来源与许可","Sources & licenses")){detail="licenses";build()});setting(7,tr("软件版本","App version"),about)
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
                body.addView(button(tr("数据来源与许可","Sources & licenses")){detail="licenses";build()});gap(body,16)
                body.addView(button(tr("意见反馈","Feedback")){detail="feedback";build()});gap(body,16)
                body.addView(button("GitHub · dpan538/baro"){activity.external("https://github.com/dpan538/baro")})
            }
        }
        gap(body,32);pane.addView(scroll,LinearLayout.LayoutParams(-1,0,1f));line(pane)
        val footer=row();footer.addView(button(tr("数据来源 Open-Meteo · BARO","Data by Open-Meteo · BARO")){detail="licenses";build()},LinearLayout.LayoutParams(0,dp(56),1f));footer.addView(button("···"){detail="more";build()},LinearLayout.LayoutParams(dp(64),dp(56)));pane.addView(footer)
        scroll.post{if(detail.isEmpty())scroll.scrollTo(0,y)}
        if(card!=null){updateCard=card;addView(card,cardLayout())}
    }
    private fun cardLayout():LayoutParams {
        val landscape=resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val available=if(landscape)width/2 else width
        return LayoutParams(min(dp(560),max(dp(280),available-dp(32))),-2,Gravity.END or Gravity.TOP).apply{setMargins(dp(16),dp(16),dp(16),dp(16))}
    }
    private fun openUpdate(){
        closeUpdate()
        val box=column().apply{setPadding(dp(24),dp(16),dp(24),dp(24));background=GradientDrawable().apply{setColor(PAPER);cornerRadius=dp(18).toFloat();setStroke(dp(1),0xffaaa99d.toInt())};elevation=dp(10).toFloat()}
        val top=row();top.addView(text("BARO",26f),LinearLayout.LayoutParams(0,-2,1f));top.addView(button("×"){closeUpdate()},LinearLayout.LayoutParams(dp(64),dp(64)));box.addView(top)
        box.addView(text(tr("软件更新","App update"),28f));gap(box,12);box.addView(text(tr("当前版本 ","Installed ")+BuildConfig.VERSION_NAME+" · "+BuildConfig.VERSION_CODE,18f));gap(box,12)
        updateMessage=text("",20f)
        val messageScroll=object:ScrollView(context){
            override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int){
                val cap=min(dp(260),max(dp(64),this@SettingsPanel.height-dp(440)))
                super.onMeasure(widthMeasureSpec,MeasureSpec.makeMeasureSpec(cap,MeasureSpec.AT_MOST))
            }
        }.apply{addView(updateMessage)}
        box.addView(messageScroll);gap(box,12);updateProgress=ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal).apply{max=100;visibility=GONE};box.addView(updateProgress,LinearLayout.LayoutParams(-1,dp(12)));gap(box,12)
        updateAction=button(tr("检查中…","Checking…")){checkUpdate()};updateAction!!.isEnabled=false;box.addView(updateAction);gap(box,10);box.addView(button(tr("发布记录","Release history")){activity.external("https://github.com/dpan538/baro/releases")})
        updateCard=box;addView(box,cardLayout());box.alpha=0f;if(ValueAnimator.areAnimatorsEnabled())box.animate().alpha(1f).setDuration(180).setInterpolator(LinearInterpolator()).start()else box.alpha=1f
        val pending=activity.updates.pending()
        if(pending!=null){updateMessage?.text=tr("已校验待安装包：","Verified package: ")+pending.version;installAction()}else checkUpdate()
    }
    private fun checkUpdate(){
        updateAction?.isEnabled=false;updateMessage?.text=tr("正在检查发布信息…","Checking published releases…")
        activity.updates.check(BuildConfig.DEBUG||BuildConfig.VERSION_NAME.contains("preview")){release,state->
            if(updateCard==null)return@check
            updateAction?.isEnabled=true
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
    private fun installAction(){updateAction?.text=tr("交给系统安装器","Open system installer");updateAction?.isEnabled=true;updateAction?.setOnClickListener{runCatching{activity.updates.install(activity)}.onFailure{updateMessage?.text=tr("安装失败，现有数据保留。","Installation failed; existing data retained.")}}}
    fun installPermissionReturned(){if(updateCard!=null){updateMessage?.text=if(activity.packageManager.canRequestPackageInstalls())tr("已允许此来源安装，可继续。","Source allowed. Continue installation.")else tr("尚未允许安装，可重试。","Installation permission not granted. Retry.");installAction()}}
    private fun closeUpdate(){activity.updates.cancel();updateCard?.animate()?.cancel();updateCard?.let{removeView(it)};updateCard=null;updateMessage=null;updateAction=null;updateProgress=null}
}
