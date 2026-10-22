# 天气首页 · Open-Meteo 字段契约

核对日期：2026-10-22（Australia/Brisbane）。本页继续采用 Open-Meteo Forecast API。已用布里斯班公开城市坐标 `-27.47, 153.03` 实际请求，v6 原始响应保存在 `verification/open-meteo-v6-forecast.json` 与 `verification/open-meteo-v6-air.json`；界面安装包仍使用明确标识的示例数据。

## 左侧显示

| 显示项 | 数据来源 | 处理 |
| --- | --- | --- |
| 当前温度 | `current.temperature_2m` | °C，显示整数 |
| 体感温度 | `current.apparent_temperature` | °C，显示整数 |
| 风速 | `current.wind_speed_10m` | 明确请求 km/h |
| 湿度 | `current.relative_humidity_2m` | % |
| UV INDEX | `current.uv_index` | 当前模型值；不能用每日最大值冒充。显示整数，等级随显示值分档 |
| VISIBILITY | `current.visibility` | 原始单位米，除以 1000 得公里，最多一位小数；数值不截为 10 km |
| PRECIP CHANCE | `hourly.precipitation_probability` | %；旁边显示所属城市当地的小时区间，如 `17–18h` |
| CLOUD COVER | `current.cloud_cover` | %，进度条 0–100 |
| 今日最高／最低 | `daily.temperature_2m_max/min` | 选当地日期，°C，紧贴主温度 |
| SUNSET / SUNRISE | `daily.sunrise`、`daily.sunset` | 白天显示日落，夜间显示下次日出；按城市时区格式化 |
| US AQI 微型状态 | Air Quality API 的 `current.us_aqi` | 独立请求；按美国指数分级；缺失不填绿色 |
| 天气描述、动画输入 | `current.weather_code`、`current.is_day` | WMO 天气码、昼夜布尔值；不使用整天最严重天气码代表当前天气 |

删除 `CLOUD HEIGHT / Mid`。高、中、低云量是不同高度带的覆盖比例，不能据此生成真实云底高度。替代的降水概率包含雨、雪等降水，因此界面使用 PRECIP，而非仅指雨的 RAIN。

## 时段、单位与缺失值

请求使用 `timezone=auto&timeformat=unixtime`，返回的 `timezone` 用于 IANA 时区格式化。按时间戳匹配，不能取数组第一项，也不依赖设备时区。这样可以正确处理午夜与夏令时。

降水概率时间戳表示该时间之前的一小时。适配器取当前时刻之后最近的小时结束点，并明确显示这个整点区间；它不声称提供从当前分钟开始滚动的未来 60 分钟预测。不是用降水概率决定“现在正在下雨”。

当前天气是模型估计，不是设备传感器或气象站即时实测；返回的 `current.time`、`interval` 分别保留为数据有效时间和统计区间。15 分钟时间步也不表示气象模型每 15 分钟重新运行。区域和模型可能导致字段缺失；`null`、未知天气码、错误单位不填假数，显示 `—` 并隐藏对应指示线。

## 右侧动画输入规则

- 雷暴、雪、雨、雾优先使用官方 WMO 天气码分组。冻雨、冰雹雷暴等当前共用所属组的画面，文字和数据保留来源语义，不宣称各有独立动画。
- 阴天保留云层；夜间使用夜色色板，不切成晴夜星空。天气码未知时使用中性色天空，不画太阳。
- 晴/少云白天风速达到 28 km/h 时选择风场景。这是产品视觉阈值，不代表官方大风预警。夜间风场独立动画尚未制作。
- 晨光／日落场景是当前时间进入日出／日落前后 30 分钟、云量不超过 50% 时的视觉规则。雨雪雷雾与阴天优先；它不是 API 提供的独立天气类型。
- 像素移动、闪电、月牙与星点均为装饰动画；不是降水雷达、实时闪电位置或真实月相。

## 请求与接入边界

