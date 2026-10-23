// Shared weather vocabulary and the legacy Canvas fallback's eight-second timeline.
// Weather fixtures are deliberately illustrative; no network observations are used.
export const MOTION = Object.freeze({ fps: 12, frames: 96, columns: 24 });
// Lift light skies while giving shadows a deeper black point. Shared by GPU and fallback.
export const PIXEL_TONE = Object.freeze({ saturation: 1.01, peak: .97, shadowGain: .74, highlightRemainder: .74, lowKnee: .16, highKnee: .76 });
const toneCache = new Map();
function toneHex(value) {
  if(toneCache.has(value))return toneCache.get(value);
  let rgb=[1,3,5].map(offset=>parseInt(value.slice(offset,offset+2),16)/255);
  const neutral=rgb[0]*.2126+rgb[1]*.7152+rgb[2]*.0722;
  rgb=rgb.map(channel=>Math.max(0,Math.min(1,neutral+(channel-neutral)*PIXEL_TONE.saturation)));
  const luminance=rgb[0]*.2126+rgb[1]*.7152+rgb[2]*.0722;
  const x=Math.max(0,Math.min(1,(luminance-PIXEL_TONE.lowKnee)/(PIXEL_TONE.highKnee-PIXEL_TONE.lowKnee)));
  const blend=x*x*(3-2*x);
  const lifted=luminance*PIXEL_TONE.shadowGain*(1-blend)+(1-(1-luminance)*PIXEL_TONE.highlightRemainder)*blend;
  const color='#'+rgb.map(channel=>Math.round(Math.min(1,channel*lifted/Math.max(luminance,.001))*PIXEL_TONE.peak*255).toString(16).padStart(2,'0')).join('');
  toneCache.set(value,color);return color;
}

