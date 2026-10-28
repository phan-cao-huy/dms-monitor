import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createClient,API_BASE} from '../dist/api.js';

const response=(data,status=200)=>new Response(JSON.stringify(data),{status,headers:{'Content-Type':'application/json'}});
const storage=()=>{const data=new Map();return {getItem:k=>data.get(k),setItem:(k,v)=>data.set(k,v),removeItem:k=>data.delete(k)};};

test('concurrent unauthorized responses rotate the refresh token once',async()=>{
  let refreshes=0,oldCalls=0;const urls=[];
  const client=createClient({storage:storage(),fetcher:async(url,options)=>{
    urls.push(url);assert.equal(options.credentials,'omit');assert.equal(options.redirect,'error');
    if(url.endsWith('auth/refresh')){refreshes++;assert.equal(JSON.parse(options.body).refresh_token,'old-refresh');await new Promise(r=>setTimeout(r,10));return response({access_token:'new-access',refresh_token:'new-refresh'});}
    if(options.headers.Authorization==='Bearer old-access'){oldCalls++;return response({detail:'expired'},401);}
    assert.equal(options.headers.Authorization,'Bearer new-access');return response({ok:true});
  }});
  client.setSession({access_token:'old-access',refresh_token:'old-refresh'});
  const results=await Promise.all([client.request('orgs'),client.request('auth/me')]);
  assert.equal(refreshes,1);assert.equal(oldCalls,2);assert.deepEqual(results,[{ok:true},{ok:true}]);
  assert.equal(client.session.refresh_token,'new-refresh');assert.ok(urls.every(u=>u.startsWith(API_BASE)));
});

test('a refresh response cannot restore a session after logout',async()=>{
  let allow;let requested;const pending=new Promise(r=>allow=r);const started=new Promise(r=>requested=r);
  const client=createClient({storage:storage(),fetcher:async url=>{
    if(url.endsWith('auth/refresh')){requested();await pending;return response({access_token:'unexpected',refresh_token:'unexpected'});}
    return response({detail:'expired'},401);
  }});
  client.setSession({access_token:'access',refresh_token:'refresh'});
  const req=client.request('orgs');await started;client.clearSession();allow();
  await assert.rejects(req,/thay đổi/);assert.equal(client.session,null);
});

test('invalid refresh revokes local session and informs the application',async()=>{
  let unauthorized=0;const client=createClient({storage:storage(),onUnauthorized:()=>unauthorized++,fetcher:async()=>response({detail:'expired'},401)});
  client.setSession({access_token:'expired',refresh_token:'expired'});
  await assert.rejects(client.request('orgs'),/hết hạn/);assert.equal(client.session,null);assert.equal(unauthorized,1);
});

test('write failures are not silently retried or reported as successful',async()=>{
  let calls=0;const client=createClient({fetcher:async()=>{calls++;return response({detail:'Permission denied'},403);}});
  await assert.rejects(client.request('devices/1/commands',{method:'POST',body:{command_type:'REBOOT'}}),/Permission denied/);
  assert.equal(calls,1);
});

test('rejects external URLs and handles no-content and validation errors',async()=>{
  let calls=0;const client=createClient({fetcher:async()=>{calls++;return new Response(null,{status:204});}});
  await assert.rejects(client.request('https://other.example/'),/không hợp lệ/);assert.equal(calls,0);
  assert.equal(await client.request('auth/logout',{method:'POST',auth:false,body:{refresh_token:'x'}}),null);
  const invalid=createClient({fetcher:async()=>response({detail:[{loc:['body','password'],msg:'too short'}]},422)});
  await assert.rejects(invalid.request('auth/login',{auth:false}),/password: too short/);
});
