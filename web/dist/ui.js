const paths = {
  workspace:'M3 3h7v18H3z M14 3h7v7h-7z M14 14h7v7h-7z',
  grid:'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z',
  monitor:'M3 4h18v13H3z M8 21h8 M12 17v4', server:'M4 3h16v7H4z M4 14h16v7H4z M7 6.5h.01 M7 17.5h.01 M11 6.5h6 M11 17.5h6',
  bell:'M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9 M10 21h4',
  users:'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M16 3a4 4 0 0 1 0 8 M22 21v-2a4 4 0 0 0-3-3.87 M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0',
  folder:'M3 7V4h6l3 3h9v13H3z', building:'M4 21V3h12v18 M16 9h4v12 M8 7h4 M8 11h4 M8 15h4 M2 21h20',
  activity:'M2 12h5l3-8 4 16 3-8h5', settings:'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8 M9 3h6l1 3 3 1 2 5-2 5-3 1-1 3H9l-1-3-3-1-2-5 2-5 3-1z',
  search:'M21 21l-5-5 M18 10.5a7.5 7.5 0 1 1-15 0 7.5 7.5 0 0 1 15 0', plus:'M12 5v14 M5 12h14', arrow:'M5 12h14 M13 6l6 6-6 6',
  chevron:'M9 5l7 7-7 7', chevronDown:'M6 9l6 6 6-6', refresh:'M20 7v5h-5 M4 17v-5h5 M5 7a8 8 0 0 1 13-2l2 2 M19 17a8 8 0 0 1-13 2l-2-2',
  download:'M12 3v12 M7 10l5 5 5-5 M4 16v5h16v-5', logout:'M9 4H4v16h5 M9 12h12 M17 8l4 4-4 4', moon:'M21 13a9 9 0 0 1-10-10 9 9 0 1 0 10 10',
  sun:'M12 2v2 M12 20v2 M2 12h2 M20 12h2 M5 5l1 1 M18 18l1 1 M5 19l1-1 M18 6l1-1 M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0',
  cpu:'M7 7h10v10H7z M9 1v4 M15 1v4 M9 19v4 M15 19v4 M1 9h4 M1 15h4 M19 9h4 M19 15h4 M10 10h4v4h-4z',
  memory:'M3 6h18v12H3z M7 18v3 M11 18v3 M15 18v3 M19 18v3 M7 10v4 M11 10v4 M15 10v4 M19 10v4',
  disk:'M6 3h12l3 11v7H3v-7z M3 14h18 M16 18h2', shield:'M12 2l8 4v6c0 5-8 10-8 10S4 17 4 12V6z M8 12l3 3 5-6',
  clock:'M12 7v6l4 2 M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0', check:'M5 12l4 4L19 6', close:'M6 6l12 12 M6 18L18 6',
  more:'M5 12h.01 M12 12h.01 M19 12h.01', edit:'M16 3l5 5-12 12-6 1 1-6z M14 5l5 5', trash:'M3 6h18 M8 6V3h8v3 M5 6l1 15h12l1-15 M10 10v7 M14 10v7',
  copy:'M9 9h12v12H9z M5 15H3V3h12v2', key:'M14 10l7-7 M18 3h3v3 M15 15a6 6 0 1 1-12 0 6 6 0 0 1 12 0',
  warning:'M12 3l10 18H2z M12 9v5 M12 17h.01', terminal:'M4 7l5 5-5 5 M12 17h8', play:'M7 4l14 8-14 8z', pause:'M7 4v16 M17 4v16',
  wifi:'M2 8a16 16 0 0 1 20 0 M5 12a11 11 0 0 1 14 0 M8 16a6 6 0 0 1 8 0 M12 20h.01', arrowUp:'M12 19V5 M6 11l6-6 6 6', arrowDown:'M12 5v14 M6 13l6 6 6-6',
  home:'M3 11l9-8 9 8 M5 9v12h14V9 M9 21v-7h6v7', menu:'M3 6h18 M3 12h18 M3 18h18', eye:'M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12 M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0', globe:'M3 12h18 M12 3c-6 5-6 13 0 18 6-5 6-13 0-18 M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0',
};
export const icon = (name, cls = '') => `<svg class="icon ${cls}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="${paths[name] || paths.monitor}"/></svg>`;
export const esc = (value = '') => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
export const number = value => value === null || value === undefined || !Number.isFinite(Number(value)) ? '—' : new Intl.NumberFormat('vi-VN', {maximumFractionDigits:1}).format(Number(value));
export const percent = value => value === null || value === undefined ? '—' : `${number(value)}%`;
export function date(value, options = {}) {
  if (!value) return 'Chưa có dữ liệu';
  const normalized = /(?:Z|[+-]\d\d:\d\d)$/.test(value) ? value : value + 'Z';
  const d = new Date(normalized);
  if (!Number.isFinite(d.getTime())) return '—';
  return new Intl.DateTimeFormat('vi-VN',{day:'2-digit',month:'2-digit',hour:'2-digit',minute:'2-digit',...options}).format(d);
}
export function ago(value) {
  if (!value) return 'Chưa kết nối';
  const d = new Date(/(?:Z|[+-]\d\d:\d\d)$/.test(value) ? value : value+'Z');
  const seconds = Math.max(0,(Date.now()-d.getTime())/1000);
  return !Number.isFinite(seconds) ? '—' : seconds<60 ? 'Vừa cập nhật' : seconds<3600 ? `${Math.floor(seconds/60)} phút trước` : seconds<86400 ? `${Math.floor(seconds/3600)} giờ trước` : `${Math.floor(seconds/86400)} ngày trước`;
}
const statusNames = {Online:'Trực tuyến',Offline:'Ngoại tuyến',Warning:'Cảnh báo',Paused:'Tạm dừng',Archived:'Đã lưu trữ',PENDING:'Đang chờ',EXECUTING:'Đang chạy',SUCCESS:'Thành công',FAILED:'Thất bại',TIMEOUT:'Hết thời gian'};
export const status = value => `<span class="pill ${esc(String(value || '').toLowerCase())}"><i class="dot"></i>${esc(statusNames[value] || value || 'Không rõ')}</span>`;
export const button = (text, action, kind = '', ico = '', attrs = '') => `<button type="button" class="btn ${kind}" data-action="${action}" ${attrs}>${ico?icon(ico):''}${text}</button>`;
export const empty = (title, description, ico = 'monitor', action = '') => `<div class="empty-state">${icon(ico)}<h3>${esc(title)}</h3><p>${esc(description)}</p>${action}</div>`;
export const meter = (value, label = '') => `<div class="table-meter ${value>=90?'hot':value>=75?'warm':''}"><span class="meter-track"><span class="meter-fill" style="width:${value==null?0:Math.max(0,Math.min(100,Number(value)||0))}%"></span></span><span>${label||percent(value)}</span></div>`;
export const field = (label, name, type = 'text', value = '', attrs = '') => `<label class="form-field">${label}<input name="${name}" type="${type}" value="${esc(value)}" ${attrs}></label>`;
export const select = (label, name, options, selected = '', attrs = '') => `<label class="form-field">${label}<select name="${name}" ${attrs}>${options.map(([value,text])=>`<option value="${esc(value)}" ${String(selected)===String(value)?'selected':''}>${esc(text)}</option>`).join('')}</select></label>`;
export const area = (label, name, value = '', attrs = '') => `<label class="form-field">${label}<textarea name="${name}" ${attrs}>${esc(value)}</textarea></label>`;
export const kv = (label, value) => `<div class="kv-row"><span>${esc(label)}</span><span>${esc(value ?? '—')}</span></div>`;
export const brand = (small = false) => `<a class="brand" href="#/" aria-label="DMS Monitor — Trang chủ"><span class="brand-mark">d</span><span>DMS Monitor</span>${small?'':'<span class="brand-version">WEB</span>'}</a>`;
export function spark(points, color = '#79a46d') {
  if (!points.length) return '';
  const path = points.map((v,i)=>`${i?'L':'M'}${i*66/Math.max(1,points.length-1)},${23-Math.max(0,Math.min(100,v))*0.2}`).join(' ');
  return `<svg class="kpi-spark" viewBox="0 0 66 24" fill="none" aria-hidden="true"><path d="${path}" stroke="${color}" stroke-width="1.6"/></svg>`;
}
export function chart(rows, {hourly=false, cpu=true, ram=true, series=null, unit='%', ceiling=100} = {}) {
  const configured=series||[[hourly?'cpu_avg':'cpu_usage','#27795c',cpu,'CPU'],[hourly?'ram_avg':'ram_usage','#6489c2',ram,'RAM']];
  const active=configured.filter(s=>s[2]);
  const valid=v=>v!==null&&v!==undefined&&Number.isFinite(Number(v));
  const normalized=[...rows].filter(r=>Number.isFinite(new Date(r.created_at||r.hour_bucket).getTime())).sort((a,b)=>new Date(a.created_at||a.hour_bucket)-new Date(b.created_at||b.hour_bucket));
  if(!normalized.length||!active.some(([key])=>normalized.some(r=>valid(r[key]))))return empty('Chưa có lịch sử hiệu năng','Agent chưa gửi số liệu cho tài nguyên này.','activity');
  const left=48,right=714,top=18,bottom=200;
  const start=new Date(normalized[0].created_at||normalized[0].hour_bucket).getTime();
  const end=new Date(normalized.at(-1).created_at||normalized.at(-1).hour_bucket).getTime();
  const max=ceiling??Math.max(1,...active.flatMap(([key])=>normalized.filter(r=>valid(r[key])).map(r=>Number(r[key]))))*1.15;
  const x=r=>end===start?(left+right)/2:left+(right-left)*(new Date(r.created_at||r.hour_bucket).getTime()-start)/(end-start);
  const y=v=>bottom-Math.max(0,Math.min(max,Number(v)))*(bottom-top)/max;
  const stats=active.map(([key,color,,label])=>{
    const vals=normalized.filter(r=>valid(r[key])).map(r=>Number(r[key]));
    return vals.length?'<div class="chart-series-summary" style="--series:'+color+'"><strong>'+esc(label)+'</strong><span>Thấp nhất <b>'+number(Math.min(...vals))+esc(unit)+'</b></span><span>Trung bình <b>'+number(vals.reduce((a,b)=>a+b,0)/vals.length)+esc(unit)+'</b></span><span>Cao nhất <b>'+number(Math.max(...vals))+esc(unit)+'</b></span></div>':'';
  }).join('');
  const lines=active.map(([key,color,,label])=>{
    const segments=[];let segment=[];
    for(const r of normalized){if(valid(r[key]))segment.push(r);else if(segment.length){segments.push(segment);segment=[];}}
    if(segment.length)segments.push(segment);
    return segments.map(seg=>{
      const path=seg.map((r,i)=>(i?'L':'M')+x(r)+','+y(r[key])).join(' ');
      return '<path d="'+path+' L'+x(seg.at(-1))+','+bottom+' L'+x(seg[0])+','+bottom+' Z" fill="'+color+'" opacity=".09"/><path d="'+path+'" fill="none" stroke="'+color+'" stroke-width="2.8" stroke-linejoin="round"/>'+seg.map(r=>'<circle tabindex="0" cx="'+x(r)+'" cy="'+y(r[key])+'" r="'+(seg.length===1?4:3)+'" fill="'+color+'" stroke="var(--surface)" stroke-width="1.5"><title>'+esc(date(r.created_at||r.hour_bucket))+' · '+esc(label)+': '+number(r[key])+esc(unit)+'</title></circle>').join('');
    }).join('');
  }).join('');
  return '<div class="chart-summaries">'+stats+'</div><svg class="telemetry-chart" viewBox="0 0 740 240" role="img" aria-label="'+esc(active.map(s=>s[3]).join(', '))+' theo thời gian">'+[0,.25,.5,.75,1].map(v=>'<line x1="'+left+'" x2="'+right+'" y1="'+y(v*max)+'" y2="'+y(v*max)+'" stroke="var(--border)" stroke-dasharray="4 6"/><text x="3" y="'+(y(v*max)+4)+'" fill="var(--muted)" font-size="11">'+number(v*max)+(unit==='%'?'%':'')+'</text>').join('')+lines+[0,.25,.5,.75,1].map((v)=>{
    const stamp=new Date(start+(end-start)*v).toISOString();
    return '<text x="'+(left+(right-left)*v)+'" y="228" text-anchor="'+(v===0?'start':v===1?'end':'middle')+'" fill="var(--muted)" font-size="11">'+esc(date(stamp,{day:hourly?'2-digit':undefined,month:hourly?'2-digit':undefined}))+'</text>';
  }).join('')+'</svg><div class="chart-caption">'+normalized.length+' mẫu dữ liệu · '+(hourly?'Trung bình theo giờ':'Mẫu gốc từ agent')+' · Di chuột vào điểm để xem chi tiết</div>';
}
export function csvDownload(filename, rows) {
  const cell = v => {let s=String(v??'');if (/^[=+@\-\t\r]/.test(s)) s="'"+s;return '"'+s.replaceAll('"','""')+'"';};
  const blob = new Blob(['\ufeff'+rows.map(r=>r.map(cell).join(',')).join('\r\n')],{type:'text/csv;charset=utf-8'});
  const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download=filename;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
}