`dist/open-meteo.js` 提供 Forecast 和 Air Quality 两个请求构造器、适配器及显示格式化。天气请求包含 9 个 current 字段、1 个 hourly 字段、4 个 daily 字段，`past_days=1&forecast_days=2` 覆盖前一日日落、当日日出日落与下一日日出。昨日数据用于夜间进度的真实分母，不能用午夜或固定 12 小时替代。

`daily.time`、日出／日落均明确请求 Unix 时间。适配器按返回 IANA 时区查找当天数组索引，最高／最低也跟随相同索引。缺失日出日落时保留 null，太阳事件显示暂无数据并隐藏光点。极昼／极夜的专门文案尚未设计。

空气质量单独请求 `https://air-quality-api.open-meteo.com/v1/air-quality`，参数为 `current=us_aqi&timeformat=unixtime&timezone=auto&forecast_days=1`。实测单位为 `USAQI`，适配器要求单位正确、同一时区、与天气时间相差不超过一小时；联网阶段还需保证坐标与请求身份匹配，避免同一时区内城市切换时串用缓存。

US AQI 范围：0–50 优，51–100 中等，101–150 敏感人群需注意，151–200 不健康，201–300 非常不健康，301+ 危险。≤100 使用轻量状态点，101 起提示，151 起不健康文案。不能用这些阈值解释 European AQI 或中国 AQI。Air Quality 也是模型预报，全球 CAMS 网格不是街道传感器实测。

两个实际请求均返回 HTTP 200，新字段经适配器核对非空；报告位于 `verification/open-meteo-v6-contract.json`。只证明所测城市时刻可用，不保证任意地点都有值。示例页面没有自动发起网络请求。

后续联网接入：建议前台按 30 分钟缓存，返回前台时按缓存有效期决定是否刷新；后台停止轮询，失败保留上次数据并标示时间。正式数据页需呈现可访问的 Open-Meteo 数据来源说明。当前 APK 没有 INTERNET 权限，未启用自动请求，也未实现联网缓存。

免费层按非商业用途及调用预算使用。不能直接由“10,000/日”推算支持几千日活：1000 台设备各全天每 30 分钟请求一次，已经是 48,000 个 HTTP 请求/日；超过 10 个变量的请求还可能按多个调用计费。共享缓存、实际活跃时间与刷新策略应在联网阶段核算。

## 官方依据与验证

- [Forecast API 字段、时间语义与 WMO 码](https://open-meteo.com/en/docs)
- [Air Quality API 的 US AQI 与单位](https://open-meteo.com/en/docs/air-quality-api)
- [官方配额、调用计算与署名说明](https://open-meteo.com/en/pricing)
- [ARPANSA 紫外线指数等级](https://www.arpansa.gov.au/understanding-radiation/radiation-sources/more-radiation-sources/sun-exposure)

`npm run check` 覆盖真实响应适配、米转公里、跨日匹配、夏令时、空值与零值、错误单位、日出日落边界、下一日日出、AQI 阈值与过期数据、WMO 分组和动画优先级，以及所有支持天气码的中文翻译。v6 在两种比例下验证三种语言 × 十一种天气共 66 个布局组合；已在 3456×2304 Android 模拟器中安装、启动及录制默认双语页面，尚未在小米真机复测。

## v9 显示单位与设置

接口适配器继续保留摄氏度、km/h 的规范数据。`weather-units.js` 仅生成显示副本：主温、最高／最低、体感一起转为华氏，风速按准确因子转换为 m/s、mph 或 kts。天气动画、风场阈值与日出日落逻辑使用规范数据，不受显示单位影响。降水概率仍为百分比，不增加 mm/in 的空置控制。

APK 新增可选前台定位，尚未根据坐标请求天气。示例标签始终保留；当前缓存大小为实际天气／地图数据缓存，默认 0 B。GitHub 更新和反馈渠道待创建仓库后接入。
