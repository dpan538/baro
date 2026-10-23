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
    var sx=1f;private set
    var sy=1f;private set
    var inset=0f;private set
    fun configure(widthDp:Float,heightDp:Float,topInsetDp:Float){sx=widthDp/referenceWidth;sy=(heightDp+topInsetDp)/referenceHeight;inset=topInsetDp}
    fun node(key:String,index:Int=0):JSONObject?=if(nodes.optJSONArray(key)!=null)nodes.getJSONArray(key).optJSONObject(index)else nodes.optJSONObject(key)
    fun rect(key:String,index:Int=0):RectF {
        val n=node(key,index)?:return RectF()
        val x=n.getDouble("x").toFloat()*sx;val y=n.getDouble("y").toFloat()*sy-inset
        return RectF(x,y,x+n.getDouble("w").toFloat()*sx,y+n.getDouble("h").toFloat()*sy)
    }
    fun size(key:String,index:Int=0)=node(key,index)?.optDouble("size",16.0)?.toFloat()?.times(sx)?:16f
    fun line(key:String,index:Int=0)=node(key,index)?.optDouble("line",20.0)?.toFloat()?.times(sx)?:20f
    fun spacing(key:String,index:Int=0)=node(key,index)?.optDouble("spacing",0.0)?.toFloat()?.times(sx)?:0f
    fun font(key:String,index:Int=0)=node(key,index)?.optString("font","")?:""
    fun weight(key:String,index:Int=0)=node(key,index)?.optInt("weight",400)?:400
}
