import fs from 'node:fs/promises';
import { WEATHER_PRESETS, resolveTimeContext, PIXEL_TONE } from '../dist/weather-art.js';
import { FINE_MOTION } from '../dist/weather-shader.js';
await import('./build-gallery.mjs');
await fs.copyFile(new URL('../output/BARO-gallery.html',import.meta.url),new URL('../output/Weather-Pixel-System.html',import.meta.url));
await fs.writeFile(new URL('../output/BARO-Pixel-System.json',import.meta.url),JSON.stringify({version:11,renderer:'WebGL2 / GLSL ES 3.0',logicalGrid:'Fine: native canvas density up to 1536 columns, bounded by GPU texture size; block: 96 columns; rows follow panel aspect',presentation:'CSS size × DPR; fine: LINEAR copy and distance-field edge coverage; block: NEAREST copy',dithering:'Fine: Bayer 4×4 with 31-tone ramps and reduced noise; block: four-step flat bands without ordered dithering',tone:PIXEL_TONE,motion:{...FINE_MOTION,seconds:8},fallback:'Legacy Canvas 2D 24-column renderer when WebGL2 is unavailable',scenarios:Object.entries(WEATHER_PRESETS).map(([scene,weather])=>({scene,...weather,time:resolveTimeContext(weather)}))},null,2));
console.log('Updated fine pixel design board and scenario specification.');