// Each fixture is one complete local-time scenario, not a recoloured shared card.
function weatherExample(input) {
  const date = input.at.slice(0, 10);
  const offset = input.at.slice(-6);
  const previousDate = new Date(Date.parse(date + 'T12:00:00Z') - 86400000).toISOString().slice(0, 10);
  const nextDate = new Date(Date.parse(date + 'T12:00:00Z') + 86400000).toISOString().slice(0, 10);
  const instant = (day, clock) => Date.parse(day + 'T' + clock + ':00' + offset) / 1000;
  const validAt = Date.parse(input.at) / 1000;
  const sunriseAt = instant(date, input.rise), sunsetAt = instant(date, input.set);
  const hour = Number(input.at.slice(11, 13));
  const uv = Math.round(input.uvIndex);
  return {
    ...input, validAt, localDate: date, sunriseAt, sunsetAt,
    nextSunriseAt: instant(nextDate, input.nextRise),
    previousSunsetAt: input.previousSetAt ? Date.parse(input.previousSetAt) / 1000 : instant(previousDate, input.previousSet ?? input.set),
    airQuality: { value: input.usAqi, standard: 'US AQI', validAt, timezone: input.timezone },
    isDay: validAt >= sunriseAt && validAt < sunsetAt,
    precipitationWindow: String(hour).padStart(2, '0') + '–' + String((hour + 1) % 24).padStart(2, '0') + 'h',
    uvLabel: uv < 3 ? 'Low' : uv < 6 ? 'Moderate' : uv < 8 ? 'High' : uv < 11 ? 'Very high' : 'Extreme'
  };
}
export const WEATHER_PRESETS = Object.freeze({
  dawn: weatherExample({ at: '2026-10-22T05:25:00+10:00', timezone: 'Australia/Brisbane', cityZh: '布里斯班', cityEn: 'BRISBANE', rise: '05:06', set: '17:59', nextRise: '05:05',
    temperatureMax: 27, temperatureMin: 17, usAqi: 24, temperature: 18, feelsLike: 18, windSpeed: 5, humidity: 78, uvIndex: 0, cloudCover: 12, precipitationProbability: 3, visibility: 24, code: 1, name: '晨光', condition: 'Clear skies', motion: '清晨初光 · 蓬云、远鸟与碎金海面' }),
  partly: weatherExample({ at: '2026-10-23T08:20:00+08:00', timezone: 'Asia/Shanghai', cityZh: '上海', cityEn: 'SHANGHAI', rise: '06:03', set: '17:16', nextRise: '06:04',
    temperatureMax: 26, temperatureMin: 18, usAqi: 154, temperature: 22, feelsLike: 23, windSpeed: 11, humidity: 66, uvIndex: 2, cloudCover: 36, precipitationProbability: 12, visibility: 14, code: 2, name: '晴间多云', condition: 'Partly cloudy', motion: '蔚蓝晴空 · 多层云缓慢漂移与柔和透光' }),
  clear: weatherExample({ at: '2026-10-26T12:35:00+10:00', timezone: 'Australia/Brisbane', cityZh: '布里斯班', cityEn: 'BRISBANE', rise: '05:01', set: '18:03', nextRise: '05:00',
    temperatureMax: 32, temperatureMin: 21, usAqi: 31, temperature: 30, feelsLike: 33, windSpeed: 13, humidity: 48, uvIndex: 10, cloudCover: 3, precipitationProbability: 2, visibility: 42, code: 0, name: '晴天', condition: 'Clear skies', motion: '明亮正午 · 太阳光晕与穿云光芒' }),
  overcast: weatherExample({ at: '2026-10-30T14:10:00+00:00', timezone: 'Europe/London', cityZh: '伦敦', cityEn: 'LONDON', rise: '06:51', set: '16:36', nextRise: '06:53',
    temperatureMax: 13, temperatureMin: 8, usAqi: 42, temperature: 11, feelsLike: 9, windSpeed: 18, humidity: 79, uvIndex: 1, cloudCover: 98, precipitationProbability: 47, visibility: 9, code: 3, name: '阴天', condition: 'Overcast', motion: '阴天云幕 · 四层云团缓慢漂移与层间遮挡' }),
  rain: weatherExample({ at: '2026-10-27T16:40:00+09:00', timezone: 'Asia/Tokyo', cityZh: '东京', cityEn: 'TOKYO', rise: '05:59', set: '16:51', nextRise: '06:00',
    temperatureMax: 20, temperatureMin: 15, usAqi: 38, temperature: 17, feelsLike: 16, windSpeed: 19, humidity: 93, uvIndex: 0, cloudCover: 100, precipitationProbability: 92, visibility: 4.8, code: 63, name: '雨天', condition: 'Rain', motion: '傍晚降雨 · 三层雨幕与冷蓝云光' }),
  storm: weatherExample({ at: '2026-10-24T20:15:00+08:00', timezone: 'Asia/Singapore', cityZh: '新加坡', cityEn: 'SINGAPORE', rise: '06:47', set: '18:52', nextRise: '06:47',
    temperatureMax: 31, temperatureMin: 25, usAqi: 58, temperature: 27, feelsLike: 32, windSpeed: 33, humidity: 91, uvIndex: 0, cloudCover: 100, precipitationProbability: 97, visibility: 3.2, code: 95, name: '雷暴', condition: 'Thunderstorm', motion: '夜间雷暴 · 分叉闪电、云内照明与三层雨幕' }),
  snow: weatherExample({ at: '2026-10-29T08:50:00+01:00', timezone: 'Europe/Oslo', cityZh: '奥斯陆', cityEn: 'OSLO', rise: '07:34', set: '16:24', nextRise: '07:37',
    temperatureMax: 1, temperatureMin: -5, usAqi: 18, temperature: -2, feelsLike: -7, windSpeed: 22, humidity: 85, uvIndex: 1, cloudCover: 95, precipitationProbability: 86, visibility: 2.3, code: 73, name: '降雪', condition: 'Snow', motion: '早晨降雪 · 三层雪晶下落与横向飘移' }),
  fog: weatherExample({ at: '2026-10-25T07:10:00+01:00', timezone: 'Europe/Amsterdam', cityZh: '阿姆斯特丹', cityEn: 'AMSTERDAM', rise: '07:24', set: '17:26', nextRise: '07:26',
    temperatureMax: 14, temperatureMin: 7, usAqi: 112, previousSetAt: '2026-10-24T18:28:00+02:00', temperature: 9, feelsLike: 8, windSpeed: 3, humidity: 99, uvIndex: 0, cloudCover: 90, precipitationProbability: 18, visibility: 0.3, code: 45, name: '雾', condition: 'Fog', motion: '日出前晨雾 · 海岛灯塔与流动雾层' }),
  wind: weatherExample({ at: '2026-10-28T15:05:00+13:00', timezone: 'Pacific/Auckland', cityZh: '惠灵顿', cityEn: 'WELLINGTON', rise: '06:11', set: '19:57', nextRise: '06:10',
    temperatureMax: 19, temperatureMin: 12, usAqi: 15, temperature: 17, feelsLike: 12, windSpeed: 44, humidity: 58, uvIndex: 4, cloudCover: 45, precipitationProbability: 24, visibility: 28, code: 2, name: '大风', condition: 'Windy', motion: '午后大风 · 浅金日光、扁云与三层风迹' }),
  night: weatherExample({ at: '2026-11-02T23:10:00+00:00', timezone: 'Atlantic/Reykjavik', cityZh: '雷克雅未克', cityEn: 'REYKJAVIK', rise: '09:12', set: '17:11', nextRise: '09:15',
    temperatureMax: 6, temperatureMin: -1, usAqi: 12, temperature: 4, feelsLike: 0, windSpeed: 9, humidity: 69, uvIndex: 0, cloudCover: 7, precipitationProbability: 1, visibility: 36, code: 0, name: '晴夜', condition: 'Clear night', motion: '深夜月光 · 三层星野、月面纹理与偶发流星' }),
  sunset: weatherExample({ at: '2026-10-22T17:48:00+10:00', timezone: 'Australia/Brisbane', cityZh: '布里斯班', cityEn: 'BRISBANE', rise: '05:06', set: '17:59', nextRise: '05:05',
    temperatureMax: 28, temperatureMin: 18, usAqi: 35, temperature: 24, feelsLike: 25, windSpeed: 7, humidity: 57, uvIndex: 0, cloudCover: 21, precipitationProbability: 6, visibility: 26, code: 1, name: '日落', condition: 'Golden hour', motion: '橙金晚霞 · 暖云、归鸟与铜金倒影' })
});

