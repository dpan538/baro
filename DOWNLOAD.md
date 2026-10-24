# BARO Android 测试版下载

当前公开测试版：**0.10.0-preview.6-r2（versionCode 19）**，使用选定的 **G1b** 图标。Android 10 及以上，安装包 20,560,756 字节（约 20.6 MB）。

**[点击直接下载 APK](https://github.com/dpan538/baro/releases/download/v0.10.0-preview.6-r2/BARO-0.10.0-preview.6-r2.apk)**

也可以复制下面的完整地址，在平板浏览器中打开：

```text
https://github.com/dpan538/baro/releases/download/v0.10.0-preview.6-r2/BARO-0.10.0-preview.6-r2.apk
```

[版本说明与全部附件](https://github.com/dpan538/baro/releases/tag/v0.10.0-preview.6-r2) · [SHA-256 校验文件](https://github.com/dpan538/baro/releases/download/v0.10.0-preview.6-r2/SHA256SUMS)

## 在 Pad 上安装

1. 用平板浏览器点击上面的「点击直接下载 APK」，无需登录 GitHub。
2. 下载后打开 `BARO-0.10.0-preview.6-r2.apk`。首次安装时，按 Android 提示允许当前浏览器或文件管理器安装应用，再确认安装。
3. 打开 **BARO / 晴雨**。左右任一方向完成滑动，都可在天气页与环境观测页之间切换；齿轮打开设置。

## 后续直接覆盖升级

进入 **设置 → 关于 → 检查更新**。有更高版本时，下载完成会核验文件、包名、版本和签名，再打开 Android 系统安装器。需要时允许 BARO 安装应用并确认「更新」。同签名测试版直接覆盖，保留设置和反馈草稿，无须卸载旧版。

本次已在原生模拟器验证从 preview.5 经应用内下载和 Android 系统安装器覆盖升级，设置、反馈草稿和私有数据保留。

已经安装本版时，检查更新应显示当前版本；只有后续更高 versionCode 的版本才提供更新。本项目专为小米 Pad 9 Pro Max 开发，后续逐步适配其他尺寸的小米 Pad。

这是非调试预发布包，沿用固定的 BARO 测试证书。反馈经 BARO 的 Vercel 服务创建 GitHub Issue，核验回执后才显示“已提交”；失败显示联网重试提示并保留草稿。小米真机功耗、物理触感及厂商安装界面仍需实测。

此前 preview.6 / 18 已撤回为草稿并保留。本次使用 preview.6-r2 / 19，避免覆盖已分发附件，保持升级版本号递增。

APK SHA-256：

```text
138dea585bfb3734f6e8f052e10b026747f480afb989a09f9dad7b8bfa8f4b26
```

匿名公网下载副本与验收 APK 的哈希一致，包名为 `studio.sundown.weather`。源码与详细验收见[测试版分发说明](docs/ANDROID-PREVIEW-DISTRIBUTION.md)和[本包验收记录](android/verification/preview-0.10.0-preview.6-r2.json)。

## English

[**Download the Android test APK ↗**](https://github.com/dpan538/baro/releases/download/v0.10.0-preview.6-r2/BARO-0.10.0-preview.6-r2.apk)

Open the link in your tablet browser, then open the downloaded APK and follow Android's installation prompts. Later, use **Settings → About → Check for updates**. Updates signed with the same BARO test certificate install over the existing app and retain preferences and drafts; Android still requires confirmation. This is a public, non-debug preview, not a stable store release.
