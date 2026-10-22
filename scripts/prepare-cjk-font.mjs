import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const source = path.resolve(root, '../design/assets/misans');
const destination = path.join(root, 'dist/assets/fonts/MiSans');
const manifest = JSON.parse(await fs.readFile(path.join(root, 'verification/misans-font-index.json'), 'utf8'));
const files = ['dist/index.html', 'dist/app.js', 'dist/settings.js', 'dist/device-services.js', 'dist/i18n.js', 'dist/weather-art.js', 'dist/open-meteo.js', 'scripts/build-gallery.mjs'];
const text = (await Promise.all(files.map(file => fs.readFile(path.join(root, file), 'utf8')))).join('');
const characters = [...new Set(text.match(/[\u3400-\u9fff]/g))];
function contains(range, character) {
  const code = character.codePointAt(0);
  return range.split(',').some(item => {
    const [low, high = low] = item.replace('U+', '').split('-').map(value => Number.parseInt(value, 16));
    return code >= low && code <= high;
  });
}
const chunks = new Set();
for (const character of characters) {
  const chunk = manifest.chunks.find(chunk => contains(chunk.range, character));
  if (!chunk) throw new Error('Missing original MiSans glyph chunk: ' + character);
  chunks.add(chunk);
}
await fs.mkdir(destination, { recursive: true });
for (const chunk of chunks) {
  try { await fs.copyFile(path.join(source, chunk.file), path.join(destination, chunk.file)); }
  catch (error) { if (error.code !== 'ENOENT') throw error; await fs.access(path.join(destination, chunk.file)); }
}
try { await fs.copyFile(path.join(source, 'MiSans-License.pdf'), path.join(destination, 'MiSans-License.pdf')); }
catch (error) { if (error.code !== 'ENOENT') throw error; await fs.access(path.join(destination, 'MiSans-License.pdf')); }
await fs.writeFile(path.join(destination, 'sources.json'), JSON.stringify({ ...manifest, chunks: [...chunks], note: 'Original vendor WOFF2 bytes copied without changing or resubsetting fonts; used within BARO.' }, null, 2));
const css = [...chunks].map(chunk => `@font-face{font-family:MiSans;src:url('MiSans/${chunk.file}') format('woff2');font-style:normal;font-weight:100 900;font-display:swap;unicode-range:${chunk.range};}`).join('\n');
await fs.writeFile(path.join(root, 'dist/assets/fonts/misans.css'), css + '\n');
console.log(`Prepared ${chunks.size} unmodified MiSans font chunks for ${characters.length} UI characters.`);
