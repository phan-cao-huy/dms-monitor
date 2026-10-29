export const endTaskScopeOptions=[['application','Buộc tắt toàn bộ ứng dụng (End task)'],['pid','Chỉ buộc tắt PID đã chọn và tiến trình con']];
export function killParameters(pid,name,scope='application'){
  if(scope==='pid'){
    if(!Number.isInteger(pid)||pid<=0)throw new Error('PID không hợp lệ.');
    return {pid};
  }
  if(scope!=='application'||typeof name!=='string'||!name.trim()||/[*/?\\\x00-\x1f]/.test(name))throw new Error('Tên chương trình phải là tên chính xác, không có đường dẫn hoặc ký tự đại diện.');
  return {process_name:name.trim()};
}