export function resolveTimeContext(weather) {
  const fallback = { date: weather.localDate ?? null, clock24: '—', clock12: '—', meridiem: '', phase: weather.period === 'sunset' ? 'dusk' : weather.isDay === false ? 'night' : weather.isDay === true ? 'day' : 'unknown', event: null, eventAt: null };
  if (!Number.isFinite(weather.validAt) || typeof weather.timezone !== 'string') return fallback;
  let local;
  const format = (time, options) => Object.fromEntries(new Intl.DateTimeFormat('en-US', { timeZone: weather.timezone, ...options }).formatToParts(new Date(time * 1000)).map(part => [part.type, part.value]));
  try { local = format(weather.validAt, { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }); }
  catch { return fallback; }
  const now = weather.validAt, sunrise = weather.sunriseAt, sunset = weather.sunsetAt;
  let phase;
  if (Number.isFinite(sunrise) && Math.abs(now - sunrise) <= 1800) phase = 'dawn';
  else if (Number.isFinite(sunset) && Math.abs(now - sunset) <= 1800) phase = 'dusk';
  else if (weather.isDay === false) phase = 'night';
  else if (weather.isDay !== true) phase = 'unknown';
  else phase = Number(local.hour) < 11 ? 'morning' : Number(local.hour) < 14 ? 'midday' : 'afternoon';
  // The upcoming solar event is independent of the illustrative dawn/dusk scene.
  // Equality belongs to the new state: sunrise -> day, sunset -> night.
  const hasDayBounds = Number.isFinite(sunrise) && Number.isFinite(sunset) && sunrise < sunset;
  const daytime = hasDayBounds ? now >= sunrise && now < sunset : weather.isDay === true;
  let eventAt = daytime ? sunset : [sunrise, weather.nextSunriseAt].filter(time => Number.isFinite(time) && time > now).sort((a,b) => a-b)[0];
  let event = Number.isFinite(eventAt) ? daytime ? 'sunset' : 'sunrise' : null;
  const startAt = daytime ? sunrise : now < sunrise ? weather.previousSunsetAt : sunset;
  const progress = Number.isFinite(startAt) && Number.isFinite(eventAt) && startAt <= now && eventAt > startAt
    ? Math.max(0, Math.min(1, (now-startAt)/(eventAt-startAt))) : null;
  if (!Number.isFinite(eventAt)) eventAt = null;
  const hour12 = Number(local.hour) % 12 || 12;
  return { date: local.year + '-' + local.month + '-' + local.day, clock24: local.hour + ':' + local.minute,
    clock12: hour12 + ':' + local.minute, meridiem: Number(local.hour) < 12 ? 'am' : 'pm', phase, event, eventAt, progress, nighttime: !daytime };
}

