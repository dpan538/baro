export const LANGUAGES = Object.freeze(['zh', 'en']);
export const LABELS = Object.freeze({
  title: ['今日天气', 'TODAY’S WEATHER'],
  settings: ['设置', 'SETTINGS'], scenes: ['天气场景', 'WEATHER SCENES'],
  appearance: ['界面与视觉', 'APPEARANCE'], units: ['气象单位', 'UNITS'],
  systemAbout: ['系统与关于', 'ABOUT'], temperatureUnit: ['温度单位', 'TEMPERATURE'],
  windUnit: ['风速单位', 'WIND SPEED'], location: ['自动定位', 'LOCATION'],
  version: ['软件版本', 'VERSION'], cache: ['数据缓存', 'CACHE'], more: ['了解更多', 'MORE'], language: ['界面语言', 'LANGUAGE'],
  pixelStyle: ['像素画风', 'PIXEL ART'], animation: ['天气动画', 'ANIMATION'],
  feelsLike: ['体感温度', 'FEELS LIKE'], windSpeed: ['风速', 'WIND SPEED'],
  uvIndex: ['紫外线指数', 'UV INDEX'], humidity: ['相对湿度', 'HUMIDITY'],
  precipitation: ['降水概率', 'PRECIP CHANCE'], visibility: ['能见度', 'VISIBILITY'],
  cloudCover: ['云量', 'CLOUD COVER'], sunset: ['日落时间', 'SUNSET'],
  dawn: ['清晨', 'DAWN'], morning: ['早晨', 'MORNING'], midday: ['正午', 'MIDDAY'],
  afternoon: ['午后', 'AFTERNOON'], dusk: ['傍晚', 'DUSK'], night: ['夜间', 'NIGHT'],
  day: ['白天', 'DAYTIME'], unknown: ['时刻暂缺', 'TIME UNAVAILABLE'],
  sunrise: ['日出', 'SUNRISE'], nextSunrise: ['下次日出', 'NEXT SUNRISE']
});
const CONDITIONS = Object.freeze({
  'Mostly clear': '晴间多云', 'Clear skies': '晴朗', 'Mainly clear': '大部晴朗',
  'Partly cloudy': '局部多云', 'Overcast': '阴天', 'Rain': '降雨',
  'Thunderstorm': '雷暴', 'Snow': '降雪', 'Fog': '雾', 'Windy': '大风',
  'Clear night': '晴夜', 'Golden hour': '日落时分', 'Rime fog': '雾凇',
  'Light drizzle': '毛毛雨', 'Drizzle': '毛毛雨', 'Dense drizzle': '强毛毛雨',
  'Freezing drizzle': '冻毛毛雨', 'Dense freezing drizzle': '强冻毛毛雨',
  'Light rain': '小雨', 'Heavy rain': '大雨', 'Freezing rain': '冻雨', 'Heavy freezing rain': '强冻雨',
  'Light snow': '小雪', 'Heavy snow': '大雪', 'Snow grains': '米雪',
  'Light showers': '小阵雨', 'Showers': '阵雨', 'Heavy showers': '强阵雨',
  'Snow showers': '阵雪', 'Heavy snow showers': '强阵雪',
  'Storm / slight hail': '雷暴伴小冰雹', 'Heavy storm': '强雷暴', 'Storm / heavy hail': '雷暴伴大冰雹',
  'Weather unavailable': '天气数据暂缺'
});
const UV = Object.freeze({ Low: '低', Moderate: '中等', High: '高', 'Very high': '很高', Extreme: '极高' });
const SCENE_NAMES = Object.freeze({
  dawn: ['晨光', 'Dawn'],
  clear: ['晴天', 'Clear'], partly: ['晴间多云', 'Partly cloudy'],
  overcast: ['阴天', 'Overcast'], rain: ['雨天', 'Rain'], storm: ['雷暴', 'Thunderstorm'],
  snow: ['雪天', 'Snow'], fog: ['雾天', 'Fog'], wind: ['大风', 'Wind'],
  night: ['晴夜', 'Night'], sunset: ['日落', 'Sunset'], unknown: ['天气暂缺', 'Unavailable']
});
export function labelPair(key, language) {
  const [zh, en] = LABELS[key];
  return { primary: language === 'en' ? en : zh, secondary: language === 'dual' ? en : '' };
}
export function conditionPair(value, language) {
  const zh = CONDITIONS[value] ?? '天气数据暂缺';
  return { primary: language === 'en' ? value : zh, secondary: language === 'dual' ? value : '' };
}
export function uvText(value, language) {
  if (!value) return '';
  return language === 'en' ? value : language === 'dual' ? `${UV[value] ?? '—'} / ${value}` : UV[value] ?? '—';
}
export function sceneName(scene, language) {
  const [zh, en] = SCENE_NAMES[scene] ?? SCENE_NAMES.unknown;
  return language === 'en' ? en : language === 'dual' ? `${zh} / ${en}` : zh;
}
export function localWindow(value, language) {
  return value ? language === 'en' ? value : value.replace(/h$/, '时') : '';
}
export function localSunset(value, period, language) {
  if (!value || value === '—' || language === 'en') return { time: value ?? '—', period: period ?? '' };
  const [rawHour, minute] = value.split(':');
  const hour = Number(rawHour) % 12 + (period === 'pm' ? 12 : 0);
  return { time: `${String(hour).padStart(2, '0')}:${minute}`, period: '' };
}
