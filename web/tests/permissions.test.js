import {test} from 'node:test';
import assert from 'node:assert/strict';
import {canControlDevice} from '../dist/permissions.js';

test('workspace Viewer cannot control even with group Manager permission',()=>{
  assert.equal(canControlDevice('Viewer',3,'Manager'),false);
  assert.equal(canControlDevice('Viewer',null,null),false);
  assert.equal(canControlDevice(null,3,'Manager'),false);
});
test('owner/admin control is preserved; manager requires group Manager access',()=>{
  assert.equal(canControlDevice('Owner',3,'Viewer'),true);
  assert.equal(canControlDevice('Admin',3,'Viewer'),true);
  assert.equal(canControlDevice('Manager',3,'Manager'),true);
  assert.equal(canControlDevice('Manager',3,'Viewer'),false);
  assert.equal(canControlDevice('Manager',3,null),false);
  assert.equal(canControlDevice('Manager',null,null),true);
});
