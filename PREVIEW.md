# Web preview / 网页原型

Current work is the browser weather-screen prototype. APK packaging and publication are paused while layout and visuals are refined.

```sh
npm ci --ignore-scripts
npm run check
npm run gallery
npm run dev
```

Open `http://127.0.0.1:4175/`; `/gallery.html` compares complete weather pages. The three coloured dots open eleven distinct sample scenarios. Language is either Chinese or English, never simultaneous. Example: `?weather=storm&lang=en&pixels=fine&motion=on`.

`npm run gallery` writes the self-contained `output/BARO-gallery.html`; generated files are ignored by Git. The authored HTML/CSS/ES modules live in `dist/`. All weather remains labelled sample data.

中文：先运行网页预览并确认设计，不需要 Android SDK。当前不打包、不发布 APK。首页三个色点可切换不同天气与时刻，设置只提供中文或英文两种语言。温度单位与主数字的底部基线对齐，并保留明确间距。

The observation page is available at `/?weather=storm&page=sky&lang=zh`. Its lower-right purpose dial is the only information control; drag it, scroll or use arrow keys. The gallery page selector compares either interface. See [instrument panel](docs/INSTRUMENT-PANEL.md) for demo-data and calculation limits.