const PALETTES = {
  dawn: ['#29457a','#617aa6','#c7abb3','#edb68c','#faca8c'],
  clear: ['#3b69ab','#4f8abf','#78abcc','#99bfd9','#b8d1e0'],
  partly: ['#3b69ab','#4f8abf','#78abcc','#99bfd9','#b8d1e0'],
  overcast: ['#294a75','#4778a1','#7da8c4','#c7dee8','#e4eef1'],
  rain: ['#122952','#244f85','#4578a8','#6b9abd','#8ab3d4'],
  storm: ['#0a0f21','#142140','#294063','#3a5480','#4a668f'],
  snow: ['#3363a8','#5e96c9','#96c2e0','#bbd8e8','#e0f0f5'],
  fog: ['#576678','#8294a1','#b5bfbf','#cfd4cf','#dfe0d4'],
  wind: ['#3b69ab','#4f8abf','#78abcc','#99bfd9','#b8d1e0'],
  night: ['#060613','#12142e','#213359','#344c70','#4a6187'],
  sunset: ['#2e195e','#6e2e70','#e05c4d','#f5b04d','#ecc482'],
  unknown: ['#b8c3c7', '#c3cdd0', '#ced6d7', '#dbe0de', '#e7e9e3']
};

// Explicit WMO groups: absent or unrecognised codes never become a sunny scene.
export const WEATHER_CODE_GROUPS = Object.freeze({
  clear: [0], partly: [1, 2], overcast: [3], fog: [45, 48],
  rain: [51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82],
  snow: [71, 73, 75, 77, 85, 86], storm: [95, 96, 97, 99]
});

export function resolveWeatherScene(weather) {
  const code = weather.code;
  const group = Object.entries(WEATHER_CODE_GROUPS).find(([, codes]) => codes.includes(code))?.[0];
  if (!group) return 'unknown';
  if (['storm', 'snow', 'rain', 'fog'].includes(group)) return group;
  if (weather.cloudCover >= 80 || code === 3) return 'overcast';
  if (weather.isDay === true && weather.windSpeed >= 28) return 'wind';
  const phase = resolveTimeContext(weather).phase;
  if (Number.isFinite(weather.cloudCover) && weather.cloudCover <= 50) {
    if (phase === 'dawn') return 'dawn';
    if (phase === 'dusk') return 'sunset';
  }
  if (weather.isDay === false) return 'night';
  if (weather.isDay !== true) return 'unknown';
  if (weather.cloudCover > 15 || code === 1 || code === 2) return 'partly';
  return 'clear';
}

const CLOUD = ['00001110000000', '00111111011000', '01111111111100', '11111111111111', '01111111111110'];
const SMALL_CLOUD = ['0001110000', '0111111100', '1111111111', '0111111110'];
const LONG_CLOUD = ['000011111000000000', '001111111111110000', '111111111111111111', '011111111111111110'];
const mix = (a, b, amount) => {
  const part = (s, offset) => parseInt(s.slice(offset, offset + 2), 16);
  return '#' + [1, 3, 5].map(offset => Math.round(part(a, offset) * (1 - amount) + part(b, offset) * amount).toString(16).padStart(2, '0')).join('');
};

