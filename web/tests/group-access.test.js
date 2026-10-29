import {test} from 'node:test';
import assert from 'node:assert/strict';
import {groupEditorState} from '../dist/group-access.js';

test('existing Manager is preselected rather than overwritten by Viewer default',()=>{
  const org=[{user_id:1,role:'Viewer'},{user_id:2,role:'Manager'}],rows=[{user_id:1,role:'Manager'}];
  const existing=groupEditorState(org,rows,1);
  assert.equal(existing.role,'Manager');assert.equal(existing.unchanged,true);
  assert.equal(existing.effective,'Chỉ xem');
  const changed=groupEditorState(org,rows,1,'Viewer');
  assert.equal(changed.unchanged,false);assert.equal(changed.effective,'Chỉ xem');
  assert.equal(groupEditorState(org,rows,2).role,'Viewer');
  assert.equal(groupEditorState(org,rows,2,'Manager').effective,'Quản lý thiết bị');
});
