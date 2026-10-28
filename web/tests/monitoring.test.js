import {test} from 'node:test';
import assert from 'node:assert/strict';
import {healthPanel,connectionsView,eventsView,checksView,checkHistory,checkPayload,checkDetail} from '../dist/monitoring.js';

const panel=(...args)=>args.join('');

test('disk health and connections preserve unknown state and escape data',()=>{
  const d={status:'Offline',static_info:{disks_health:[{name:'<script>',healthy:null}],connections:[{pid:1,process:'<img>',laddr:'a',raddr:'b',status:'LISTEN'}]}};
  const html=healthPanel(d,panel)+connectionsView(d,panel);
  assert.match(html,/Không xác định/);
  assert.match(html,/Không đọc được/);
  assert.match(html,/ngoại tuyến/);
  assert.doesNotMatch(html,/<script>|<img>/);
});

test('Windows event rows are escaped and cursor navigation is rendered',()=>{
  const html=eventsView({deviceEvents:[{level:'ERROR',message:'<script>event</script>',source:'<img>',log_name:'System',event_id:1,record_id:2}],eventNext:2},{static_info:{}},panel);
  assert.match(html,/Xem cũ hơn/);
  assert.doesNotMatch(html,/<script>|<img>/);
});

test('service check views distinguish no result, pending failure and confirmed incident',()=>{
  const rows=[{check_id:1,name:'<script>',enabled:true,latest:null},{check_id:2,name:'Working',enabled:true,latest:{ok:false,detail:'TIMEOUT'}},{check_id:3,name:'Down',enabled:true,incident_open:true,latest:{ok:false,detail:'HTTP_500'}}];
  const html=checksView({checks:rows},panel,panel);
  assert.match(html,/Chờ kiểm tra/);
  assert.match(html,/Đang theo dõi lỗi/);
  assert.match(html,/Có sự cố/);
  assert.doesNotMatch(html,/<script>/);
  assert.equal(checkDetail('SSL_DAYS:10'),'Chứng chỉ còn 10 ngày');
  assert.doesNotMatch(checkHistory({results:[],incidents:[]}),/undefined|NaN/);
});

test('service check payload uses numeric limits and explicit enabled setting',()=>{
  const payload=checkPayload(new Map(Object.entries({name:' Site ',type:'HTTP',target:' https://example.com ',enabled:'false',interval_seconds:'30',timeout_seconds:'5',failure_threshold:'3'})));
  assert.equal(payload.interval_seconds,30);
  assert.equal(payload.enabled,false);
  assert.equal(payload.target,'https://example.com');
});
