package studio.sundown.weather

import java.time.Instant

/** Short, ranked explanations of the selected model window, never site observations. */
data class ConditionsAdvice(val text:String,val reasons:List<String>)

fun conditionsAdvice(w:Weather?,purpose:Int,at:Long,language:String,windowAvailable:Boolean=true):ConditionsAdvice {
    fun tr(zh:String,en:String)=if(language=="zh")zh else en
    fun result(id:String,zh:String,en:String)=ConditionsAdvice(tr(zh,en),listOf(id))
    if(w==null)return result("unavailable","天气数据暂不可用，恢复联网后会自动更新。","Weather is unavailable; it will update when connectivity returns.")
    if(!windowAvailable&&purpose in 4..6)return result("no_window","近期没有对应的光照窗口，可换一个拍摄时段。","No matching light window was found; choose another photographic period.")
    fun v(key:String)=w.value(key,at)
    val p=purpose.coerceIn(0,11)
    val t=v("temperature_2m");val rh=v("relative_humidity_2m")
    val td=v("dew_point_2m")?:Physics.dew(t,rh)
    val spread=if(t!=null&&td!=null)t-td else null
    val speed=v("wind_speed_10m");val gust=v("wind_gusts_10m")
    val soil=v("soil_moisture_0_to_1cm");val surface=v("soil_temperature_0cm")
    val cloud=v("cloud_cover");val low=v("cloud_cover_low");val vis=v("visibility")
    val code=w.value("weather_code",w.atmosphereTime(at))?.toInt()
    val wetCode=code in setOf(51,53,55,56,57,61,63,65,66,67,80,81,82,95,96,99)
    val snow=code in setOf(71,73,75,77,85,86)
    // Hourly precipitation is a preceding-hour total, not a live rain sensor.
    val chance=w.precip(at)?.second
    val elevation=Astronomy.sun(at,w.place).elevation
    val hour=Instant.ofEpochSecond(at).atZone(w.zone).hour
    val morning=hour in 3..9
    val nightOrMorning=elevation<=0||(morning&&elevation<20)
    val nearDew=spread!=null&&spread in 0.0..2.0
    val dewWeather=nearDew&&nightOrMorning&&speed!=null&&speed<=10&&cloud!=null&&cloud<=50&&!wetCode&&!snow
    // These are editorial triggers, not soil-type saturation or measured grass wetness.
    val wetSoil=soil!=null&&soil>=.35
    val frost=dewWeather&&((surface!=null&&surface<=0)||(t!=null&&t<=2))
    data class Candidate(val id:String,val priority:Int,val copy:Pair<String,String>)
    val choices=mutableListOf<Candidate>()
    fun offer(id:String,priority:Int,copy:Pair<String,String>?){if(copy!=null)choices.add(Candidate(id,priority,copy))}

    if(code in setOf(95,96,99))offer("thunder",120,when(p){
        2->"雷暴时段不宜骑行，先到室内等候。" to "Thunderstorms: pause the ride and wait indoors."
        4,5,6->"雷暴时段先停止露天拍摄，到室内等候。" to "Thunderstorms: pause exposed photography and wait indoors."
        7,8->"雷暴时段先停止露天观测，到室内等候。" to "Thunderstorms: pause exposed observing and wait indoors."
        9->"雷暴时段不要把帐篷当作避雷处，转入室内。" to "Thunderstorms: a tent is not lightning shelter; move indoors."
        10->"雷暴时段先收回阳台衣物，关好迎风侧窗。" to "Thunderstorms: bring balcony laundry in and close exposed windows."
        11->"雷暴时段先把露天器材移入干燥室内。" to "Thunderstorms: move exposed equipment into a dry room."
        else->"雷暴时段减少露天停留，先到室内等候。" to "Thunderstorms: reduce exposed time and wait indoors."
    })
    if(code in setOf(56,57,66,67))offer("freezing_rain",115,when(p){
        2->"冻雨可能使路面结冰，先暂停骑行。" to "Freezing rain may ice the road; pause cycling."
        in 4..8->"冻雨可能使地面和器材结冰，先暂停露天架设。" to "Freezing rain may ice ground and gear; pause outdoor setup."
        10,11->"冻雨时收回露天衣物或器材，检查表面水分。" to "Bring exposed items in during freezing rain; check surface moisture."
        else->"冻雨可能使路面结冰，出行前先检查落脚处。" to "Freezing rain may ice surfaces; check your footing before setting out."
    })
    else if(snow)offer("snow",100,when(p){
        2->"降雪会影响抓地与视线，先检查骑行路况。" to "Snow affects grip and sightlines; check the cycling route."
        in 4..8->"降雪会遮住远景，镜头和脚架需防湿。" to "Snow obscures distant detail; keep lenses and tripod dry."
        9->"降雪时先检查营地积雪与排水，保持睡具干燥。" to "Check campsite snow and drainage; keep bedding dry."
        10,11->"雪水带入室内前先擦干，潮湿物品不要直接收纳。" to "Wipe off snowmelt before bringing items in; dry them before storage."
        else->"降雪会影响视线与路面，步行避开未清理路段。" to "Snow affects visibility and surfaces; avoid uncleared paths."
    })
    else if(wetCode)offer("rain",95,when(p){
        0->"该时段有降雨，通勤备雨具并给换乘留余量。" to "Rain in this window: carry rainwear and allow time for transfers."
        1->"该时段有降雨，步行选便于避雨和折返的路线。" to "Rain in this window: walk a route with shelter and an easy return."
        2->"该时段有降雨，骑行留出制动距离并保护随身物。" to "Rain in this window: allow braking space and cover belongings."
        3->"该时段有降雨，停留点选有顶棚且座位干燥处。" to "Rain in this window: choose covered seating and check it is dry."
        4->"降雨会遮挡晚霞，先选有遮雨条件的西向机位。" to "Rain can obscure sunset colour; choose a sheltered west-facing spot."
        5->"降雨会影响晨光拍摄，先保护器材并确认东侧视野。" to "Rain affects dawn shooting; protect gear and check the eastern view."
        6->"雨中蓝调拍摄先给器材遮雨，再找稳定支撑。" to "For rainy blue hour, cover equipment before setting up a stable support."
        7->"降雨会挡住星空，先收起露天望远镜。" to "Rain obscures the stars; bring exposed optics under cover."
        8->"降雨削弱远景层次，可先选择较近的观察目标。" to "Rain reduces distant contrast; start with nearer viewing targets."
        9->"降雨时先检查营地排水，帐底和睡具要隔湿。" to "In rain, check campsite drainage and isolate bedding from wet ground."
        10->"该时段有降雨，先收回露天晾晒衣物。" to "Rain in this window: bring outdoor laundry under cover."
        else->"器材或织物淋雨后先晾干，再放回密闭收纳处。" to "Dry rain-wet equipment or fabrics before putting them in closed storage."
    })
    else if(chance!=null&&chance>=60)offer("rain_chance",85,when(p){
        2->"降水机会较高，骑行备防水外层并预留避雨点。" to "Rain is possible: carry a waterproof layer and plan sheltered stops."
        in 4..8->"降水机会较高，露天架设前先准备器材遮雨。" to "Rain is possible: prepare an equipment cover before outdoor setup."
        9->"降水机会较高，扎营前先确认排水与撤收路线。" to "Rain is possible: check drainage and a route out before pitching."
        10->"降水机会较高，外出前把露天衣物移到遮雨处。" to "Rain is possible: move exposed laundry under cover before leaving."
        11->"降水机会较高，外带器材先加防水包。" to "Rain is possible: use a waterproof bag for equipment taken outside."
        else->"降水机会较高，带上雨具并安排可避雨的路线。" to "Rain is possible: carry rainwear and choose a route with shelter."
    })
    if(gust!=null&&gust>=35)offer("gusts",90,when(p){
        0,1->"阵风较强，经过开阔路段留意雨伞和随身物。" to "Strong gusts: secure umbrellas and loose items on exposed paths."
        2->"阵风较强，桥面和开阔路段需留意侧风。" to "Strong gusts: watch for crosswinds on bridges and exposed sections."
        3->"阵风较强，停留时避开松动物件和无遮蔽处。" to "Strong gusts: avoid loose objects and exposed resting spots."
        in 4..6->"阵风可能晃动脚架，先降低支撑高度。" to "Gusts may shake the tripod; lower its height before shooting."
        7->"阵风会晃动望远镜，先缩短支架并找有遮挡处。" to "Gusts shake optics; lower the mount and seek a sheltered position."
        8->"阵风可能晃动长焦和望远镜，使用稳定支撑。" to "Gusts can shake long lenses and binoculars; use stable support."
        9->"阵风较强，搭建前检查营地遮挡和固定条件。" to "Strong gusts: check campsite shelter and anchor points before setup."
        10->"阵风较强，固定晾衣架并收好阳台轻物。" to "Strong gusts: secure the drying rack and loose balcony items."
        else->"阵风较强，收起露天轻物并扣好器材箱。" to "Strong gusts: bring light items in and latch equipment cases."
    })
    if(p<10&&(code in setOf(45,48)||(vis!=null&&vis<3000)))offer("visibility",88,when(p){
        2->"能见度偏低，骑行先确认照明并降低速度。" to "Low visibility: check bicycle lights and reduce speed."
        in 4..8->"能见度偏低，远景细节受限，可选近处主体。" to "Low visibility limits distant detail; choose a nearer subject."
        9->"能见度偏低，扎营和返程前先确认路线标记。" to "Low visibility: confirm route markers before camping or returning."
        else->"能见度偏低，步行和通勤优先选择熟悉路线。" to "Low visibility: use familiar routes for walking and commuting."
    })
    if(wetSoil&&p<10)offer("wet_soil",72,when(p){
        2->"表层土壤可能偏湿，未铺装路段可能泥泞。" to "Model soil moisture is high; unpaved cycling sections may be muddy."
        3->"表层土壤可能偏湿，不宜直接坐草地或地上，先垫坐垫。" to "Model soil moisture is high; avoid sitting directly on grass or soil; use a mat."
        9->"表层土壤可能偏湿，先检查营地，帐底加防潮垫。" to "Model soil moisture is high; inspect the pitch and use a groundsheet."
        4->"表层土壤可能偏湿，晚霞机位先选硬地，器材包垫高。" to "Model soil moisture is high; use firm ground for sunset and raise equipment bags."
        5->"表层土壤可能偏湿，晨拍穿草地前先检查鞋面防水。" to "Model soil moisture is high; check shoe protection before crossing grass for dawn."
        6->"表层土壤可能偏湿，蓝调低光下先确认脚架落点。" to "Model soil moisture is high; check tripod footing before blue-hour light fades."
        7->"表层土壤可能偏湿，望远镜底座与电源要隔地防潮。" to "Model soil moisture is high; keep telescope bases and power supplies off damp ground."
        8->"表层土壤可能偏湿，远眺先选硬地站位，避免踩入湿草。" to "Model soil moisture is high; choose firm ground for viewing and avoid wet grass."
        0->"表层土壤可能偏湿，通勤抄近路穿草地前先留意鞋面防水。" to "Model soil moisture is high; check shoe protection before taking a grassy commute shortcut."
        else->"表层土壤可能偏湿，草地可能沾湿鞋面，可优先走铺装路。" to "Model soil moisture is high; grass may wet shoes; prefer paved paths."
    })
    if(frost&&p<10)offer("frost",82,when(p){
        2->"近地冷湿且风弱，局地可能结霜，先检查路面。" to "Cold, humid, calm conditions may favour frost; check road surfaces."
        in 4..8->"近地冷湿且风弱，器材表面可能结霜，先做防护。" to "Cold, humid, calm conditions may favour frost on exposed equipment."
        else->"近地冷湿且风弱，草地可能结霜，先检查落脚处。" to "Cold, humid, calm conditions may favour grass frost; check footing."
    })
    else if(dewWeather&&p<10)offer("dew",70,when(p){
        2->"近露点且风弱，停放的车座可能有露水，骑前先擦干。" to "Near the dew point with light wind: check the saddle for dew before riding."
        3->"近露点且风弱，草地和座位可能结露，落座前先检查。" to "Near the dew point with light wind: check grass and seats for dew."
        4->"近露点且风弱，晚霞拍摄延长至夜间需留意镜片结露。" to "Near the dew point with light wind: watch for lens dew after sunset."
        5->"清晨近露点且风弱，草叶可能有露水，先保护鞋袜与镜头。" to "A calm dawn near the dew point may leave dew; protect shoes and lenses."
        6->"近露点且风弱，蓝调拍摄先装镜头罩并检查镜片。" to "Near the dew point with light wind: fit a lens hood and check glass for blue hour."
        7->"近露点且风弱，长时间观星先给镜片做好防露。" to "Near the dew point with light wind: protect optics from dew during long observing."
        8->"近露点且风弱，望远镜可能结露，远眺前先检查镜面。" to "Near the dew point with light wind: check binocular glass for dew before viewing."
        9->"近露点且风弱，外帐可能挂露，收帐前先晾干。" to "Near the dew point with light wind: air a dewy flysheet before packing."
        else->if(morning)"清晨近露点且风弱，草叶可能有露水，留意弄湿鞋袜。" to "A calm morning near the dew point may leave dew on grass; protect shoes and socks."
            else "夜间近露点且风弱，草叶可能结露，留意弄湿鞋面。" to "A calm night near the dew point may leave dew on grass; keep shoes dry."
    })
    if(p in 4..8&&(low?.let{it>=65}==true||cloud?.let{it>=85}==true))offer("cloud",74,when(p){
        4->"云层较厚，西侧霞光可能受挡，先确认地平线云隙。" to "Thick cloud may block sunset colour; check gaps along the western horizon."
        5->"云层较厚，晨光可能受挡，先确认东侧云隙。" to "Thick cloud may block dawn light; check gaps toward the east."
        6->"云层较厚，蓝调层次可能较平，可寻找地面灯光。" to "Thick cloud may flatten blue-hour colour; include ground lights."
        7->"云层较厚，可见星区有限，先确认是否有可用云隙。" to "Thick cloud limits star fields; check for usable gaps before setup."
        else->"云层较厚，远山轮廓可能受遮挡，先选较低目标。" to "Thick cloud may hide distant peaks; start with lower targets."
    })
    if(p==7&&elevation< -6&&Astronomy.moon(at).illumination>=.6&&Astronomy.moonAltitude(at,w.place)>0)
        offer("moonlight",60,"月面较亮且已升起，暗弱星体对比可能降低，可转向亮目标。" to "A bright Moon is above the horizon; faint targets may lose contrast; try brighter ones.")
    if(p<4||p==9){
        if(t!=null&&t>=28&&rh!=null&&rh>=75)offer("humid_heat",66,when(p){
            2->"高温高湿，骑行减少连续暴晒，安排阴凉停靠。" to "Warm, humid air: break up exposed riding with shaded stops."
            3->"高温高湿，长时间停留优先选遮阴且通风的位置。" to "Warm, humid air: choose a shaded, ventilated resting spot."
            9->"高温高湿，营地优先考虑遮阴与帐内通风。" to "Warm, humid air: prioritise shade and tent ventilation."
            else->"高温高湿，步行或通勤可选有遮阴、可休息的路线。" to "Warm, humid air: choose walking or commute routes with shade and rest stops."
        })
        else if(t!=null&&t<=8)offer("cold",62,if(p==9)"气温偏低，露营先备好隔地保温层。" to "Cool air: prepare insulation between your bedding and the ground."
            else "气温偏低，长时间停留备一层可穿脱的外衣。" to "Cool air: carry an extra removable layer for longer stops.")
    }
    if(p in listOf(7,9)){val end=w.hourly.at("temperature_2m",at+7200)
        if(t!=null&&end!=null&&end<=t-3)offer("cooling",68,if(p==9)"后续两小时降温较明显，睡具和衣物先放在随手可取处。" to "The next two hours cool notably; keep extra clothing and bedding within reach."
            else "后续两小时降温较明显，观星久坐前备好外层衣物。" to "The next two hours cool notably; bring an extra layer for seated observing.")
    }
    if(p==10){
        if(rh!=null&&rh>=80)offer("slow_drying",76,"室外湿度偏高，衣物可能干得慢，晾晒时拉开间距。" to "Humid outdoor air may slow drying; leave space between hanging clothes.")
        else if(Physics.vpd(t,rh)?.let{it>=1}==true&&!wetCode)offer("drying",55,"室外空气较干，衣物可拉开晾晒；仍需留意降水时段。" to "Outdoor air is drier; space out laundry and still check rain timing.")
    }
    if(p==11&&(rh?.let{it>=80}==true||spread?.let{it<=3}==true))offer("storage_moisture",76,"室外湿度偏高，冷器材带入暖房先装袋回温，晾干后收纳。" to "Humid outside: bag cold gear before entering a warm room; dry it before storage.")
    val light=radiationState(v("direct_radiation_instant"),v("diffuse_radiation_instant"),elevation)
    if(p!=7&&p!=11&&light.kind in setOf("direct","diffuse","mixed"))offer("light_quality",if(p in 4..6)58 else 48,when{
        p==10&&light.kind=="direct"->"直射占比较高，露天晾晒留意深浅衣物的日晒差异。" to "Direct light dominates; consider sun exposure when hanging light and dark fabrics."
        p==9&&light.kind=="direct"->"直射占比较高，搭营先留意遮阴随太阳移动。" to "Direct light dominates; check how campsite shade moves with the Sun."
        p==2&&light.kind=="direct"->"直射占比较高，骑行休息点可优先选遮阴处。" to "Direct light dominates; choose shaded stops when cycling."
        p<4&&light.kind=="direct"->"直射占比较高，步行或停留可优先选遮阴处。" to "Direct light dominates; prefer shaded walking routes and resting spots."
        light.kind=="direct"->"直射占比较高，主体明暗差更大，留意亮部曝光。" to "Direct light dominates; expect stronger contrast and check highlights."
        light.kind=="diffuse"->"漫射占比较高，光线较柔，主体阴影可能较浅。" to "Diffuse light dominates; expect softer light and shallower shadows."
        else->"直射与漫射并存，云隙变化可能改变主体反差。" to "Direct and diffuse light coexist; cloud gaps may change subject contrast."
    })
    val fallback=when(p){
        0->"通勤前确认换乘与遮雨点，路面积水需现场查看。" to "Check transfers and shelter before commuting; inspect route conditions locally."
        1->"步行选便于折返的路线，草地干湿需现场确认。" to "Choose a walk with an easy return; check grass wetness locally."
        2->"骑行前确认桥面和开阔路段，路面状况需现场判断。" to "Check bridges and exposed sections before riding; assess the road locally."
        3->"长时间停留先检查座位干湿，随身备一张坐垫。" to "Check seating before a long stop and carry a sitting mat."
        4->"晚霞拍摄先确认西侧地平线，地形遮挡未计入时段。" to "Check the western horizon for sunset; the light window excludes terrain obstruction."
        5->"晨光拍摄先确认东侧视野，留出布置器材的时间。" to "Check the eastern view for dawn and allow time to set up equipment."
        6->"蓝调拍摄光线会变暗，先摆稳脚架再调整曝光。" to "Blue-hour light fades; stabilise the tripod before adjusting exposure."
        7->"观星先避开路灯直射，现场光污染尚未测量。" to "Avoid direct streetlights when observing; local light pollution is unmeasured."
        8->"远眺先确认目标方向的遮挡，能见度是区域模型值。" to "Check obstructions toward your target; visibility is a regional model estimate."
        9->"扎营先检查地面排水与干湿，场地条件需现场确认。" to "Inspect pitch drainage and ground moisture before setting up camp."
        10->"开窗和晾晒前需另看室内温湿度，室外模型不能代替。" to "Check indoor temperature and humidity before airing; outdoor data cannot replace them."
        else->"收纳前确认物品已干燥，柜内湿度与器材温度未测量。" to "Check items are dry before storage; cabinet humidity and gear temperature are unmeasured."
    }
    val ranked=choices.sortedByDescending{it.priority}.distinctBy{it.id}
    if(ranked.isEmpty())return result("local_check",fallback.first,fallback.second)
    val selected=ranked.take(2).toMutableList()
    if(selected.size==1)selected.add(Candidate("local_check",0,fallback))
    val budget=if(language=="zh")84 else 180
    // Keep complete thoughts. Never truncate a caution or join half sentences.
    val included=mutableListOf<Candidate>();var text=""
    for(candidate in selected){val copy=tr(candidate.copy.first,candidate.copy.second);val combined=text+(if(language=="zh"||text.isEmpty())""else " ")+copy
        if(combined.length<=budget){text=combined;included.add(candidate)}
    }
    return ConditionsAdvice(text,included.map{it.id})
}

