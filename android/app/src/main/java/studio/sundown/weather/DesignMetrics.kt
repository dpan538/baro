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
    private val adjusted=mutableMapOf<String,RectF>()
    var sx=1f;private set
    var sy=1f;private set
    var inset=0f;private set
    fun configure(widthDp:Float,heightDp:Float,topInsetDp:Float){
        sx=widthDp/referenceWidth;sy=(heightDp+topInsetDp)/referenceHeight;inset=topInsetDp
        adjusted.clear()
        // The full-height figure contract supersedes the former fixed card heights.
        // Keep the assembly edges and columns; redistribute only neighbouring cards.
        if(referenceWidth>referenceHeight&&nodes.has("ins-pressure")){
            val pressure=rect("ins-pressure");val dew=rect("ins-dew")
            val pressureHeight=(pressure.width()-24)/(2*.86f)
            if(pressureHeight<pressure.height()){
                val gap=dew.top-pressure.bottom
                replace("ins-pressure",RectF(pressure.left,pressure.top,pressure.right,pressure.top+pressureHeight))
                replace("ins-dew",RectF(dew.left,pressure.top+pressureHeight+gap,dew.right,dew.bottom))
            }
            val moon=rect("ins-lunar");val drift=rect("ins-drift")
            val moonHeight=minOf(moon.height(),moon.width()*.58f)
            if(moonHeight<moon.height()){
                val gap=drift.top-moon.bottom
                replace("ins-lunar",RectF(moon.left,moon.top,moon.right,moon.top+moonHeight))
                replace("ins-drift",RectF(drift.left,moon.top+moonHeight+gap,drift.right,drift.bottom))
            }
        }else if(nodes.has("ins-pressure")){
            val pressure=rect("ins-pressure");val dew=rect("ins-dew")
            val pressureHeight=(size("ins-pressure:heading")*.9f+size("ins-pressure:reading")*.9f+16f*.95f+18f)/.85f
            if(pressureHeight<pressure.height()){
                val gap=dew.top-pressure.bottom
                replace("ins-pressure",RectF(pressure.left,pressure.top,pressure.right,pressure.top+pressureHeight))
                replace("ins-dew",RectF(dew.left,pressure.top+pressureHeight+gap,dew.right,dew.bottom))
            }
        }
    }
    private fun replace(key:String,b:RectF){
        val original=rect(key)
        val children=nodes.keys().asSequence().filter{it.startsWith("$key:")}.toList()
        for(child in children){
            val r=rect(child);val scale=b.height()/original.height()
            adjusted[child]=RectF(r.left,b.top+(r.top-original.top)*scale,r.right,b.top+(r.bottom-original.top)*scale)
        }
        adjusted[key]=RectF(b)
    }
    fun node(key:String,index:Int=0):JSONObject?=if(nodes.optJSONArray(key)!=null)nodes.getJSONArray(key).optJSONObject(index)else nodes.optJSONObject(key)
    fun rect(key:String,index:Int=0):RectF {
        adjusted[key]?.let{return RectF(it)}
        val n=node(key,index)?:return RectF()
        val x=n.getDouble("x").toFloat()*sx;val y=n.getDouble("y").toFloat()*sy-inset
        return RectF(x,y,x+n.getDouble("w").toFloat()*sx,y+n.getDouble("h").toFloat()*sy)
    }
    fun outline(key:String,bounds:RectF=rect(key)):Path{
        val a=node(key)?.optJSONArray("radii")
        val limit=minOf(bounds.width(),bounds.height())/2
        // Small corners belong to each instrument tile; preserve the larger group-edge corners.
        val minimum=if(key.startsWith("ins-")&&!key.contains(':')&&key !in setOf("ins-context","ins-assembly","ins-wheel","ins-panorama"))8f else 0f
        val radii=FloatArray(8){i->maxOf(minimum,(a?.optDouble(i/2,0.0)?:0.0).toFloat()*sx).coerceIn(0f,maxOf(0f,limit))}
        return Path().apply{addRoundRect(bounds,radii,Path.Direction.CW)}
    }
    fun size(key:String,index:Int=0):Float {
        val scaled=node(key,index)?.optDouble("size",16.0)?.toFloat()?.times(sx)?:16f
        return scaled
    }
    fun line(key:String,index:Int=0):Float {
        val scaled=node(key,index)?.optDouble("line",20.0)?.toFloat()?.times(sx)?:20f
        return scaled
    }
    fun spacing(key:String,index:Int=0)=node(key,index)?.optDouble("spacing",0.0)?.toFloat()?.times(sx)?:0f
    fun font(key:String,index:Int=0)=node(key,index)?.optString("font","")?:""
    fun weight(key:String,index:Int=0)=node(key,index)?.optInt("weight",400)?:400
}