export function createWeatherFrame(weather, visibleRows, tick = 0) {
  const scene = resolveWeatherScene(weather);
  const columns = MOTION.columns;
  const rows = Math.ceil(visibleRows);
  const frame = ((tick % MOTION.frames) + MOTION.frames) % MOTION.frames;
  const nocturnal = weather.isDay === false && !['unknown', 'dawn', 'sunset'].includes(scene);
  const palette = nocturnal ? PALETTES.night : PALETTES[scene];
  const pixels = new Array(columns * rows);
  for (let y = 0; y < rows; y++) for (let x = 0; x < columns; x++) {
    const band = Math.min(palette.length - 1, Math.floor(y / visibleRows * palette.length));
    pixels[y * columns + x] = (x * 13 + y * 7) % 23 === 0 ? mix(palette[band], '#ffffff', .025) : palette[band];
  }
  const put = (x, y, color) => {
    x = Math.round(x); y = Math.round(y);
    if (x >= 0 && x < columns && y >= 0 && y < rows) pixels[y * columns + x] = color;
  };
  const drift = (offset = 0) => [0, 1, 2, 1][Math.floor((frame + offset) % 96 / 24)];
  const cloud = (left, top, mask = CLOUD, dark = false, offset = 0) => {
    const shades = scene==='sunset'?['#f8db7a','#edab66','#974b83']:scene==='dawn'?['#f8e8ba','#eeb987','#8a73bd']:nocturnal?['#9aafff','#5067c9','#1b307b']:dark?['#9eaaff','#4b59b8','#1b2866']:['#f8f8ed','#d9eaf2','#80b4db'];
    const x0 = Math.round(left + drift(offset));
    const y0 = Math.round(top);
    mask.forEach((line, y) => [...line].forEach((on, x) => {
      if (on === '1') put(x0 + x, y0 + y, shades[y === mask.length - 1 ? 2 : y >= mask.length - 2 ? 1 : 0]);
    }));
  };
  const sun = (cx, cy, rays = true) => {
    for (let dy = -4; dy <= 4; dy++) for (let dx = -4; dx <= 4; dx++) {
      if (dx * dx + dy * dy <= 15) put(cx + dx, cy + dy, dy < -1 ? '#ffffb5' : dy > 1 ? '#ffb51e' : '#ffe637');
    }
    if (rays) {
      const bright = Math.floor(frame / 12) % 2 === 0 ? '#ffcf22' : '#fff280';
      for (const [dx, dy] of [[0,-6],[0,-5],[0,5],[0,6],[-6,0],[-5,0],[5,0],[6,0],[-4,-4],[-5,-5],[4,-4],[5,-5],[-4,4],[-5,5],[4,4],[5,5]]) put(cx + dx, cy + dy, bright);
    }
  };

  if (scene === 'clear' || scene === 'partly' || scene === 'wind') {
    sun(scene === 'clear' ? 15 : 16, Math.max(7, Math.round(visibleRows * .27)));
    if (scene === 'clear') cloud(-4, visibleRows * .76, SMALL_CLOUD);
    else {
      cloud(-3, visibleRows * .47, CLOUD);
      cloud(15, visibleRows * .68, SMALL_CLOUD, false, 48);
      if (weather.cloudCover > 50) cloud(10, visibleRows * .37, SMALL_CLOUD, false, 24);
    }
    if (scene === 'wind') {
      for (let line = 0; line < 3; line++) {
        const x0 = ((Math.floor(frame / 4) + line * 8) % 32) - 8;
        const y = Math.round(visibleRows * (.57 + line * .12));
        for (let x = 0; x < 5; x++) put(x0 + x, y, '#f5f4e7');
      }
    }
  } else if (scene === 'overcast') {
    cloud(-5, visibleRows * .14, LONG_CLOUD);
    cloud(10, visibleRows * .34, CLOUD, false, 48);
    cloud(-3, visibleRows * .59, LONG_CLOUD, false, 24);
    cloud(15, visibleRows * .8, SMALL_CLOUD, false, 72);
  } else if (scene === 'rain' || scene === 'storm' || scene === 'snow') {
    cloud(-3, visibleRows * .13, LONG_CLOUD, scene === 'storm');
    cloud(10, visibleRows * .2, CLOUD, scene === 'storm', 48);
    const top = Math.round(visibleRows * .43);
    const depth = Math.max(6, rows - top - 1);
    const columnsForDrops = [2,6,10,15,19,22,4,8,12,17,21,1,5,9,14,18];
    const phasesForDrops = [0,7,3,12,5,14,1,10,16,6,13,4,15,2,11,8];
    for (let index = 0; index < (scene === 'snow' ? 10 : 16); index++) {
      const column = columnsForDrops[index];
      const slow = scene === 'snow' ? Math.floor(frame / 4) : frame;
      const y = top + ((phasesForDrops[index] + slow) % depth);
      if (scene === 'snow') {
        const x = column + [0, 1, 0, -1][Math.floor(frame / 24)];
        put(x, y, '#ffffff');
        if (index % 3 === 0) for (const [dx,dy] of [[-1,0],[1,0],[0,-1],[0,1]]) put(x+dx, y+dy, '#edf5f4');
      } else {
        put(column, y, scene === 'storm' ? '#9ec5dd' : '#5d89a9');
        put(column - 1, y + 1, scene === 'storm' ? '#82abc8' : '#739cb8');
      }
    }
    if (scene === 'storm' && frame >= 24 && frame <= 28) {
      const x0 = 14;
      const y0 = Math.round(visibleRows * .38);
      [[0,0],[-1,1],[-2,2],[-1,2],[0,2],[-1,3],[-2,4],[-3,5]].forEach(([x,y])=>put(x0+x,y0+y,'#ffe6a1'));
    }
  } else if (scene === 'fog') {
    for (let layer = 0; layer < 5; layer++) {
      const y = Math.round(visibleRows * (.19 + layer * .155));
      const color = layer % 2 ? '#e9ede7' : '#dce3df';
      const x0 = (layer % 2 ? -3 : 4) + drift(layer * 12);
      for (let dy = 0; dy < 2; dy++) for (let x = 0; x < 20; x++) put(x0 + x, y + dy, color);
      put(x0 - 1, y + 1, color); put(x0 + 20, y, color);
    }
  } else if (scene === 'night') {
    const cx = 16, cy = Math.max(6, Math.round(visibleRows * .27));
    for (let dy=-4;dy<=4;dy++) for(let dx=-4;dx<=4;dx++) {
      if(dx*dx+dy*dy<=15 && (dx-2)*(dx-2)+dy*dy>12) put(cx+dx,cy+dy,dy>1?'#ffdb5a':'#fffbd1');
    }
    [[3,.12],[8,.21],[21,.13],[4,.48],[12,.43],[20,.54],[7,.74],[17,.83]].forEach(([x,f],index)=>{
      const y=Math.round(visibleRows*f);
      const light=(Math.floor(frame/12)+index)%4;
      put(x,y,light===0?'#fff1a0':light===1?'#96e8ff':'#5480dd');
      if(index%3===0 && light===0) for(const [dx,dy] of [[-1,0],[1,0],[0,-1],[0,1]]) put(x+dx,y+dy,'#75cfff');
    });
    cloud(-4,visibleRows*.64,SMALL_CLOUD,false,24);
  } else if (scene === 'sunset' || scene === 'dawn') {
    const morning = scene === 'dawn';
    sun(morning ? 8 : 16,Math.round(visibleRows*(morning ? .72 : .68)),false);
    const horizon=Math.round(visibleRows*.735),center=morning?8:16;
    const far=morning?'#708799':'#6e5763',near=morning?'#1f3b57':'#1a2640';
    for(let y=horizon;y<rows;y++) for(let x=0;x<24;x++) {
      const depth=(y-horizon)/Math.max(1,rows-horizon-1);
      let water=mix(far,near,Math.sqrt(depth));
      if(y%2===0&&(x+y*5)%9<2)water=mix(water,'#758fa6',.18);
      const shimmer=.55+.2*Math.sin(frame/MOTION.frames*Math.PI*2+(x+y)*1.7);
      if(y%2===0&&Math.abs(x-center)<1+depth*5&&(x*7+y*3)%5<3)water=mix(water,'#ffd07a',shimmer);
      put(x,y,water);
    }
    if(morning)cloud(13,visibleRows*.24,LONG_CLOUD,false,48);
    else cloud(-5,visibleRows*.29,SMALL_CLOUD,false,48);
  }
  return { pixels: pixels.map(toneHex), rows, columns, scene, palette: palette.map(toneHex) };
}

