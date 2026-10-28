import {test} from 'node:test';
import assert from 'node:assert/strict';
import {esc,chart,percent,date,ago,alertCopy,processHeat,commandFeedback} from '../dist/ui.js';
import {demoData} from '../dist/demo.js';
test('server text is escaped before rendering in tables and modals',()=>{
  assert.equal(esc('<img src=x onerror="steal()">'), '&lt;img src=x onerror=&quot;steal()&quot;&gt;');
});
test('missing telemetry stays missing instead of displaying fake zero usage',()=>{
  assert.equal(percent(null),'—');assert.equal(percent(undefined),'—');
  assert.match(chart([]),/Chưa có lịch sử/);assert.equal(ago(null),'Chưa kết nối');
});
test('raw and hourly metric contracts generate valid charts',()=>{
  const rows=demoData().metrics;
  assert.match(chart(rows),/<svg/);assert.doesNotMatch(chart(rows),/NaN|undefined/);
  const hourly=rows.map(r=>({hour_bucket:r.created_at,cpu_avg:r.cpu_usage,ram_avg:r.ram_usage}));
  assert.doesNotMatch(chart(hourly,{hourly:true}),/NaN|undefined/);assert.notEqual(date(rows[0].created_at),'—');
});

test('resource charts preserve gaps and support network scales',()=>{
  const rows=[{created_at:'2026-10-26T01:00:00Z',net_in_kbps:1200},{created_at:'2026-10-26T01:01:00Z',net_in_kbps:null},{created_at:'2026-10-26T01:02:00Z',net_in_kbps:2500}];
  const result=chart(rows,{series:[['net_in_kbps','#608a84',true,'Tải xuống']],unit:' KB/s',ceiling:null});
  assert.match(result,/Cao nhất/);assert.match(result,/2.500/);assert.doesNotMatch(result,/NaN|undefined/);
  assert.equal((result.match(/stroke-width="2.8"/g)||[]).length,2);
  assert.match(chart(rows,{series:[['gpu_usage_pct','#608a84',true,'GPU']]}),/Chưa có lịch sử/);
});

test('alert copy identifies resource, measured value and rule in Vietnamese and English',()=>{
  const alert={metric_value:88,threshold_value:50.3};
  const rule={metric_type:'RAM',condition:'>=',duration_minutes:2};
  assert.match(alertCopy(alert,rule,'PC-01','vi').title,/Cảnh báo RAM · PC-01/);
  assert.match(alertCopy(alert,rule,'PC-01','en').body,/RAM is at 88%/);
  assert.match(alertCopy(alert,rule,'PC-01','en').body,/2 min/);
});

test('process heat uses actual metric levels and preserves unknown GPU',()=>{
  assert.equal(processHeat(null,40,80),'heat-unknown');
  assert.equal(processHeat(0,40,80),'heat-low');
  assert.equal(processHeat(50,40,80),'heat-warm');
  assert.equal(processHeat(90,40,80),'heat-high');
});

test('kill feedback follows actual server completion and preserves failure reasons',()=>{
  const c={command_id:12,command_type:'KILL_PROCESS',parameters:{pid:45},status:'PENDING'};
  assert.match(commandFeedback(c),/Đang thực thi lệnh/);
  assert.match(commandFeedback({...c,status:'SUCCESS'}),/PID 45 thành công/);
  assert.match(commandFeedback({...c,parameters:{process_name:'chrome.exe'},status:'SUCCESS'}),/tất cả tiến trình chrome.exe/);
  assert.match(commandFeedback({...c,status:'FAILED',error_message:'Access denied'}),/Access denied/);
  assert.match(commandFeedback({...c,status:'TIMEOUT'}),/chưa xác nhận/);
});
