import {test} from 'node:test';
import assert from 'node:assert/strict';
import {endTaskScopeOptions,killParameters} from '../dist/process-actions.js';
test('End task defaults to application name without a child PID overriding it',()=>{
  assert.equal(endTaskScopeOptions[0][0],'application');
  assert.deepEqual(killParameters(123,'test.exe'),{process_name:'test.exe'});
  assert.deepEqual(killParameters(123,'test.exe','pid'),{pid:123});
});
test('End task rejects ambiguous scope or wildcard/path targets',()=>{
  for(const name of ['','*.exe','../test.exe','C:\\test.exe'])assert.throws(()=>killParameters(123,name));
  assert.throws(()=>killParameters(0,'test.exe','pid'));
  assert.throws(()=>killParameters(123,'test.exe','other'));
});
