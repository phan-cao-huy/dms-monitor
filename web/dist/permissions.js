export function canControlDevice(workspaceRole,groupId,groupRole){
  return ['Owner','Admin'].includes(workspaceRole)||(workspaceRole==='Manager'&&(groupId==null||groupRole==='Manager'));
}
