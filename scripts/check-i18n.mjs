import assert from 'node:assert/strict';
import { LANGUAGES, LABELS, labelPair, conditionPair, uvText, localWindow, localSunset, sceneName } from '../dist/i18n.js';
import { WEATHER_PRESETS, WEATHER_CODE_GROUPS } from '../dist/weather-art.js';
import { adaptOpenMeteo } from '../dist/open-meteo.js';
import fs from 'node:fs/promises';
for (const language of LANGUAGES) {
  for (const key of Object.keys(LABELS)) {
    const label = labelPair(key, language);
    assert(label.primary);
    assert.equal(Boolean(label.secondary), language === 'dual');
  }
  for (const [key, weather] of Object.entries(WEATHER_PRESETS)) {
    assert(sceneName(key, language));
    assert(!conditionPair(weather.condition, language).primary.includes('暂缺'));
  }
}
const response = JSON.parse(await fs.readFile(new URL('../verification/open-meteo-response.json', import.meta.url), 'utf8'));
for (const code of Object.values(WEATHER_CODE_GROUPS).flat()) {
  const fixture = structuredClone(response);
  fixture.current.weather_code = code;
  assert(!conditionPair(adaptOpenMeteo(fixture).condition, 'zh').primary.includes('暂缺'), 'Missing Chinese weather code: ' + code);
}
assert.deepEqual(localSunset('6:07', 'pm', 'dual'), { time: '18:07', period: '' });
assert.deepEqual(localSunset('12:07', 'am', 'zh'), { time: '00:07', period: '' });
assert.deepEqual(localSunset('—', '', 'en'), { time: '—', period: '' });
assert.equal(localWindow('23–00h', 'zh'), '23–00时');
assert.equal(uvText('Very high', 'dual'), '很高 / Very high');
console.log('Verified Chinese and English language modes, eleven demo weather descriptions, all supported WMO translations, localized sunset times and hourly labels.');
