package studio.sundown.weather

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30 as GL
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.TextureView
import kotlin.math.*

/** Only authored GPU artwork is reused. No WebView or JS is embedded. */
class PixelArt(context: Context, private val instrument: Boolean): TextureView(context), TextureView.SurfaceTextureListener {
    private val thread=HandlerThread("BARO-art").apply { start() }
    private val worker=Handler(thread.looper)
    private var display: EGLDisplay=EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext=EGL14.EGL_NO_CONTEXT
    private var surface: EGLSurface=EGL14.EGL_NO_SURFACE
    private var program=0;private var copy=0;private var texture=0;private var framebuffer=0
    private var gw=0;private var gh=0;private var sw=1;private var sh=1
    @Volatile var active=false;set(value){field=value;worker.removeCallbacks(frame);worker.post(frame)}
    @Volatile var motion=true
    @Volatile var block=false
    @Volatile var weather: Weather?=null
    @Volatile var sampleTime:Long?=null
    @Volatile var error: String?=null;private set
    @Volatile var renderedFrames=0L;private set
    @Volatile var elapsed=0.0
    private var last=0L
    private val scenes=listOf("clear","partly","overcast","rain","storm","snow","fog","wind","night","sunset","dawn","unknown")
    private val instruments=listOf("partly","rain","storm","snow","night","sunset","dawn","clear","overcast","fog","wind")
    init{surfaceTextureListener=this;isOpaque=true;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO}
    private fun shader(type: Int, path: String): Int {
        val code=context.assets.open("shaders/$path").bufferedReader().use { it.readText() }
        val id=GL.glCreateShader(type);GL.glShaderSource(id,code);GL.glCompileShader(id)
        val ok=IntArray(1);GL.glGetShaderiv(id,GL.GL_COMPILE_STATUS,ok,0);check(ok[0]!=0){GL.glGetShaderInfoLog(id)};return id
    }
    private fun link(v:String,f:String):Int {
        val vertex=shader(GL.GL_VERTEX_SHADER,v);val fragment=shader(GL.GL_FRAGMENT_SHADER,f)
        val p=GL.glCreateProgram();GL.glAttachShader(p,vertex);GL.glAttachShader(p,fragment);GL.glLinkProgram(p)
        GL.glDeleteShader(vertex);GL.glDeleteShader(fragment)
        val ok=IntArray(1);GL.glGetProgramiv(p,GL.GL_LINK_STATUS,ok,0);check(ok[0]!=0){GL.glGetProgramInfoLog(p)};return p
    }
    private fun initEgl(st:SurfaceTexture){
        display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);check(EGL14.eglInitialize(display,IntArray(2),0,IntArray(2),0))
        val configs=arrayOfNulls<EGLConfig>(1);val n=IntArray(1)
        check(EGL14.eglChooseConfig(display,intArrayOf(EGL14.EGL_RENDERABLE_TYPE,0x40,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT,EGL14.EGL_NONE),0,configs,0,1,n,0)&&n[0]>0)
        eglContext=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,3,EGL14.EGL_NONE),0)
        surface=EGL14.eglCreateWindowSurface(display,configs[0],st,intArrayOf(EGL14.EGL_NONE),0);check(EGL14.eglMakeCurrent(display,surface,surface,eglContext))
        program=link(if(instrument)"instrument.vert" else "weather.vert",if(instrument)"instrument.frag" else "weather.frag")
        copy=link("weather.vert","copy.frag")
        val ids=IntArray(1);GL.glGenTextures(1,ids,0);texture=ids[0];GL.glGenFramebuffers(1,ids,0);framebuffer=ids[0]
        GL.glGenVertexArrays(1,ids,0);GL.glBindVertexArray(ids[0]);gw=0;gh=0;last=0
    }
    private fun f(name:String,value:Double){GL.glUniform1f(GL.glGetUniformLocation(program,name),value.toFloat())}
    private fun i(name:String,value:Int){GL.glUniform1i(GL.glGetUniformLocation(program,name),value)}
    private fun vec(name:String,x:Int,y:Int){GL.glUniform2f(GL.glGetUniformLocation(program,name),x.toFloat(),y.toFloat())}
    private fun draw(){
        val now=SystemClock.elapsedRealtime()
        if(motion&&last!=0L)elapsed+=(now-last).coerceAtLeast(0)/1000.0
        last=now
        val limit=IntArray(1);GL.glGetIntegerv(GL.GL_MAX_TEXTURE_SIZE,limit,0)
        var w=if(block)if(instrument)192 else 96 else min(1536,sw*(if(instrument)2 else 1))
        w=min(w,limit[0]);var h=max(1,(w.toDouble()*sh/sw).roundToInt());if(h>limit[0]){h=limit[0];w=max(1,(h.toDouble()*sw/sh).roundToInt())}
        GL.glBindTexture(GL.GL_TEXTURE_2D,texture)
        val filter=if(block)GL.GL_NEAREST else GL.GL_LINEAR
        GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MIN_FILTER,filter);GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MAG_FILTER,filter)
        GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_WRAP_S,GL.GL_CLAMP_TO_EDGE);GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_WRAP_T,GL.GL_CLAMP_TO_EDGE)
        if(w!=gw||h!=gh){gw=w;gh=h;GL.glTexImage2D(GL.GL_TEXTURE_2D,0,GL.GL_RGBA,w,h,0,GL.GL_RGBA,GL.GL_UNSIGNED_BYTE,null)}
        GL.glBindFramebuffer(GL.GL_FRAMEBUFFER,framebuffer);GL.glFramebufferTexture2D(GL.GL_FRAMEBUFFER,GL.GL_COLOR_ATTACHMENT0,GL.GL_TEXTURE_2D,texture,0)
        check(GL.glCheckFramebufferStatus(GL.GL_FRAMEBUFFER)==GL.GL_FRAMEBUFFER_COMPLETE)
        GL.glViewport(0,0,w,h);GL.glUseProgram(program)
        val wx=weather;val at=if(instrument)sampleTime?:wx?.validAt?:0L else wx?.validAt?:0L;val scene=wx?.scene(at)?:"unknown";val time=if(motion)elapsed else 0.0
        i("coarse",if(block)1 else 0)
        if(instrument){
            vec("screen",w,h);vec("grid",w,h);f("clock",time)
            val low=wx?.value("cloud_cover_low",at);val mid=wx?.value("cloud_cover_mid",at);val high=wx?.value("cloud_cover_high",at)
            f("cloud",listOfNotNull(low,mid,high).maxOrNull()?.div(100)?:0.0)
            f("rain",if(scene in listOf("rain","storm"))(wx?.value("precipitation",at)?:0.0).div(3).coerceIn(0.0,1.0) else 0.0)
            f("fog",if(scene=="fog").92 else max(0.0,1-(wx?.value("visibility",at)?:15000.0)/15000)*.5)
            val sun=wx?.let { Astronomy.sun(at,it.place) };f("elevation",sun?.elevation?:0.0);f("bearing",sun?.azimuth?:0.0);f("moonPhase",wx?.let { Astronomy.moon(at).fraction }?:0.0)
            i("scene",instruments.indexOf(scene).coerceAtLeast(0))
        }else{vec("resolution",w,h);f("loopTime",time%45);i("scene",scenes.indexOf(scene).coerceAtLeast(0));i("night",if(wx?.value("is_day")==0.0&&scene !in listOf("dawn","sunset","fog"))1 else 0)}
        GL.glDrawArrays(GL.GL_TRIANGLES,0,3)
        GL.glBindFramebuffer(GL.GL_FRAMEBUFFER,0);GL.glViewport(0,0,sw,sh);GL.glUseProgram(copy)
        GL.glActiveTexture(GL.GL_TEXTURE0);GL.glBindTexture(GL.GL_TEXTURE_2D,texture);GL.glUniform1i(GL.glGetUniformLocation(copy,"image"),0);GL.glDrawArrays(GL.GL_TRIANGLES,0,3)
        check(EGL14.eglSwapBuffers(display,surface));renderedFrames++
    }
    private val frame=object:Runnable{override fun run(){
        if(!active||surface==EGL14.EGL_NO_SURFACE){last=0;return}
        try{draw()}catch(e:Exception){error=e.message;active=false;return}
        if(motion)worker.postDelayed(this,if(instrument)83 else 42)
    }}
    fun refresh(){worker.removeCallbacks(frame);worker.post(frame)}
    override fun onSurfaceTextureAvailable(st:SurfaceTexture,w:Int,h:Int){sw=w;sh=h;worker.post{try{initEgl(st);frame.run()}catch(e:Exception){error=e.message}}}
    override fun onSurfaceTextureSizeChanged(st:SurfaceTexture,w:Int,h:Int){worker.post{sw=w;sh=h;refresh()}}
    override fun onSurfaceTextureUpdated(st:SurfaceTexture){}
    override fun onSurfaceTextureDestroyed(st:SurfaceTexture):Boolean{worker.removeCallbacksAndMessages(null);worker.post{releaseEgl();st.release()};return false}
    private fun releaseEgl(){if(display!=EGL14.EGL_NO_DISPLAY){EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);EGL14.eglDestroySurface(display,surface);EGL14.eglDestroyContext(display,eglContext);EGL14.eglTerminate(display)};surface=EGL14.EGL_NO_SURFACE;display=EGL14.EGL_NO_DISPLAY}
    fun dispose(){active=false;worker.post{releaseEgl();thread.quitSafely()}}
}