data class RadiationState(val kind:String,val direct:Double?,val diffuse:Double?,val diffuseShare:Double?)
fun radiationState(direct:Double?,diffuse:Double?,solarElevation:Double?):RadiationState {
    if(direct==null||diffuse==null||!direct.isFinite()||!diffuse.isFinite()||direct<0||diffuse<0)return RadiationState("missing",direct,diffuse,null)
    val total=direct+diffuse
    if(total<=0)return RadiationState(if(solarElevation!=null&&solarElevation<=0)"night"else "zero",direct,diffuse,null)
    val share=diffuse/total
    return RadiationState(when{share>=.7->"diffuse";share<=.3->"direct";else->"mixed"},direct,diffuse,share)
}

data class WhiteBalanceHint(val direction:String,val note:String)
fun whiteBalanceHint(w:Weather?,at:Long,language:String):WhiteBalanceHint {
    fun tr(zh:String,en:String)=if(language=="zh")zh else en
    if(w==null)return WhiteBalanceHint(tr("先用自动","Auto WB"),tr("用中性灰物体校准","Calibrate on neutral grey"))
    val elevation=Astronomy.sun(at,w.place).elevation
    val light=radiationState(w.value("direct_radiation_instant",at),w.value("diffuse_radiation_instant",at),elevation)
    return when{
        elevation< -6->WhiteBalanceHint(tr("先用自动","Auto WB"),tr("混合灯光先拍灰卡","Mixed light: use a grey card"))
        elevation<=0->WhiteBalanceHint(tr("可略加暖","Warmer WB"),tr("保留蓝调可少补偿","Less correction keeps blue tones"))
        light.kind=="missing"||light.kind=="zero"->WhiteBalanceHint(tr("先用自动","Auto WB"),tr("光线未知，用灰卡校准","Light unknown: use a grey card"))
        elevation<=10&&light.kind=="direct"->WhiteBalanceHint(tr("可略降温","Cooler WB"),tr("保留金色可少补偿","Less correction keeps golden tones"))
        light.kind=="diffuse"->WhiteBalanceHint(tr("可略加暖","Warmer WB"),tr("阴影若偏蓝再微调","Adjust only if shadows look blue"))
        else->WhiteBalanceHint(tr("先用日光","Daylight WB"),tr("以灰卡微调，不是实测","Fine-tune on grey; not measured"))
    }
}