export function alertCopy(alert,rule={},name='',language='vi'){
  if(alert.message_en){const text=language==='en'?alert.message_en:alert.message;const [title,...body]=text.split('\n');return {title,body:body.join(' · ')};}
  const en=language==='en',kind=rule.metric_type||(/^RAM/.test(alert.message||'')?'RAM':/mất kết nối/.test(alert.message||'')?'OFFLINE':'');
  const value=number(alert.metric_value),threshold=number(alert.threshold_value??rule.threshold),op=({'<=':'≤','>=':'≥'})[rule.condition]||rule.condition||'≥';
  const labels={CPU:'CPU',RAM:'RAM',GPU:'GPU (VGA)',DISK:en?'Disk':'Ổ đĩa',NET_IN:en?'Download':'Mạng tải xuống',NET_OUT:en?'Upload':'Mạng tải lên'};
  if(kind==='OFFLINE')return {title:(en?'Device offline · ':'Máy mất kết nối · ')+name,body:en?'No heartbeat for '+value+' min. Threshold: '+threshold+' min.':'Không nhận tín hiệu trong '+value+' phút. Ngưỡng: '+threshold+' phút.'};
  if(labels[kind]){
    const unit=['CPU','RAM','GPU','DISK'].includes(kind)?'%':' KB/s';
    const relation=en?({'≥':'at or above','>':'above','≤':'at or below','<':'below'})[op]:({'≥':'đạt/vượt ngưỡng','>':'vượt ngưỡng','≤':'bằng/thấp hơn ngưỡng','<':'thấp hơn ngưỡng'})[op];
    return {title:(en?labels[kind]+' alert · ':'Cảnh báo '+labels[kind]+' · ')+name,
      body:(en?labels[kind]+' is at ':labels[kind]+' đang ở mức ')+value+unit+', '+(relation||op)+' '+threshold+unit+(en?' for ':' trong ')+(rule.duration_minutes||1)+(en?' min.':' phút.')};
  }
  if(['SERVICE','PROCESS_MISSING'].includes(kind))return {title:(en?'Service/process stopped · ':'Dịch vụ/tiến trình ngừng chạy · ')+name,body:rule.service_name||rule.process_name||alert.message};
  return {title:(en?'Device alert · ':'Cảnh báo thiết bị · ')+name,body:alert.message||'—'};
}

export function processHeat(value,warm=20,hot=50){
  if(value==null||!Number.isFinite(Number(value)))return 'heat-unknown';
  return Number(value)>=hot?'heat-high':Number(value)>=warm?'heat-warm':'heat-low';
}

export function commandFeedback(command){
  if(command.status==='SUCCESS')return command.command_type==='KILL_PROCESS'?(command.parameters?.process_name?'Đã dừng tất cả tiến trình '+command.parameters.process_name+' trên máy.':'Đã dừng tiến trình PID '+(command.parameters?.pid??'—')+' thành công.'):command.command_type==='REBOOT'?'Agent đã xác nhận lệnh khởi động lại.':'Lệnh đã hoàn tất.';
  if(command.status==='FAILED')return 'Lệnh thất bại: '+(command.error_message||command.output||'Agent báo lỗi.');
  if(command.status==='TIMEOUT')return 'Lệnh hết thời gian chờ; chưa xác nhận thực hiện thành công.';
  return 'Đang thực thi lệnh...';
}
