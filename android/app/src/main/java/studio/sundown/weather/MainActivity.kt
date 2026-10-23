package studio.sundown.weather

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.location.*
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*

class MainActivity:Activity(){
    lateinit var model:AppModel;private set
    lateinit var repository:WeatherRepository;private set
    lateinit var updates:Updates;private set
    lateinit var feedback:Feedback;private set
    lateinit var pager:TwoPageHost;private set
    lateinit var root:FrameLayout;private set
    lateinit var settings:SettingsPanel;private set
    private lateinit var weatherArt:PixelArt
    private lateinit var observationArt:PixelArt
    private var visible=false
    private var locating=false
    private var lastWeatherAttempt=0L
    private var locationGeneration=0L
    private val main=Handler(Looper.getMainLooper())
    private val devicePrefs by lazy{getSharedPreferences("sundown-device",MODE_PRIVATE)}
    private val locationManager by lazy{getSystemService(LocationManager::class.java)}
    private var placeSearch:NetworkJob?=null
    private val listener=object:LocationListener{
        override fun onLocationChanged(location:Location){
            if(!visible||!locating||!devicePrefs.getBoolean("location_follow",false))return
            if(!location.latitude.isFinite()||!location.longitude.isFinite())return
            stopLocation();model.locationStatus="fix"
            selectPlace(Place(model.tr("当前位置","Current location"),location.latitude,location.longitude,model.place.zone),manual=false)
        }
        override fun onProviderDisabled(provider:String){model.locationStatus="system_off";stopLocation();settings.refreshLocation()}
        override fun onProviderEnabled(provider:String){}
        @Deprecated("Legacy provider status") override fun onStatusChanged(provider:String?,status:Int,extras:Bundle?){}
    }
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        model=AppModel(this);repository=WeatherRepository(this);updates=Updates(this,repository.pool);feedback=Feedback(model.prefs,repository.pool,repository.backend)
        updates.cleanInstalled()
        window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        if(Build.VERSION.SDK_INT>=30)window.setDecorFitsSystemWindows(false)
        root=FrameLayout(this).apply{setBackgroundColor(FRAME);setOnApplyWindowInsetsListener{v,insets->
            if(Build.VERSION.SDK_INT>=30){val a=insets.getInsets(WindowInsets.Type.displayCutout() or WindowInsets.Type.captionBar());val bar=insets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars());val ime=insets.getInsets(WindowInsets.Type.ime());val landscape=resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;v.setPadding(a.left,if(landscape)maxOf(a.top,bar.top)else a.top,a.right,maxOf(a.bottom,ime.bottom))}
            else{@Suppress("DEPRECATION") v.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom)}
            insets
        }}
        weatherArt=PixelArt(this,false);observationArt=PixelArt(this,true)
        weatherArt.elapsed=savedInstanceState?.getDouble("weather_elapsed")?:0.0;observationArt.elapsed=savedInstanceState?.getDouble("observation_elapsed")?:0.0
        val weatherBoard=WeatherDashboard(this,model);val obsBoard=ObservationDashboard(this,model)
        weatherBoard.onSettings={settings.open()};weatherBoard.onPlace={showPlaces()};obsBoard.onPurpose={redraw()}
        pager=TwoPageHost(this,model,NativePage(this,weatherBoard,weatherArt),NativePage(this,obsBoard,observationArt))
        pager.onPageChanged={redraw()};pager.onTransition={transition->artLifecycle(transition)}
        root.addView(pager,FrameLayout.LayoutParams(-1,-1))
        settings=SettingsPanel(this);root.addView(settings,FrameLayout.LayoutParams(-1,-1));setContentView(root)
        immersive()
        if(Build.VERSION.SDK_INT>=33)onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT){back()}
        model.weather=repository.cached(model.place)
        redraw()
    }
    private fun immersive(){if(Build.VERSION.SDK_INT>=30){window.insetsController?.apply{systemBarsBehavior=WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE;hide(WindowInsets.Type.systemBars())}}else{@Suppress("DEPRECATION")
        window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE}}
    override fun onWindowFocusChanged(hasFocus:Boolean){super.onWindowFocusChanged(hasFocus);if(hasFocus)immersive()}
    override fun onResume(){super.onResume();visible=true;immersive();refresh();if(devicePrefs.getBoolean("location_follow",false))startLocation();artLifecycle();main.post(ticker)
        if(updates.installing){updates.installing=false;settings.installPermissionReturned()}}
    override fun onPause(){visible=false;main.removeCallbacks(ticker);repository.cancelWeather();placeSearch?.cancel();feedback.cancel();settings.cancelRequests();stopLocation();artLifecycle();super.onPause()}
    override fun onDestroy(){placeSearch?.cancel();settings.dispose();weatherArt.dispose();observationArt.dispose();repository.close();super.onDestroy()}
    override fun onSaveInstanceState(out:Bundle){out.putDouble("weather_elapsed",weatherArt.elapsed);out.putDouble("observation_elapsed",observationArt.elapsed);super.onSaveInstanceState(out)}
    private val ticker=object:Runnable{override fun run(){
        if(!visible)return
        val wx=model.weather;val before=wx?.let{model.window(it)}
        model.now=System.currentTimeMillis()/1000
        if(wx!=null&&model.window(wx)!=before)redraw()else if(model.page==1)pager.observationPage.dashboard.invalidate()
        if(wx!=null&&model.now-wx.fetchedAt>=CACHE_SECONDS&&model.status!="loading"&&model.now-lastWeatherAttempt>=60)refresh()
        main.postDelayed(this,1000)
    }}
    fun refresh(force:Boolean=false){
        if(!visible)return
        lastWeatherAttempt=System.currentTimeMillis()/1000
        model.status="loading";redraw();val place=model.place
        repository.refresh(place,force){weather,error->
            model.weather=weather;model.status=if(error==null)"ready" else "offline"
            if(weather!=null)model.place=weather.place
            redraw()
        }
    }
    fun redraw(){
        model.now=System.currentTimeMillis()/1000
        pager.syncPage();root.setBackgroundColor(if(model.page==0)0xff191e1d.toInt()else FRAME);pager.weatherPage.updateState();pager.observationPage.updateState()
        val enabled=model.motion&&android.animation.ValueAnimator.areAnimatorsEnabled()
        weatherArt.weather=model.weather;observationArt.weather=model.weather;observationArt.sampleTime=model.weather?.let{model.window(it).start}
        weatherArt.block=model.block;observationArt.block=model.block;weatherArt.motion=enabled;observationArt.motion=enabled
        pager.weatherPage.requestLayout();pager.observationPage.requestLayout();pager.weatherPage.dashboard.invalidate();pager.observationPage.dashboard.invalidate();weatherArt.refresh();observationArt.refresh();artLifecycle()
    }
    private fun artLifecycle(transition:Boolean=false){weatherArt.active=visible&&model.weather!=null&&(model.page==0||transition);observationArt.active=visible&&model.weather?.let{it.scene(model.window(it).start)!="unknown"}==true&&(model.page==1||transition)}
    fun selectPlace(place:Place,manual:Boolean=true){
        repository.cancelWeather();model.place=place;model.weather=repository.cached(place)
        if(manual){devicePrefs.edit().putBoolean("location_follow",false).apply();stopLocation();model.locationStatus="off"}
        refresh(true)
    }
    fun locationEnabled()=devicePrefs.getBoolean("location_follow",false)
    fun setLocation(enabled:Boolean){devicePrefs.edit().putBoolean("location_follow",enabled).apply()
        if(!enabled){stopLocation();model.locationStatus="off";settings.refreshLocation();return}
        if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){model.locationStatus="permission";requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION),901)}else startLocation()
        settings.refreshLocation()
    }
    private fun startLocation(){
        stopLocation();if(!visible||!locationEnabled())return
        if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){model.locationStatus="denied";settings.refreshLocation();return}
        if(!locationManager.isLocationEnabled){model.locationStatus="system_off";settings.refreshLocation();return}
        val candidates=if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)listOf(LocationManager.NETWORK_PROVIDER,LocationManager.GPS_PROVIDER)else listOf(LocationManager.NETWORK_PROVIDER)
        val providers=candidates.filter{locationManager.isProviderEnabled(it)}
        if(providers.isEmpty()){model.locationStatus="unavailable";settings.refreshLocation();return}
        val gen=++locationGeneration;locating=true;model.locationStatus="waiting"
        try{providers.forEach{locationManager.requestSingleUpdate(it,listener,Looper.getMainLooper())};main.postDelayed({if(locating&&gen==locationGeneration){stopLocation();model.locationStatus="timeout";settings.refreshLocation()}},20000)}catch(_:SecurityException){stopLocation();model.locationStatus="denied"}
        settings.refreshLocation()
    }
    private fun stopLocation(){locationGeneration++;locating=false;locationManager.removeUpdates(listener)}
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==901){if(grantResults.any{it==PackageManager.PERMISSION_GRANTED})startLocation()else{devicePrefs.edit().putBoolean("location_follow",false).apply();model.locationStatus="denied";settings.refreshLocation()}}}
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Deprecated("API 29–32 fallback") override fun onBackPressed(){back()}
    private fun back(){if(settings.visibility==View.VISIBLE)settings.back()else finish()}
    fun showPlaces(){
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(24),dp(12),dp(24),dp(12))}
        val search=EditText(this).apply{hint=model.tr("城市名称","City name");setSingleLine(true);maxLines=1;filters=arrayOf(android.text.InputFilter.LengthFilter(80))}
        val status=TextView(this);val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};val scroll=ScrollView(this).apply{addView(list)}
        val button=Button(this).apply{text=model.tr("搜索","Search");minHeight=dp(56)}
        box.addView(search);box.addView(button);box.addView(status);box.addView(scroll,LinearLayout.LayoutParams(-1,dp(330)))
        val dialog=AlertDialog.Builder(this).setTitle(model.tr("选择地点","Choose place")).setView(box).setNeutralButton(model.tr("重新获取天气","Refresh weather")){_,_->refresh(true)}.setNegativeButton(model.tr("取消","Cancel"),null).create()
        dialog.setOnDismissListener{placeSearch?.cancel()}
        button.setOnClickListener{val q=search.text.toString().trim();if(q.length<2)return@setOnClickListener;placeSearch?.cancel();button.isEnabled=false;status.text=model.tr("搜索中…","Searching…");list.removeAllViews()
            placeSearch=repository.search(q,model.lang){places,error->button.isEnabled=true;status.text=if(error!=null)model.tr("搜索失败，请重试","Search failed. Retry.")else if(places.isNullOrEmpty())model.tr("没有匹配地点","No matching places")else ""
                places?.forEach{place->list.addView(Button(this).apply{text=place.name;minHeight=dp(56);setOnClickListener{selectPlace(place);dialog.dismiss()}})}}}
        dialog.show()
    }
    fun external(url:String){val u=android.net.Uri.parse(url);if(u.scheme!="https")return;runCatching{startActivity(Intent(Intent.ACTION_VIEW,u))}}
    fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
}
