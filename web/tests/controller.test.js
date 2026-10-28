// Offline controller/template test. No browser, rendering engine or network is used.
import {test} from 'node:test';
import assert from 'node:assert/strict';

test('workspace routes render and demo mode cannot write to the real API',async()=>{
  const nodes=new Map();const events=new Map();const windowEvents=new Map();
  const node=()=>({innerHTML:'',children:[],isConnected:true,append(){},remove(){},focus(){},querySelector(){return null;}});
  for(const id of ['#app','#modal-root','#toasts','#modal-error'])nodes.set(id,node());
  const storage=()=>{const data=new Map();return{getItem:key=>data.get(key)||null,setItem:(key,value)=>data.set(key,value),removeItem:key=>data.delete(key)};};
  globalThis.localStorage=storage();globalThis.sessionStorage=storage();globalThis.location={hash:'#/'};
  globalThis.document={querySelector:s=>nodes.get(s)||null,getElementById:()=>null,activeElement:null,hidden:true,documentElement:{dataset:{}},addEventListener:(name,fn)=>events.set(name,fn),createElement:node};
  globalThis.window={addEventListener:(name,fn)=>windowEvents.set(name,fn),scrollTo(){}};
  globalThis.setInterval=()=>0;globalThis.clearInterval=()=>{};globalThis.setTimeout=fn=>{fn();return 0;};
  let requests=0;globalThis.fetch=async()=>{requests++;throw new Error('No network permitted in this test');};
  await import('../dist/app.js');
  assert.match(nodes.get('#app').innerHTML,/Trong <em>một tầm nhìn/);
  const click=async(action,data={})=>events.get('click')({preventDefault(){},target:{closest:selector=>selector==='[data-action]'?{dataset:{action,...data},disabled:false}:null}});
  await click('demo');await windowEvents.get('hashchange')();
  for(const [path,title] of [['/overview','Tổng quan hệ thống'],['/devices','Danh sách thiết bị'],['/devices/1','DESIGN-WS-01'],['/alerts','Lịch sử cảnh báo'],['/organizations','Nhóm thiết bị'],['/activity','Lịch sử lệnh'],['/settings','Cài đặt & tài khoản']]){
    location.hash='#'+path;await windowEvents.get('hashchange')();
    const html=nodes.get('#app').innerHTML;
    assert.ok(html.includes(title),`Missing ${title} on ${path}`);
    assert.doesNotMatch(html,/NaN|undefined/);
  }
  location.hash='#/devices/1';await windowEvents.get('hashchange')();
  assert.match(nodes.get('#app').innerHTML,/data-action="delete-device"/);
  for(const text of ['D:','E:','Còn trống','Tiến trình nổi bật','Thông tin máy'])assert.ok(nodes.get('#app').innerHTML.includes(text),'Missing overview content: '+text);
  assert.doesNotMatch(nodes.get('#app').innerHTML,/Disk \(C:\)/);

  await click('detail-tab',{value:'processes'});
  assert.match(nodes.get('#app').innerHTML,/process-sort/);
  assert.match(nodes.get('#app').innerHTML,/heat-low/);
  await click('process-sort',{value:'ram'});
  await click('process-select',{id:'1832'});
  assert.match(nodes.get('#app').innerHTML,/process-detail/);
  await click('detail-tab',{value:'metrics'});
  for(const text of ['Phân tích tài nguyên','Lưu lượng mạng','Thấp nhất','Cao nhất'])assert.ok(nodes.get('#app').innerHTML.includes(text));
  assert.doesNotMatch(nodes.get('#app').innerHTML,/NaN|undefined/);
  location.hash='#/alerts';await windowEvents.get('hashchange')();
  await click('alert-tab',{value:'rules'});
  assert.match(nodes.get('#app').innerHTML,/data-action="edit-rule"/);
  await click('edit-rule',{id:'1'});
  assert.equal(requests,0);
  await click('add-device');assert.equal(requests,0);
  await click('group-members',{id:'1'});
  const submit={disabled:false,isConnected:true};
  const originalFormData=globalThis.FormData;
  globalThis.FormData=class{get(name){return name==='user_id'?'2':'Manager';}};
  try{
    await events.get('submit')({preventDefault(){},target:{id:'modal-form',querySelector:()=>submit}});
    assert.equal(requests,0);
    assert.match(nodes.get('#modal-error').innerHTML,/minh họa/);
  }finally{globalThis.FormData=originalFormData;}
});
