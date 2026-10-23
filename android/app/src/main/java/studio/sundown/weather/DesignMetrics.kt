package studio.sundown.weather

import android.content.Context
import android.graphics.*
import org.json.JSONObject

/** Measured CSS reference coordinates are a design system, never device pixels as dp. */
class DesignMetrics(context:Context,path:String){
    private val json=JSONObject(context.assets.open("layouts/$path.json").bufferedReader().use{it.readText()})
    private val nodes=json.getJSONObject("nodes")
    private val referenceWidth=json.getDouble("width").toFloat()
    private val referenceHeight=json.getDouble("height").toFloat()
    private var readableFallback=false
    var sx=1f;private set
    var sy=1f;private set
    var inset=0f;private set
    fun configure(widthDp:Float,heightDp:Float,topInsetDp:Float){sx=widthDp/referenceWidth;sy=(heightDp+topInsetDp)/referenceHeight;inset=topInsetDp;readableFallback=referenceHeight>referenceWidth&&widthDp<referenceWidth&&heightDp>1550}
    fun node(key:String,index:Int=0):JSONObject?=if(nodes.optJSONArray(key)!=null)nodes.getJSONArray(key).optJSONObject(index)else nodes.optJSONObject(key)
    fun rect(key:String,index:Int=0):RectF {
        val n=node(key,index)?:return RectF()
        val x=n.getDouble("x").toFloat()*sx;val y=n.getDouble("y").toFloat()*sy-inset
        return RectF(x,y,x+n.getDouble("w").toFloat()*sx,y+n.getDouble("h").toFloat()*sy)
    }
    fun outline(key:String,bounds:RectF=rect(key)):Path{
        val a=node(key)?.optJSONArray("radii")
        val limit=minOf(bounds.width(),bounds.height())/2
        val radii=FloatArray(8){i->((a?.optDouble(i/2,0.0)?:0.0).toFloat()*sx).coerceIn(0f,maxOf(0f,limit))}
        return Path().apply{addRoundRect(bounds,radii,Path.Direction.CW)}
    }
    fun size(key:String,index:Int=0):Float {
        val scaled=node(key,index)?.optDouble("size",16.0)?.toFloat()?.times(sx)?:16f
        return if(readableFallback)maxOf(if(key.endsWith(":heading"))24f else if(key.endsWith(":reading"))27f else 16f,scaled)else scaled
    }
    fun line(key:String,index:Int=0):Float {
        val scaled=node(key,index)?.optDouble("line",20.0)?.toFloat()?.times(sx)?:20f
        return if(readableFallback)maxOf(scaled,size(key,index)*1.1f)else scaled
    }
    fun spacing(key:String,index:Int=0)=node(key,index)?.optDouble("spacing",0.0)?.toFloat()?.times(sx)?:0f
    fun font(key:String,index:Int=0)=node(key,index)?.optString("font","")?:""
    fun weight(key:String,index:Int=0)=node(key,index)?.optInt("weight",400)?:400
}