export class PixelWeatherRenderer {
  constructor(canvas, weather, { animated = true } = {}) {
    this.canvas=canvas; this.ctx=canvas.getContext('2d',{alpha:false}); this.weather=weather;
    this.animated=animated; this.visible=true; this.lastFrame=-1; this.previous=[];
    this.reducedMotion=matchMedia('(prefers-reduced-motion: reduce)');
    this.onVisibility=()=>this.sync();
    this.onMotion=()=>{this.lastFrame=-1;this.sync();};
    this.onResize=()=>this.resize();
    document.addEventListener('visibilitychange',this.onVisibility);
    window.addEventListener('resize',this.onResize);
    this.reducedMotion.addEventListener('change',this.onMotion);
    this.resizeObserver=new ResizeObserver(()=>this.resize()); this.resizeObserver.observe(canvas);
    this.intersectionObserver=new IntersectionObserver(entries=>{this.visible=entries[0].isIntersecting;this.sync();}); this.intersectionObserver.observe(canvas);
    this.origin=performance.now(); this.resize(); this.sync();
  }
  setWeather(weather) {this.weather=weather;this.previous=[];this.lastFrame=-1;this.render(0);}
  setPresentationActive(value){this.presentationActive=value;if(value){this.previous=[];this.resize();this.sync();}else cancelAnimationFrame(this.raf);}
  setAnimated(value) {
    if(value&&!this.animated)this.origin=performance.now()-Math.max(0,this.lastFrame)*1000/MOTION.fps;
    this.animated=value;this.sync();
  }
  resize() {
    if(this.presentationActive===false)return;
    if(!this.ctx)return;
    const box=this.canvas.getBoundingClientRect(); if(!box.width||!box.height)return;
    const dpr=window.devicePixelRatio||1;
    const width=Math.round(box.width*dpr),height=Math.round(box.height*dpr);
    if(this.canvas.width===width&&this.canvas.height===height)return;
    this.canvas.width=width;this.canvas.height=height;this.ctx.imageSmoothingEnabled=false;
    const frame=Math.max(0,this.lastFrame);
    this.previous=[];this.lastFrame=frame;this.canvas.dataset.resolution=`${width}×${height}`;this.render(frame);
  }
  render(frame) {
    if(this.presentationActive===false)return;
    if(!this.ctx||!this.canvas.width||!this.canvas.height)return;
    const tile=this.canvas.width/MOTION.columns;
    const data=createWeatherFrame(this.weather,this.canvas.height/tile,frame);
    const inset=Math.max(1,Math.round(tile*.012));
    for(let index=0;index<data.pixels.length;index++) {
      const color=data.pixels[index];if(this.previous[index]===color)continue;
      const x=index%data.columns,y=Math.floor(index/data.columns);
      const x0=Math.round(x*tile),y0=Math.round(y*tile),x1=Math.round((x+1)*tile),y1=Math.round((y+1)*tile);
      this.ctx.fillStyle=mix(color,data.scene==='night'?'#17273d':'#546c78',.26);
      this.ctx.fillRect(x0,y0,x1-x0,y1-y0);
      this.ctx.fillStyle=color;this.ctx.fillRect(x0+inset,y0+inset,Math.max(0,x1-x0-inset*2),Math.max(0,y1-y0-inset*2));
    }
    this.previous=data.pixels;this.canvas.dataset.scene=data.scene;this.canvas.dataset.frame=String(frame);
  }
  sync() {
    cancelAnimationFrame(this.raf);
    if(this.presentationActive===false)return;
    if(this.reducedMotion.matches){this.render(0);return;}
    if(!this.animated){this.render(Math.max(0,this.lastFrame));return;}
    if(document.hidden||!this.visible)return;
    const loop=now=>{
      const frame=Math.floor((now-this.origin)*MOTION.fps/1000)%MOTION.frames;
      if(frame!==this.lastFrame){this.render(frame);this.lastFrame=frame;}
      this.raf=requestAnimationFrame(loop);
    };
    this.raf=requestAnimationFrame(loop);
  }
  destroy() {
    cancelAnimationFrame(this.raf);this.resizeObserver.disconnect();this.intersectionObserver.disconnect();
    document.removeEventListener('visibilitychange',this.onVisibility);this.reducedMotion.removeEventListener('change',this.onMotion);
    window.removeEventListener('resize',this.onResize);
  }
}
