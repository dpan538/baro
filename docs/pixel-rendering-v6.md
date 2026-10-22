> Historical implementation note. Current scope is documented in [implementation status](IMPLEMENTATION-STATUS.md) and [architecture](ARCHITECTURE.md).

# v6 · 像素天气技术核对与设计系统

核对日期：2026-10-22。

## 选型

AGSL 从 Android 13（API 33）开始提供 RuntimeShader，可以用于 Java/Kotlin 原生 Canvas/View，也可用于 Compose。项目当前为 Java Activity + WebView、最低 API 29。AGSL 不能直接作为网页 Canvas 的 shader 插入；引入它需要增加原生绘制层，并处理跨层参数、圆角和页面生命周期。

本版采用 WebGL2 fragment shader。WebGL2 基于 OpenGL ES 3.0，适合现有 WebView 和独立浏览器预览。双通道使复杂光影只运行在约 256×340 个逻辑像素上，物理分辨率仅执行纹理拷贝。它足以实现当前设计要求，不存在“用了 AGSL 就必然更精细”的结论。精细度主要由像素网格、云团轮廓、色阶距离、抖动与构图决定。

未来若改成原生 Compose，可将云团距离场、色板与 Bayer 算法迁移到 RuntimeShader；当前没有声称已实现 AGSL。

## 统一语汇

- 网格：256 列，按右侧宽高比算行数，方形逻辑像素，NEAREST 放大。没有高密度格线遮挡细节。
- 天空：四级主色阶，有序抖动连接色带；太阳附近使用局部量化暖光，避免整片天空出现高反差噪点。
- 云团：椭圆距离场合并的多叶轮廓，细粒度边缘变化；共享四级阴影／中间调／亮面／透光边缘，夜间替换蓝灰色板。
- 晨昏：独立的冷暖色板、低太阳位置和两层地平线。月牙为设计图形，不计算真实月相。
- 降水：雨线沿倾斜方向错相落下；雪花分前后层下落与摆动；雷暴每八秒仅出现一次短暂局部闪电，不闪白全屏。
- 动作：24fps 上限、192 帧循环。天空抖动固定在网格上，避免整屏颗粒闪烁；仅云层、光影、天气元素运动。
- 性能：ResizeObserver 更新物理画布；IntersectionObserver、页面可见性和系统减弱动态效果共同控制动画；销毁时释放 GPU 对象和观察器。
- 降级：不支持 WebGL2 或 shader 初始化失败时使用旧版 Canvas 24 列场景，保留可见天气反馈。目标设备上的清晰度仍需真机确认。

`dist/weather-shader.js` 为 GPU 实现；`dist/weather-art.js` 提供完整场景、天气映射、太阳时间状态及旧版降级渲染；`dist/weather-icons.js` 为 SVG 天气图标系统。

## 太阳状态

以 Unix 时间戳比较，IANA 时区只负责当地日期／时间格式化。`sunrise ≤ now < sunset` 为白天；其余时段选大于 now 的下一次日出。昼间点位为 `(now-sunrise)/(sunset-sunrise)`；夜间为 `(now-previousSunset)/(nextSunrise-previousSunset)`。缺少有效边界时隐藏点位，不使用固定 06:00／18:00 补造事件。

晴天场景在日出前后 30 分钟、日落前后 30 分钟允许晨昏色板；此规则独立于左侧“即将发生的太阳事件”，因此日出之后的晨光页仍显示日落。雨、雪、雷、雾优先，不因到了傍晚就变成晴天晚霞。

## 验证范围

浏览器检查 66 个完整页面组合，全部使用 WebGL2。Android API 36 / WebView 133 模拟器在 3456×2304 屏幕实际安装运行，右侧精细绘制可见。八秒实际录屏约 193 帧，平均 24fps；这是模拟器录屏证据，不能据此保证小米真机帧率或功耗。

多 iframe 对照页在 CUA 可访问性检查时记录了无来源 URL 的 MutationObserver 错误；项目源码不使用 MutationObserver。所有 11 个 iframe 的 GPU 渲染与控件检查成功，没有观察到 WebGL 上下文数量警告。该无来源错误尚未归因，不能把对照页声称为全日志无错误。单页未观察到应用脚本或 shader 错误。

## 官方资料

- [Android AGSL 概述与 API 33 要求](https://developer.android.com/develop/ui/views/graphics/agsl)
- [Android 使用 RuntimeShader](https://developer.android.com/develop/ui/views/graphics/agsl/using-agsl)
- [Khronos WebGL 2.0 规范](https://registry.khronos.org/webgl/specs/2.0.0/)
