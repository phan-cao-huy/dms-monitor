import {esc, number, date, button, field, select, empty, chart} from './ui.js?v=1.0.32';

export const metricOptions=[['cpu_usage','CPU (%)'],['ram_usage','RAM (%)'],['disk_usage','Dung lượng ổ đĩa (%)'],['net_in_kbps','Mạng tải xuống (KB/s)'],['net_out_kbps','Mạng tải lên (KB/s)'],['gpu_usage_pct','GPU (%)'],['cpu_temp_c','Nhiệt độ CPU (°C)']];
export const rangeOptions=[['1h','1 giờ'],['6h','6 giờ'],['24h','24 giờ'],['3d','3 ngày'],['7d','7 ngày'],['30d','30 ngày'],['90d','90 ngày']];
export const rangeHours=value=>({'1h':1,'6h':6,'24h':24,'3d':72,'7d':168,'30d':720,'90d':2160}[value]||1);
export const resolutionText=value=>({raw:'Mẫu gốc',minute:'Tầng phút',hourly:'Tầng giờ'}[value]||'Đang tải');

export function labelOptions(devices){
  return [...new Set(devices.flatMap(d=>Object.entries(d.labels||{}).map(([key,value])=>key+':'+value)))].sort().map(value=>[value,value]);
}

export function matchesLabel(device,filter){
  if(!filter||filter==='all')return true;
  const split=filter.indexOf(':');
  return split>=0&&device.labels?.[filter.slice(0,split)]===filter.slice(split+1);
}

export function labelChips(labels){
  return Object.entries(labels||{}).map(([k,v])=>`<span class="pill label-chip">${esc(k)}: ${esc(v)}</span>`).join('');
}

export function labelsPanel(d,canEdit,panel){
  const labels=Object.entries(d.labels||{});
  return panel('Nhãn thiết bị','Tối đa 20 nhãn · Dùng để lọc và so sánh tài nguyên theo môi trường, vị trí hoặc vai trò.',
    labels.length?`<div class="table-scroll"><table><thead><tr><th>Khóa</th><th>Giá trị</th><th></th></tr></thead><tbody>${labels.map(([key,value])=>`<tr><td class="mono">${esc(key)}</td><td>${esc(value)}</td><td>${canEdit?button('Sửa','edit-label','sm','edit',`data-key="${esc(key)}"`)+button('Xóa','delete-label','sm danger','trash',`data-key="${esc(key)}"`):''}</td></tr>`).join('')}</tbody></table></div>`:empty('Chưa có nhãn','Ví dụ env:production, location:hanoi, role:database.','grid'),
    canEdit?button('Thêm nhãn','add-label','sm primary','plus',labels.length>=20?'disabled':''):'');
}

export function normalizeLabelKey(key){return String(key).trim().toLowerCase();}
export function labelInputError(key,value){
  if(!/^[a-z][a-z0-9_]{0,31}$/.test(normalizeLabelKey(key)))return 'Khóa phải bắt đầu bằng a–z; chỉ dùng chữ, số, dấu _ và tối đa 32 ký tự.';
  if([...value].length>64||/[\x00-\x1f\x7f]/.test(value))return 'Giá trị tối đa 64 ký tự, không dùng xuống dòng hoặc ký tự điều khiển.';
  return '';
}
export function labelForm(key='',value=''){
  return '<div class="label-editor">'+field('Khóa nhãn','key','text',key,`required maxlength="32" pattern="[a-z][a-z0-9_]{0,31}" autocapitalize="none" ${key?'readonly':''} placeholder="env"`)+
    '<p class="muted text-xs">Bắt đầu bằng a–z; dùng chữ, số, dấu _. Tối đa 32 ký tự. Chữ hoa tự chuyển thành chữ thường.</p>'+
    field('Giá trị (tối đa 64 ký tự)','value','text',value,'placeholder="production"')+
    '<p data-label-error role="status" class="text-xs"></p></div>';
}

