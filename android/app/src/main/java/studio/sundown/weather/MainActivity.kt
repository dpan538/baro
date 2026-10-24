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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.ViewCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal

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
    private var layoutKey=""
    private var pageTransition=false
    private var touchInProgress=false
    private var locationGeneration=0L
    private val main=Handler(Looper.getMainLooper())
    private val resumeArtwork=Runnable{touchInProgress=false;artLifecycle()}
    private val devicePrefs by lazy{getSharedPreferences("sundown-device",MODE_PRIVATE)}
    private val locationManager by lazy{getSystemService(LocationManager::class.java)}
    private var placeSearch:NetworkJob?=null
    private val locationRequests=mutableListOf<CancellationSignal>()
    private var locationTimeout:Runnable?=null
    private var placeDialog:PlaceDialog?=null
    private fun acceptLocation(location:Location){
        if(!visible||!locating||!locationEnabled()||!usableLocation(location))return
        stopLocation();model.locationStatus="fix"
        selectPlace(Place(model.tr("当前位置","Current location"),location.latitude,location.longitude,model.place.zone),manual=false)
        settings.refreshLocation()
    }
    private fun usableLocation(location:Location):Boolean {
        val age=(SystemClock.elapsedRealtimeNanos()-location.elapsedRealtimeNanos)/1_000_000
        return validLocationFix(location.latitude,location.longitude,age,if(location.hasAccuracy())location.accuracy.toDouble()else null)
    }
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        model=AppModel(this);repository=WeatherRepository(this);updates=Updates(this,repository.pool);feedback=Feedback(model.prefs,repository.pool,repository.backend)
        updates.cleanInstalled()
        WindowCompat.setDecorFitsSystemWindows(window,false)
        window.decorView.setBackgroundColor(PAPER)
        root=FrameLayout(this).apply{setBackgroundColor(PAPER)}
        ViewCompat.setOnApplyWindowInsetsListener(root){v,insets->
            val chrome=insets.getInsets(WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.captionBar())
            val ime=insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(chrome.left,chrome.top,chrome.right,maxOf(chrome.bottom,ime.bottom))
            if(this::pager.isInitialized)pager.observationPage.requestLayout()
            insets
        }
        weatherArt=PixelArt(this,false);observationArt=PixelArt(this,true)
        weatherArt.elapsed=savedInstanceState?.getDouble("weather_elapsed")?:0.0;observationArt.elapsed=savedInstanceState?.getDouble("observation_elapsed")?:0.0
        val weatherBoard=WeatherDashboard(this,model);val obsBoard=ObservationDashboard(this,model)
        weatherBoard.onSettings={settings.open()};weatherBoard.onPlace={showPlaces()};obsBoard.onPurpose={obsBoard.animatePurposeChange();redraw()}
        pager=TwoPageHost(this,model,NativePage(this,weatherBoard,weatherArt),NativePage(this,obsBoard,observationArt))
        pager.onPageChanged={redraw()};pager.onTransition={transition->pageTransition=transition;artLifecycle()}
        root.addView(pager,FrameLayout.LayoutParams(-1,-1))
        settings=SettingsPanel(this);root.addView(settings,FrameLayout.LayoutParams(-1,-1));setContentView(root)
        immersive()
        if(Build.VERSION.SDK_INT>=33)onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT){back()}
        model.weather=repository.cached(model.place)
        redraw()
    }
    private fun immersive(){WindowInsetsControllerCompat(window,window.decorView).apply{
        isAppearanceLightStatusBars=true;isAppearanceLightNavigationBars=true
        systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if(model.page==0)hide(WindowInsetsCompat.Type.systemBars())
        else{show(WindowInsetsCompat.Type.statusBars());hide(WindowInsetsCompat.Type.navigationBars())}
    }}
    override fun onWindowFocusChanged(hasFocus:Boolean){super.onWindowFocusChanged(hasFocus);if(hasFocus)immersive()}
    override fun onResume(){super.onResume();visible=true;immersive();refresh();if(devicePrefs.getBoolean("location_follow",false))startLocation();artLifecycle();main.post(ticker)
        if(updates.installing){updates.installing=false;settings.installPermissionReturned()}}
    override fun onPause(){visible=false;touchInProgress=false;main.removeCallbacksAndMessages(null);repository.cancelWeather();placeSearch?.cancel();placeDialog?.cancelSearch();feedback.cancel();settings.cancelRequests();stopLocation();artLifecycle();super.onPause()}
    override fun onDestroy(){placeSearch?.cancel();placeDialog?.dismissFromHost();settings.dispose();weatherArt.dispose();observationArt.dispose();repository.close();super.onDestroy()}
    override fun onSaveInstanceState(out:Bundle){out.putDouble("weather_elapsed",weatherArt.elapsed);out.putDouble("observation_elapsed",observationArt.elapsed);super.onSaveInstanceState(out)}
    private val ticker=object:Runnable{override fun run(){
        if(!visible)return
        val wx=model.weather;val before=wx?.let{model.window(it)}
        model.now=System.currentTimeMillis()/1000
        if(wx!=null&&model.window(wx)!=before)redraw()else if(model.page==1&&!pager.overlayOpen)pager.observationPage.dashboard.invalidate()
        if((wx==null||wx.stale(model.now))&&model.status!="loading"&&model.now-lastWeatherAttempt>=MIN_READ_SECONDS)refresh()
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
        pager.syncPage();immersive();root.setBackgroundColor(PAPER);pager.weatherPage.updateState();pager.observationPage.updateState()
        val enabled=model.motion&&android.animation.ValueAnimator.areAnimatorsEnabled()
        weatherArt.updateScene(model.weather,null,model.block,enabled)
        observationArt.updateScene(model.weather,model.weather?.let{model.window(it).start},model.block,enabled)
        val key="${model.lang}:${model.purpose}"
        if(key!=layoutKey){layoutKey=key;pager.weatherPage.requestLayout();pager.observationPage.requestLayout()}
        pager.weatherPage.dashboard.invalidate();pager.observationPage.dashboard.invalidate();artLifecycle()
    }
    private fun artLifecycle(){
        val transition=pageTransition
        val pause=transition
        weatherArt.pauseForInteraction(pause);observationArt.pauseForInteraction(pause)
        val exposed=visible
        weatherArt.active=exposed&&model.weather!=null&&(model.page==0||transition)
        observationArt.active=exposed&&model.weather?.let{it.scene(model.window(it).start)!="unknown"}==true&&(model.page==1||transition)
    }
    override fun dispatchTouchEvent(event:MotionEvent):Boolean{
        if(this::pager.isInitialized){when(event.actionMasked){
            MotionEvent.ACTION_DOWN->{main.removeCallbacks(resumeArtwork);touchInProgress=true;artLifecycle()}
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{main.removeCallbacks(resumeArtwork);main.postDelayed(resumeArtwork,240)}
        }}
        return super.dispatchTouchEvent(event)
    }
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
        val fine=checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
        val providers=listOf("fused",LocationManager.NETWORK_PROVIDER,LocationManager.GPS_PROVIDER).filter{provider->
            (provider!=LocationManager.GPS_PROVIDER||fine||Build.VERSION.SDK_INT>=31)&&runCatching{locationManager.isProviderEnabled(provider)}.getOrDefault(false)
        }
        if(providers.isEmpty()){model.locationStatus="unavailable";settings.refreshLocation();return}
        val gen=++locationGeneration;locating=true;model.locationStatus="waiting"
        // Reuse only recent system fixes. A granted permission is not itself a fix.
        val cached=(providers+LocationManager.PASSIVE_PROVIDER).mapNotNull{provider->runCatching{locationManager.getLastKnownLocation(provider)}.getOrNull()}.filter(::usableLocation).maxByOrNull{it.elapsedRealtimeNanos}
        if(cached!=null){acceptLocation(cached);return}
        for(provider in providers){
            val request=CancellationSignal();locationRequests.add(request)
            try{LocationManagerCompat.getCurrentLocation(locationManager,provider,request,mainExecutor){fix->
                if(gen==locationGeneration&&fix!=null)acceptLocation(fix)
            }}catch(_:SecurityException){request.cancel()}catch(_:IllegalArgumentException){request.cancel()}
        }
        locationTimeout=Runnable{if(locating&&gen==locationGeneration){stopLocation();model.locationStatus="timeout";settings.refreshLocation()}}
        main.postDelayed(locationTimeout!!,30000)
        settings.refreshLocation()
    }
    private fun stopLocation(){locationGeneration++;locating=false;locationTimeout?.let(main::removeCallbacks);locationTimeout=null;locationRequests.forEach{it.cancel()};locationRequests.clear()}
    fun retryLocation(){setLocation(true)}
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==901){if(grantResults.any{it==PackageManager.PERMISSION_GRANTED})startLocation()else{devicePrefs.edit().putBoolean("location_follow",false).apply();model.locationStatus="denied";settings.refreshLocation()}}}
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Deprecated("API 29–32 fallback") override fun onBackPressed(){back()}
    private fun back(){if(settings.visibility==View.VISIBLE)settings.back()else finish()}
    fun showPlaces(){
        if(placeDialog?.isShowing==true)return
        placeDialog=PlaceDialog(this).also{it.show()}
    }
    fun external(url:String){val u=android.net.Uri.parse(url);if(u.scheme!="https")return;runCatching{startActivity(Intent(Intent.ACTION_VIEW,u))}}
    fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
}
