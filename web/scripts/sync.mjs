import {mkdir, readdir, copyFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
const source=fileURLToPath(new URL('../dist/',import.meta.url));
const target=fileURLToPath(new URL('../../backend/static/web/',import.meta.url));
await mkdir(target,{recursive:true});
for(const name of await readdir(source)) {
  if(!/\.(?:html|css|js|svg)$/.test(name))throw new Error('Unexpected distribution file: '+name);
  await copyFile(path.join(source,name),path.join(target,name));
}
console.log('DMS web synchronized to backend/static/web');
