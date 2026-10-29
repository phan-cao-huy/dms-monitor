import {test} from 'node:test';
import assert from 'node:assert/strict';
import {connectedCount,isDeviceOnline,matchesDeviceStatus} from '../dist/device-status.js';

test('two warning PCs are counted as two online devices and included by online filter',()=>{
  const devices=[{status:'Warning',name:'pc01'},{status:'Warning',name:'pc03'},{status:'Offline'}];
  assert.equal(connectedCount(devices),2);
  assert.equal(devices.filter(d=>matchesDeviceStatus(d,'Online')).length,2);
  assert.equal(devices.filter(d=>matchesDeviceStatus(d,'Warning')).length,2);
});
test('disconnected, paused, archived and unknown devices are not counted as online',()=>{
  for(const status of ['Offline','Paused','Archived',undefined,'Other'])assert.equal(isDeviceOnline({status}),false);
  assert.equal(connectedCount([{status:'Online'},{status:'Warning'}]),2);
  assert.equal(matchesDeviceStatus({status:'Offline'},'Offline'),true);
  assert.equal(matchesDeviceStatus({status:'Warning'},'Offline'),false);
});
