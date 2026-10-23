> Historical implementation note. Current settings/list, touch, motion and release behavior is defined in [settings interaction](SETTINGS-INTERACTION.md). Older empty-repository and direct-update-link records below are historical.

# v9 设置与天气场景

设置、来源／许可、反馈、关于与场景库都替换同一左侧容器。右侧天气图始终保留。页头和页脚固定，中间独立滚动；分类按钮将当前内容滚动到对应组。详情返回先回设置，× 直接回天气页。

`app.js` 保存规范化偏好，将换算后的副本交给文字格式化，原始天气继续输入渲染器。`weather-units.js` 使用 °F = °C × 9/5 + 32；km/h 转 m/s、mph、kts 的除数分别为 3.6、1.609344、1.852。空值不换算为零。未选择降水量或气压字段，避免添加空置单位配置。

`settings.js` 负责分组、状态、详情与 11 个天气场景；`device-services.js` 隔离 Android 原生能力与浏览器降级。`app-meta.js` 记录版本和未来 GitHub 地址。当前仓库地址为空，点击检查更新显示未配置，反馈仅复制本地文本与版本／显示偏好，不附带坐标。

`DeviceServices.java` 通过只供安装资源使用的 JavascriptInterface 提供版本、前台定位、缓存、剪贴板与用户点击的 https 外链。WebView 继续阻止网络请求与远程导航。定位默认关闭；用户开启才请求精确与大致权限，前台使用 GPS 或网络定位，暂停页面时移除更新，不申请后台权限。不把定位状态或示例数据描述为实时天气。

Android 缓存仅统计和清理 `getCacheDir()/weather`、`getCacheDir()/maps`，不遍历符号链接，不操作偏好和安装资源。浏览器只使用 `sundown-weather-v1`、`sundown-maps-v1`，忽略其他应用缓存。无缓存时显示 0 B。当前没有真正的联网预报／地图数据，因此不虚构缓存体积。

已完成：单位和空值自动检查、浏览器真实按钮操作、两种比例下的设置与场景排版、原生编译／安装／启动和高清截图／动画录制。

尚待目标设备验证：定位授权的允许／拒绝／仅大致／系统关闭／回前台流程，非空原生缓存清理，剪贴板及外链返回，Android 返回键。GitHub Releases 与 Issues 尚未接入，实时天气尚未接入。
