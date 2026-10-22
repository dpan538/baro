const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '../dist');
const designBoard = path.resolve(__dirname, '../output/Weather-Pixel-System.html');
const gallery = path.resolve(__dirname, '../output/BARO-gallery.html');
const types = {'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.svg':'image/svg+xml','.ttf':'font/ttf','.woff2':'font/woff2','.txt':'text/plain; charset=utf-8'};
const server = http.createServer((req, res) => {
  let file;
  try {const pathname=decodeURIComponent(new URL(req.url,'http://localhost').pathname);file=pathname==='/design-system.html'?designBoard:pathname==='/gallery.html'?gallery:path.resolve(root,'.'+pathname);}
  catch {res.writeHead(400).end();return;}
  if (file !== root && file !== designBoard && file !== gallery && !file.startsWith(root + path.sep)) {res.writeHead(403).end();return;}
  if (file === root) file = path.join(root, 'index.html');
  fs.stat(file, (error, stat) => {
    if (error || !stat.isFile()) {res.writeHead(404).end();return;}
    res.writeHead(200, {'Content-Type':types[path.extname(file)] || 'application/octet-stream','Cache-Control':'no-store'});
    fs.createReadStream(file).pipe(res);
  });
});
server.listen(Number(process.env.BARO_PORT || 4175), '127.0.0.1', () => console.log(`BARO: http://127.0.0.1:${server.address().port}`));
server.on('error', error => {console.error(error.message);process.exitCode = 1;});
