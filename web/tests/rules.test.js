import {test} from 'node:test';
import assert from 'node:assert/strict';
import {effectiveRules,duplicateRule} from '../dist/rules.js';

const device={device_id:1,group_id:2,org_id:3};
const make=(id,scope,more={})=>({rule_id:id,metric_type:'RAM',is_active:true,...scope,...more});
test('device beats group beats default; rule order and old duplicate do not change the winner',()=>{
  const rules=[make(1,{workspace_id:3}),make(2,{group_id:2}),make(3,{device_id:1}),make(4,{device_id:1}),make(5,{device_id:99})];
  assert.deepEqual(effectiveRules(rules,device).map(r=>r.rule_id),[4]);
  assert.deepEqual(effectiveRules([...rules].reverse(),device).map(r=>r.rule_id),[4]);
  assert.deepEqual(effectiveRules(rules.slice(0,2),device).map(r=>r.rule_id),[2]);
  assert.deepEqual(effectiveRules(rules.slice(0,1),device).map(r=>r.rule_id),[1]);
});
test('inactive and other workspace rules do not apply',()=>{
  assert.deepEqual(effectiveRules([make(1,{workspace_id:4}),make(2,{device_id:1},{is_active:false})],device),[]);
});
test('only an active same-scope component conflicts, regardless of threshold',()=>{
  const rules=[make(1,{device_id:1})];
  assert.equal(duplicateRule(rules,make(2,{device_id:1},{threshold:50})).rule_id,1);
  assert.equal(duplicateRule(rules,make(2,{device_id:2})),undefined);
  assert.equal(duplicateRule(rules,make(1,{device_id:1})),undefined);
  assert.equal(duplicateRule(rules,make(2,{group_id:2})),undefined);
});
test('named service and process rules remain independent',()=>{
  const one=make(1,{device_id:1},{metric_type:'SERVICE',service_name:'Spooler'});
  const two=make(2,{device_id:1},{metric_type:'SERVICE',service_name:'DMS-Agent'});
  assert.equal(effectiveRules([one,two],device).length,2);
  assert.equal(duplicateRule([one],two),undefined);
});
