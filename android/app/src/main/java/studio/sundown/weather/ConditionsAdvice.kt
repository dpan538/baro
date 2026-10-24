package studio.sundown.weather

import java.time.Instant

/** Activity decisions plus visibility, convection and light at the same selected model instant. */
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
        2->"雷暴时段不宜骑行，先到室内等候。" to "Thunderstorms: stop riding; wait indoors."
        4,5,6->"雷暴时段先停止露天拍摄，到室内等候。" to "Thunderstorms: stop outdoor photos; go indoors."
        7,8->"雷暴时段先停止露天观测，到室内等候。" to "Thunderstorms: stop observing; go indoors."
        9->"雷暴时段不要把帐篷当作避雷处，转入室内。" to "Thunderstorms: tents do not protect; go indoors."
        10->"雷暴时段先收回阳台衣物，关好迎风侧窗。" to "Thunderstorms: bring balcony laundry in and close exposed windows."
        11->"雷暴时段先把露天器材移入干燥室内。" to "Thunderstorms: move exposed equipment into a dry room."
        else->"雷暴时段减少露天停留，先到室内等候。" to "Thunderstorms: wait indoors."
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
    else if(wetCode&&code !in setOf(95,96,99))offer("rain",95,when(p){
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
    // Reading bands are editorial context, not road-safety limits or a diagnosis of fog.
    val visibility=if(p<10&&vis!=null&&vis.isFinite()&&vis>=0){
        val distance=Units.number(vis/1000,if(vis%1000==0.0)0 else 1)
        val level=when{vis<1000->"很低";vis<5000->"偏低";vis<10000->"远景稍受限";else->"较好"}
        val english=when{vis<1000->"very low";vis<5000->"reduced";vis<10000->"distant detail limited";else->"good"}
        val action=when{
            p==2&&vis<1000->"，先暂停骑行确认路况"
            p==2&&vis<5000->"，减速留意车辆"
            p==2&&vis<10000->"，仍需检查近处路况"
            p==2->"，仍查近处路况"
            p in 4..8&&vis<10000->"，优先选较近目标"
            p==9&&vis<5000->"，确认返程路标"
            vis<5000->"，留意路口与台阶"
            else->"，仍查局地遮挡"
        }
        val enAction=when{
            p==2&&vis<1000->"pause cycling"
            p==2&&vis<5000->"slow down and watch traffic"
            p==2->"still check nearby traffic"
            p in 4..8&&vis<10000->"choose nearer subjects"
            p==9&&vis<5000->"check return-route markers"
            vis<5000->"watch junctions and steps"
            else->"check local obstructions"
        }
        Candidate("visibility",if(vis<1000)105 else if(vis<5000)88 else 30,
            "能见度${distance}公里${level}${action}。" to "Visibility ${distance} km: $english; $enAction.")
    }else if(p<10&&code in setOf(45,48))Candidate("visibility",88,
        "有雾，能见度数值缺失，先确认路线视线。" to "Fog is reported but visibility is missing; check sightlines locally.")else null
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
    val cape=v("cape")?.takeIf{it.isFinite()&&it>=0}
    val cin=v("convective_inhibition")?.takeIf{it.isFinite()}
    val convection=if(p<10&&cape!=null)Candidate("convection",if(cape>=1000)84 else 25,when{
        code in setOf(95,96,99)&&cape<1000->"该时段有雷暴，低位能仍需避险。" to "Thunderstorms are reported; low CAPE does not remove the risk."
        code in setOf(95,96,99)->"雷暴可能伴强雨阵风，不能单看位能或抑制。" to "Storms may bring heavy rain/gusts despite CIN."
        cape>=1000&&cin!=null&&kotlin.math.abs(cin)>=100->
            "对流位能较高但有抑制，若触发雷暴或伴强雨阵风。" to "Higher CAPE with inhibition: triggered storms may bring heavy rain/gusts."
        cape>=1000->"对流位能较高，若触发雷暴或伴强雨阵风，留意预警。" to "Higher CAPE: storms, if triggered, may bring heavy rain/gusts."
        else->"对流位能低，仍留意雷暴预警。" to "Low CAPE does not rule out storms."
    })else null
    val light=radiationState(v("direct_radiation_instant"),v("diffuse_radiation_instant"),elevation)
    val hazardous=code in setOf(95,96,99,56,57,66,67)
    val lightCopy=if(hazardous)when(light.kind){
        "direct"->"直射主导，明暗反差较强。" to "Direct light dominates; contrast is stronger."
        "diffuse"->"漫射主导，光线较柔和。" to "Diffuse light dominates; light is softer."
        "mixed"->"直射、漫射混合，明暗可能变化。" to "Direct/diffuse mix; contrast may change."
        "night"->"夜间直射、漫射为零，月光灯光仍存在。" to "Night solar direct/diffuse: zero; lamps/Moon remain."
        "zero"->"日间直射、漫射为零，光线强弱待确认。" to "Daytime solar direct/diffuse: zero; light quality is unknown."
        else->"直射、漫射缺失，光线强弱待确认。" to "Solar direct/diffuse missing; light quality is unknown."
    }else when(light.kind){
        "direct"->when(p){
            2->"直射主导，留意迎光眩光并选遮阴停靠。" to "Direct light: watch glare; stop in shade."
            0,1,3->"直射主导，步行或停留优先选遮阴处。" to "Direct light dominates; seek shade."
            9->"直射主导，留意营地遮阴随太阳移动。" to "Direct light: shade moves around the camp."
            10->"直射主导，晾晒留意织物日晒差异。" to "Direct light dominates; consider fabric sun exposure when drying."
            11->"直射主导，器材与织物避免长时间暴晒。" to "Direct light dominates; avoid prolonged sun on stored gear or fabrics."
            else->"直射主导，反差较强，留意亮部曝光。" to "Direct light dominates; protect highlights."
        }
        "diffuse"->if(p in 0..3||p==9)"漫射主导，光线较柔，仍需保持自身可见。" to "Diffuse light is softer; remain visible."
            else "漫射主导，光线较柔，主体阴影较浅。" to "Diffuse light: soft light, faint shadows."
        "mixed"->"直射与漫射混合，云隙可能改变明暗反差。" to "Direct/diffuse mix; gaps may change contrast."
        "night"->when(p){
            0,1,2->"夜间直射、漫射为零，需主动照明。" to "Night solar direct/diffuse: zero; use lights."
            7->"夜间直射、漫射为零，观星仍需避开月光与灯光。" to "Night solar direct/diffuse: zero; lamps/Moon affect stars."
            else->"夜间直射、漫射为零，不代表没有月光或灯光。" to "Night solar direct/diffuse: zero; lamps/Moon remain."
        }
        "zero"->"日间直射、漫射为零，暂不据此判断光线强弱。" to "Daytime solar direct/diffuse: zero; quality unknown."
        else->"直射、漫射数据缺失，暂不判断光线强弱。" to "Direct and diffuse radiation are missing; light quality is unknown."
    }
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
    // Preserve two activity decisions, then explain the actual visible instruments.
    // Explicit coverage prevents rain/soil advice from hiding visibility and radiation.
    val selected=ranked.take(2).toMutableList()
    if(selected.isEmpty())selected.add(Candidate("local_check",0,fallback))
    visibility?.let{selected.add(it)};convection?.let{selected.add(it)}
    if(light.kind!="missing"||choices.isNotEmpty()||visibility!=null||convection!=null)
        selected.add(Candidate("light_quality",10,lightCopy))
    if(selected.size==1&&selected.first().id!="local_check")selected.add(Candidate("local_check",0,fallback))
    val budget=if(language=="zh")112 else 230
    val compact=selected.size>=3
    fun copy(candidate:Candidate):String{val pair=if(compact)compactAdvice(candidate.id,p,candidate.copy)else candidate.copy;return tr(pair.first,pair.second)}
    fun length()=selected.sumOf{copy(it).length}+if(language=="en")selected.size-1 else 0
    // Optional second activity detail yields to requested instrument coverage;
    // the highest-priority weather decision is always retained.
    if(length()>budget&&ranked.size>=2)selected.remove(ranked[1])
    val included=mutableListOf<Candidate>();var text=""
    for(candidate in selected.sortedByDescending{it.priority}){
        val combined=text+(if(language=="zh"||text.isEmpty())""else " ")+copy(candidate)
        if(combined.length<=budget){text=combined;included.add(candidate)}
    }
    return ConditionsAdvice(text,included.map{it.id})
}

private fun compactAdvice(id:String,p:Int,original:Pair<String,String>):Pair<String,String> = when(id){
    "freezing_rain"->when(p){
        2->"冻雨可能使路面结冰，先暂停骑行。" to "Freezing rain: roads may ice; pause cycling."
        in 4..8->"冻雨可能使地面器材结冰，暂停露天架设。" to "Freezing rain: ground/gear may ice; pause setup."
        10,11->"冻雨时收回露天物品，检查表面水分。" to "Freezing rain: bring items in; check moisture."
        else->"冻雨可能使地面结冰，先检查落脚处。" to "Freezing rain: ground may ice; check footing."
    }
    "snow"->when(p){
        2->"降雪影响抓地与视线，先检查骑行路况。" to "Snow affects grip/sightlines; check the route."
        in 4..8->"降雪遮挡远景，镜头和脚架需防湿。" to "Snow hides distant detail; keep optics dry."
        9->"降雪时检查营地与排水，保持睡具干燥。" to "Snow: check pitch/drainage; keep bedding dry."
        10,11->"雪水先擦干，潮湿物品不要直接收纳。" to "Wipe off snowmelt; dry items before storage."
        else->"降雪影响路面，避开未清理路段。" to "Snow affects surfaces; avoid uncleared paths."
    }
    "rain_chance"->when(p){
        2->"降水机会较高，骑行备雨具与避雨点。" to "Rain possible: pack rainwear and plan shelter."
        in 4..8->"降水机会较高，露天架设先备器材遮雨。" to "Rain possible: prepare a cover for outdoor gear."
        9->"降水机会较高，扎营先看排水与撤收路线。" to "Rain possible: check drainage and a route out."
        10->"降水机会较高，露天衣物移到遮雨处。" to "Rain possible: shelter outdoor laundry."
        11->"降水机会较高，外带器材加防水包。" to "Rain possible: waterproof equipment bags."
        else->"降水机会较高，带雨具并安排避雨路线。" to "Rain possible: carry rainwear and plan shelter."
    }
    "dew"->when(p){
        2->"近露点且风弱，车座可能有露水，骑前擦干。" to "Calm near dew point: check saddle for dew."
        3->"近露点且风弱，草地座位可能结露，落座前检查。" to "Calm near dew point: check seats for dew."
        4,5,6->"近露点且风弱，拍摄留意鞋面与镜头结露。" to "Calm near dew point: protect shoes/lenses from dew."
        7,8->"近露点且风弱，长时间观测先给镜片防露。" to "Calm near dew point: protect optics from dew."
        9->"近露点且风弱，外帐可能挂露，收帐前晾干。" to "Calm near dew point: dry a dewy flysheet."
        else->"清晨或夜间近露点且风弱，草叶可能沾湿鞋袜。" to "Calm near dew point: grass dew may wet shoes/socks."
    }
    "gusts"->when(p){
        2->"阵风较强，桥面与开阔路段留意侧风。" to "Gusts: watch exposed crosswinds."
        9->"阵风较强，检查营地遮挡和固定。" to "Gusts: check campsite shelter and anchors."
        in 4..8->"阵风会晃动支架，降低并稳固支撑。" to "Gusts: lower and stabilise mounts."
        10,11->"阵风较强，收好阳台轻物并固定器材。" to "Gusts: secure balcony items and gear."
        else->"阵风较强，留意松动物件并收好雨伞。" to "Gusts: secure loose items."
    }
    "rain"->when(p){
        0->"有雨，通勤备雨具并预留换乘时间。" to "Rain: carry rainwear and allow transfer time."
        1->"有雨，步行选便于避雨和折返的路线。" to "Rain: walk a route with shelter and an easy return."
        2->"有雨，骑行留足制动余量。" to "Rain: allow braking space and cover belongings."
        3->"有雨，停留选有顶棚且座位干燥处。" to "Rain: choose covered, dry seating."
        4->"有雨，晚霞机位需遮雨且西侧开阔。" to "Rain: shelter gear; check western view."
        5->"有雨，晨拍保护器材并确认东侧视野。" to "Rain: cover gear; check eastern view."
        6->"有雨，蓝调拍摄先遮雨并摆稳脚架。" to "Rain: cover blue-hour gear; steady tripod."
        7->"降雨遮挡星空，收起露天望远镜。" to "Rain obscures stars; shelter exposed optics."
        8->"雨中远景层次受限，优先观察近处目标。" to "Rain limits distant detail; view nearer."
        9->"有雨，检查营地排水并让睡具隔湿。" to "Rain: check drainage; keep bedding dry."
        10->"有雨，收回露天晾晒衣物。" to "Rain: bring exposed laundry under cover."
        else->"淋雨物品先晾干，再密闭收纳。" to "Dry rain-wet items before closed storage."
    }
    "wet_soil"->when(p){
        0->"湿草可能沾鞋，通勤抄近路前先检查。" to "Damp grass may wet shoes on commute shortcuts."
        1->"湿草可能沾湿鞋面，优先走铺装路。" to "Damp grass may wet shoes; prefer paved paths."
        2->"表土可能偏湿，土路留意泥泞。" to "Moist soil: unpaved sections may be muddy."
        3->"表土可能偏湿，坐草地或地上先垫坐垫。" to "Moist model soil: use a mat before sitting on grass or ground."
        4->"表土可能偏湿，晚霞机位选硬地并垫高器材。" to "Moist soil: firm sunset footing; raise bags."
        5->"表土可能偏湿，晨拍穿草地先保护鞋面。" to "Moist soil: protect shoes on dawn grass."
        6->"表土可能偏湿，蓝调低光下确认脚架落点。" to "Moist soil: check blue-hour tripod footing."
        7->"表土可能偏湿，望远镜底座与电源需隔地防潮。" to "Moist soil: raise telescope bases and power."
        8->"表土可能偏湿，远眺选硬地避免踏入湿草。" to "Moist soil: view from firm ground and avoid wet grass."
        else->"表土可能偏湿，检查营地并加防潮垫。" to "Moist soil: check pitch; use a groundsheet."
    }
    "local_check"->when(p){
        0->"通勤先看换乘与遮雨点。" to "Check commute transfers and shelter."
        1->"步行选便于折返的路线。" to "Choose a walk with an easy return."
        2->"骑行先检查车况与路线。" to "Check bicycle and route before riding."
        3->"长时间停留先检查座位干湿。" to "Check seating is dry before a long stop."
        4->"晚霞拍摄先确认西侧地平线。" to "Check the western horizon for sunset."
        5->"晨光拍摄先确认东侧视野。" to "Check the eastern view for dawn."
        6->"蓝调拍摄先摆稳脚架。" to "Stabilise the tripod for blue hour."
        7->"观星先避开路灯，光污染未实测。" to "Avoid streetlights; local light pollution is unmeasured."
        8->"远眺先确认目标方向遮挡。" to "Check obstructions toward distant targets."
        9->"扎营前现场检查排水与干湿。" to "Inspect campsite drainage and ground moisture locally."
        10->"开窗晾晒还需另看室内温湿度。" to "Check indoor temperature and humidity before airing."
        else->"收纳前确认物品干燥，柜内湿度未测量。" to "Dry items before storage; cabinet humidity is unmeasured."
    }
    else->original
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
