import { resolveWeatherScene, resolveTimeContext } from './weather-art.js';

export function resolveWeatherIcon(weather) {
  const scene = resolveWeatherScene(weather);
  if (scene === 'unknown') return 'unknown';
  if (scene === 'dawn') return 'sunrise';
  if (scene === 'sunset') return 'sunset';
  if (scene === 'night') return 'moon';
  if (scene === 'clear') return 'sun';
  const night = weather.isDay === false && !['dawn', 'dusk'].includes(resolveTimeContext(weather).phase);
  return (night ? 'night-' : '') + scene;
}

// Solar-event diagrams share a viewBox and stroke, but retain distinct silhouettes.
export function solarIconMarkup(event) {
  const line=path=>`<path d="${path}"/>`;
  let body='';
  if(event==='sunrise'){
    for(let i=0;i<=12;i++){
      const angle=Math.PI+i*Math.PI/12;
      const point=r=>[86+Math.cos(angle)*r,98+Math.sin(angle)*r].join(' ');
      body+=line('M'+point(29)+'L'+point(65)+'M'+point(73)+'L'+point(80));
    }
  }else if(event==='sunset'){
    // Three inner solar rings and a regular broken outer ring share sunrise's centre.
    for(const radius of [54,41,28]){
      body+=line(`M${86-radius} 98A${radius} ${radius} 0 0 1 ${86+radius} 98`);
    }
    for(let i=0;i<=12;i++){
      const angle=Math.PI+i*Math.PI/12;
      const point=r=>[86+Math.cos(angle)*r,98+Math.sin(angle)*r].join(' ');
      body+=line('M'+point(73)+'L'+point(80));
    }
  }else body=line('M36 86H136M74 64H98');
  return `<g fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="butt" stroke-linejoin="round">${body}</g>`;
}

export function weatherIconMarkup(key) {
  const line = path => `<path d="${path}"/>`;
  // The same 32-unit canvas as the gear and navigation icons. Keep secondary
  // marks sparse so they retain the same optical weight at small sizes.
  const sun = (x = 16, y = 16, radius = 6) => {
    let result = `<circle cx="${x}" cy="${y}" r="${radius}"/>`;
    for (let ray = 0; ray < 8; ray++) {
      const angle = ray * Math.PI / 4;
      const point = r => `${x + Math.cos(angle)*r} ${y + Math.sin(angle)*r}`;
      result += line('M' + point(radius+3) + 'L' + point(radius+6));
    }
    return result;
  };
  const moon = line('M20 4A12 12 0 1 0 28 22A12 12 0 0 1 20 4Z');
  const smallMoon = line('M12 3A8 8 0 1 0 19 15A8 8 0 0 1 12 3Z');
  const cloud = '<path fill="var(--paper)" d="M8 21H25A5 5 0 0 0 26 11A8 8 0 0 0 11 9A6 6 0 0 0 8 21Z"/>';
  let body;
  if (key === 'sunrise' || key === 'sunset') {
    body = line('M3 21H29M9 17A7 7 0 0 1 23 17M16 3V6M5 7L7 9M27 7L25 9');
    body += key === 'sunrise' ? line('M16 30V25M13 28L16 25L19 28') : line('M16 25V30M13 27L16 30L19 27');
  } else if (key === 'sun') body = sun();
  else if (key === 'moon') body = moon;
  else if (key === 'unknown') body = '<circle cx="16" cy="16" r="12"/>' + line('M11 16H21');
  else {
    const night = key.startsWith('night-');
    const type = key.replace(/^night-/, '');
    const sky = type === 'storm' ? '' : night ? smallMoon : type === 'partly' ? sun(10, 10, 4) : '';
    body = sky + cloud;
    if (type === 'rain') body += line('M10 25L8 29M18 25L16 29M26 25L24 29');
    if (type === 'storm') body += line('M18 24L14 27H18L15 31');
    if (type === 'snow') {
      for (const x of [11, 23]) body += line(`M${x} 25V31M${x-2.6} 26.5L${x+2.6} 29.5M${x-2.6} 29.5L${x+2.6} 26.5`);
    }
    if (type === 'fog') body = line('M5 9H27M3 16H25M7 23H29');
    if (type === 'wind') body = line('M3 12H21A4 4 0 1 0 17 8M3 20H25A4 4 0 1 1 21 24');
  }
  return `<g fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="butt" stroke-linejoin="round">${body}</g>`;
}
