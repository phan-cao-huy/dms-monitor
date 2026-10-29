import {canControlDevice} from './permissions.js';

export function groupEditorState(orgMembers,assignments,userId,pendingRole){
  const member=orgMembers.find(m=>String(m.user_id)===String(userId));
  const existing=assignments.find(m=>String(m.user_id)===String(userId));
  const role=pendingRole??existing?.role??'Viewer';
  const controls=canControlDevice(member?.role,1,role);
  return {role,stored:existing?.role??null,unchanged:!!existing&&existing.role===role,
    effective:controls?'Quản lý thiết bị':'Chỉ xem',
    explanation:member?.role==='Viewer'?'Vai trò không gian là Viewer: luôn chỉ xem, dù nhóm lưu Manager. Muốn điều khiển cần vai trò Manager trong không gian và trong nhóm.':'Quyền thực tế kết hợp vai trò trong không gian và trong nhóm.'};
}