export function analyticsView(state,panel,heading){
  const filter=state.analysisFilter||{metric:'cpu_usage',agg:'avg',group_by:'device',range:'24h',label:'all',step:''};
  const keys=[...new Set(state.devices.flatMap(d=>Object.keys(d.labels||{})))].sort();
  const form=`<form id="analysis-form" class="analysis-form">${select('Tài nguyên','metric',metricOptions,filter.metric)}${select('Phép tính','agg',[['avg','Trung bình có trọng số'],['min','Thấp nhất'],['max','Cao nhất']],filter.agg)}${select('Gom theo','group_by',[['device','Thiết bị'],['group','Nhóm thiết bị'],...keys.map(k=>['label:'+k,'Nhãn: '+k])],filter.group_by)}${select('Khoảng thời gian','range',rangeOptions,filter.range)}${select('Lọc nhãn','label',[['all','Tất cả nhãn'],...labelOptions(state.devices)],filter.label)}${select('Bước biểu đồ','step',[['','Tự động (tối đa 500 điểm)'],['60','1 phút'],['300','5 phút'],['3600','1 giờ']],String(filter.step||''))}<button class="btn primary" type="submit">${state.analysisBusy?'Đang truy vấn…':'So sánh dữ liệu'}</button></form>`;
  let body='';
  const data=state.analysisData;
  if(!data)body=empty('Chọn tài nguyên để so sánh','Dữ liệu được giới hạn theo quyền truy cập thiết bị và nhóm của bạn.','activity');
  else if(!data.series.length)body=empty('Không có số liệu trong lựa chọn này','Đổi bộ lọc hoặc khoảng thời gian. Lịch sử GPU/nhiệt độ chỉ có từ khi server bắt đầu lưu các mẫu này.','activity');
  else {
    const colors=['#27795c','#6489c2','#b58b45','#9674ac','#c95a53','#608a84','#9b7c61','#6e8f47','#4f8ca5','#b66f86','#7d83ab','#918348'];
    const shown=data.series.slice(0,12),timestamps=[...new Set(shown.flatMap(s=>s.points.map(p=>p.timestamp)))].sort();
    const values=new Map(timestamps.map(timestamp=>[timestamp,{created_at:timestamp}]));
    shown.forEach((s,i)=>s.points.forEach(p=>{values.get(p.timestamp)['s'+i]=p.value;}));
    const unit=data.metric==='cpu_temp_c'?' °C':data.metric.includes('kbps')?' KB/s':'%';
    const ceiling=unit==='%'?100:null;
    body=`<div class="notice">${resolutionText(data.resolution)} · Bước thực tế ${data.step_seconds} giây · ${esc(data.agg)} · ${data.series.length} chuỗi dữ liệu${data.series.length>12?' · Đang vẽ 12 chuỗi đầu, hãy lọc nhãn để thu hẹp':''}</div>`+
      chart(fillTimeline([...values.values()],data.from,data.to,data.step_seconds),{series:shown.map((s,i)=>['s'+i,colors[i],true,s.name]),unit,ceiling,showSummary:false,resolution:data.resolution,stepSeconds:data.step_seconds})+
      `<div class="table-scroll"><table><thead><tr><th>Chuỗi dữ liệu</th><th>Giá trị gần nhất có dữ liệu</th><th>Thời điểm</th><th>Số điểm</th></tr></thead><tbody>${data.series.map((s,i)=>{const latest=s.points.filter(p=>p.value!=null).at(-1);return `<tr><td><span style="color:${colors[i%colors.length]}">●</span> ${esc(s.name)}</td><td>${latest?number(latest.value)+unit:'Không có dữ liệu đủ để tính'}</td><td>${latest?date(latest.timestamp):'—'}</td><td>${s.points.length}</td></tr>`;}).join('')}</tbody></table></div>`;
  }
  return heading('Phân tích tài nguyên','So sánh thiết bị, nhóm và nhãn trên cùng một khoảng thời gian.')+
    panel('Chọn dữ liệu','Bước quá nhỏ sẽ tự tăng để giữ tối đa 500 điểm mỗi chuỗi.',`<div class="panel-body">${form}</div>`)+
    `<div class="gap-top">${panel('Kết quả so sánh','Giá trị thiếu được giữ trống; chỉ gộp các thiết bị bạn được phép xem.',`<div class="panel-body">${body}</div>`)}</div>`;
}


export function fillTimeline(items,start,end,step){
  const first=Date.parse(start),last=Date.parse(end),seconds=Number(step);
  if(!Number.isFinite(first)||!Number.isFinite(last)||!(seconds>0)||last<=first)return items;
  const count=Math.ceil((last-first)/(seconds*1000));
  if(count>500)return items;
  const values=new Map(items.map(r=>[Date.parse(r.created_at),r]));
  return Array.from({length:count},(_,i)=>{const stamp=first+i*seconds*1000;return values.get(stamp)||{created_at:new Date(stamp).toISOString()};});
}
