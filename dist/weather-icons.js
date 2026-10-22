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

export function weatherIconMarkup(key) {
  const line = path => `<path d="${path}"/>`;
  const star = (x, y, size = 5) => line(`M${x-size} ${y}H${x+size}M${x} ${y-size}V${y+size}`);
  const sun = (x = 86, y = 53, radius = 24) => {
    let result = `<circle cx="${x}" cy="${y}" r="${radius}"/>`;
    for (let ray = 0; ray < 12; ray++) {
      const angle = ray * Math.PI / 6;
      const point = r => `${x + Math.cos(angle)*r} ${y + Math.sin(angle)*r}`;
      result += line('M' + point(radius+9) + 'L' + point(radius+17));
    }
    return result;
  };
  const moon = '<path d="M100 12A39 39 0 1 0 120 79A33 33 0 0 1 100 12Z"/>';
  const smallMoon = `<g transform="translate(1 -2) scale(.57)">${moon}</g>`;
  const cloud = '<path fill="var(--paper)" d="M41 70H131C144 70 150 60 145 50C141 42 133 39 125 41C123 23 98 17 86 34C73 26 57 33 56 46C41 41 29 52 33 63C34 67 37 70 41 70Z"/>';
  let body;
  if (key === 'sunrise' || key === 'sunset') {
    body = '';
    for (let ray = 0; ray <= 12; ray++) {
      const angle = Math.PI + ray * Math.PI / 12;
      const point = r => `${86 + Math.cos(angle)*r} ${77 + Math.sin(angle)*r}`;
      body += line('M' + point(25) + 'L' + point(58) + 'M' + point(65) + 'L' + point(71));
    }
    body += line('M18 81H70M102 81H154');
    body += key === 'sunrise' ? line('M86 104V87M79 94L86 87L93 94') : line('M86 87V104M79 97L86 104L93 97');
  } else if (key === 'sun') body = sun();
  else if (key === 'moon') body = moon + star(134, 28) + star(37, 32, 3) + star(130, 85, 3);
  else if (key === 'unknown') body = line('M54 53H118') + '<circle cx="86" cy="53" r="37" stroke-dasharray="4 7"/>';
  else {
    const night = key.startsWith('night-');
    const type = key.replace(/^night-/, '');
    const sky = type === 'storm' ? '' : night ? smallMoon : type === 'partly' ? sun(59, 33, 17) : '';
    body = sky + cloud;
    if (type === 'rain') body += line('M60 80L53 94M90 80L83 94M120 80L113 94');
    if (type === 'storm') body += '<path fill="currentColor" stroke="none" d="M92 74L78 91H89L83 104L109 84H96L103 74Z"/>';
    if (type === 'snow') {
      for (const x of [56, 87, 118]) body += star(x, 91, 7) + line(`M${x-5} 86L${x+5} 96M${x-5} 96L${x+5} 86`);
    }
    if (type === 'fog') body = sky + line('M29 45H143M41 61H154M19 77H131M42 93H149');
    if (type === 'wind') body = sky + line('M20 43H109C128 43 128 18 111 18C104 18 100 22 100 28M34 61H139C156 61 156 84 142 84C136 84 133 81 133 76M19 80H100M49 95H111');
  }
  return `<g fill="none" stroke="currentColor" stroke-width="${key.endsWith('storm') ? 2.8 : 2.3}" stroke-linecap="butt" stroke-linejoin="round">${body}</g>`;
}
