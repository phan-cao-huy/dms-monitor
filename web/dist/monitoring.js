import {SERVER} from './api.js?v=1.0.32';
import {esc, number, date, ago, button, empty, field, select, kv} from './ui.js?v=1.0.32';

export function healthPanel(d,panel){
  const info=d.static_info||{},rows=info.disks_health;
  const stale=['Offline','Paused'].includes(d.status)||(info.t2_collected_at && Date.now()-Date.parse(info.t2_collected_at)>120000);
  return panel('Sức khỏe ổ đĩa vật lý','Windows Storage / SMART · Không phải mức dung lượng đã dùng',`<div class="panel-body">${stale?'<p class="notice">Mẫu dữ liệu đã cũ, thiết bị ngoại tuyến hoặc tạm dừng. Đây là dữ liệu lần gần nhất.</p>':''}${rows?.length?`<div class="partition-grid">${rows.map(r=>`<article class="partition-card"><strong>${esc(r.name)}</strong><p><span class="pill ${r.healthy===false?'offline':r.healthy===true?'online':'paused'}">${r.healthy===false?'Có cảnh báo / lỗi':r.healthy===true?'Bình thường':'Không xác định'}</span></p>${kv('Nhiệt độ',r.temp_c==null?'Không đọc được':number(r.temp_c)+' °C')}${kv('Độ hao mòn',r.wear_pct==null?'Không đọc được':number(r.wear_pct)+'%')}${kv('Nguồn',r.source)}</article>`).join('')}</div>`:empty('Chưa đọc được sức khỏe ổ đĩa','Cần Agent 1.3.0 và driver/phần cứng hỗ trợ. Không có dữ liệu không có nghĩa ổ đĩa bị lỗi.','server')}<p class="muted text-xs">Mẫu gần nhất: ${info.t2_collected_at?date(info.t2_collected_at):'Chưa có dữ liệu'}</p><a class="text-link" href="${SERVER}/static/dms-agent.exe">Tải Agent Windows 1.3.0</a><p class="muted text-xs">Thay file Agent và khởi động lại dịch vụ trên máy tính cần theo dõi. Giữ nguyên config.json.</p></div>`);
}

export function connectionsView(d,panel){
  const rows=d.static_info?.connections;
  return panel('Kết nối mạng theo tiến trình','Tối đa 50 kết nối ESTABLISHED / LISTEN · Không đo băng thông từng tiến trình',`<div class="panel-body"><p class="notice">${['Offline','Paused'].includes(d.status)?'Dữ liệu cũ do thiết bị ngoại tuyến hoặc tạm dừng. ':''}Mẫu gần nhất: ${d.static_info?.t2_collected_at?date(d.static_info.t2_collected_at):'Chưa có dữ liệu'}</p></div>`+(rows?.length?`<div class="table-scroll"><table><thead><tr><th>Tiến trình</th><th>PID</th><th>Địa chỉ máy</th><th>Địa chỉ đích</th><th>Trạng thái</th></tr></thead><tbody>${rows.map(r=>`<tr><td><strong>${esc(r.process||'Không đọc được tên')}</strong></td><td>${esc(r.pid)}</td><td class="mono">${esc(r.laddr)}</td><td class="mono">${esc(r.raddr)}</td><td><span class="pill ${r.status==='ESTABLISHED'?'online':'paused'}">${esc(r.status)}</span></td></tr>`).join('')}</tbody></table></div>`:empty(rows?'Không có kết nối trong mẫu này':'Chưa có dữ liệu kết nối','Cần Agent 1.3.0; quyền hệ thống có thể giới hạn dữ liệu đọc được.','activity')));
}

export function eventsView(state,d,panel){
  const levels={ERROR:'Lỗi',CRITICAL:'Nghiêm trọng',AUDIT_FAILURE:'Đăng nhập thất bại'};
  const rows=state.deviceEvents||[],status=d.static_info?.event_log_status;
  return panel('Nhật ký sự kiện Windows','System / Application: Error, Critical · Security: đăng nhập thất bại 4625',
    `<div class="panel-body"><div class="row wrap">${select('Lọc mức độ','event_level',[['','Tất cả'],...Object.entries(levels)],state.eventLevel||'')}${button('Về mới nhất','events-latest','sm','refresh')}</div><p class="muted text-sm">${status?Object.entries(status).map(([k,v])=>esc(k)+': '+(v==='OK'?'Đọc được':v==='ACCESS_DENIED'?'Thiếu quyền':'Không đọc được')).join(' · '):'Cần cập nhật Agent 1.3.0. Agent bắt đầu từ sự kiện mới, không tải lại nhật ký cũ.'}</p></div>`+
    (rows.length?`<div class="table-scroll"><table><thead><tr><th>Mức độ</th><th>Thời điểm sự kiện</th><th>Nguồn / ID</th><th>Nội dung</th></tr></thead><tbody>${rows.map(r=>`<tr><td><span class="pill ${r.level==='CRITICAL'?'offline':'warning'}">${esc(levels[r.level]||r.level)}</span></td><td>${date(r.occurred_at)}</td><td><strong>${esc(r.source)}</strong><div class="muted text-xs">${esc(r.log_name)} · ${esc(r.event_id)} · Record ${esc(r.record_id)}</div></td><td><details><summary>${esc(r.message.slice(0,120))}</summary><p class="event-message">${esc(r.message)}</p></details></td></tr>`).join('')}</tbody></table></div>`:empty('Chưa có sự kiện trong bộ lọc','Chỉ lưu sự kiện mới khi bật theo dõi; nhật ký được giữ 7 ngày theo mặc định.','activity'))+
    (state.eventNext?`<div class="panel-body">${button('Xem cũ hơn','events-older','sm','','data-before="'+esc(state.eventNext)+'"')}</div>`:''));
}

export function checkDetail(code){
  if(!code)return 'Chưa kiểm tra';
  if(code.startsWith('HTTP_'))return 'HTTP '+code.slice(5);
  if(code.startsWith('SSL_DAYS:'))return 'Chứng chỉ còn '+code.split(':')[1]+' ngày';
  return {PING_OK:'Ping thành công',PING_FAILED:'Không nhận phản hồi Ping',TCP_OK:'Kết nối TCP thành công',TIMEOUT:'Quá thời gian chờ',CONNECTION_FAILED:'Không kết nối được hoặc phản hồi không hợp lệ',TLS_VALIDATION_FAILED:'Chứng chỉ HTTPS không hợp lệ / hết hạn',TARGET_BLOCKED:'Địa chỉ bị chặn bởi chính sách mạng',TOO_MANY_REDIRECTS:'Chuyển hướng quá 3 lần',PING_UNAVAILABLE:'Server chưa hỗ trợ Ping'}[code]||code;
}

export function checksView(state,panel,heading){
  const rows=state.checks||[];
  return heading('Kiểm tra dịch vụ','Kiểm tra từ server DMS: website/API, cổng TCP, Ping và hạn chứng chỉ HTTPS.',button('Thêm phép kiểm tra','add-check','primary','plus'))+
    panel('Dịch vụ đang theo dõi','Sự cố được mở sau N lần thất bại liên tiếp; chứng chỉ còn ≤14 ngày được cảnh báo ngay.',
      `<div class="panel-body"><p class="notice">Kiểm tra từ vị trí server DMS, không phải từ máy Agent. Địa chỉ Internet được phép; mạng riêng chỉ dùng khi quản trị server khai báo dải mạng cho phép.</p></div>`+
      (rows.length?`<div class="table-scroll"><table><thead><tr><th>Dịch vụ</th><th>Kết quả</th><th>Độ trễ</th><th>Chu kỳ / lỗi</th><th>Cập nhật</th><th></th></tr></thead><tbody>${rows.map(r=>`<tr><td><strong>${esc(r.name)}</strong><div class="muted text-xs">${esc(r.type)} · ${esc(r.target)}</div></td><td><span class="pill ${!r.enabled?'paused':r.incident_open?'offline':r.latest?.ok?'online':'warning'}">${!r.enabled?'Tạm tắt':r.incident_open?'Có sự cố':r.latest?.ok?'Bình thường':r.latest?'Đang theo dõi lỗi':'Chờ kiểm tra'}</span><div class="text-xs muted">${esc(checkDetail(r.latest?.detail))}</div></td><td>${r.latest?.latency_ms!=null?number(r.latest.latency_ms)+' ms':'—'}</td><td>${esc(r.interval_seconds)} giây<div class="muted text-xs">${esc(r.consecutive_failures)} / ${esc(r.failure_threshold)} lần lỗi liên tiếp</div></td><td>${r.last_checked_at?ago(r.last_checked_at):'Chưa chạy'}</td><td><div class="row wrap">${button('Chạy ngay','run-check','sm','refresh',`data-id="${r.check_id}"`)}${button('Lịch sử','check-history','sm','',`data-id="${r.check_id}"`)}${button('Sửa','edit-check','sm','edit',`data-id="${r.check_id}"`)}${button('Xóa','delete-check','sm danger','trash',`data-id="${r.check_id}"`)}</div></td></tr>`).join('')}</tbody></table></div>`:empty('Chưa có phép kiểm tra','Thêm địa chỉ website, máy chủ hoặc cổng dịch vụ để bắt đầu.','activity')));
}

export function checkForm(r={}){
  return '<div class="form-grid">'+field('Tên dịch vụ','name','text',r.name||'','required maxlength="100"')+
    select('Loại kiểm tra','type',[['HTTP','Website / API (HTTP)'],['SSL','Hạn chứng chỉ HTTPS'],['TCP','Cổng TCP'],['PING','Ping']],r.type||'HTTP')+
    field('Đích: URL cho HTTP/SSL; host:port cho TCP; hostname/IP cho Ping','target','text',r.target||'','required maxlength="2048" placeholder="https://example.com/health"')+
    field('Chu kỳ (giây, tối thiểu 30)','interval_seconds','number',r.interval_seconds||60,'required min="30" max="86400"')+
    field('Thời gian chờ (giây, tối đa 5)','timeout_seconds','number',r.timeout_seconds||5,'required min="1" max="5"')+
    field('Mở sự cố sau số lần lỗi liên tiếp','failure_threshold','number',r.failure_threshold||3,'required min="1" max="20"')+
    select('Trạng thái','enabled',[['true','Đang bật'],['false','Tạm tắt']],String(r.enabled??true))+'</div><p class="muted text-sm">Gửi email sự cố qua các kênh đã bật trong Cảnh báo → Email. Không lưu nội dung phản hồi của địa chỉ được kiểm tra.</p>';
}

export function checkPayload(f){
  return {name:String(f.get('name')).trim(),type:f.get('type'),target:String(f.get('target')).trim(),enabled:f.get('enabled')==='true',
    interval_seconds:Number(f.get('interval_seconds')),timeout_seconds:Number(f.get('timeout_seconds')),failure_threshold:Number(f.get('failure_threshold'))};
}

export function checkHistory(data){
  return `<h3>Sự cố gần đây</h3>`+(data.incidents.length?data.incidents.map(i=>`<div class="notice ${i.resolved_at?'':'error'}"><strong>${i.resolved_at?'Đã phục hồi':'Đang xảy ra'}</strong> · ${esc(checkDetail(i.detail))}<p>${date(i.opened_at)}${i.resolved_at?' → '+date(i.resolved_at):''}</p></div>`).join(''):'<p class="muted">Chưa có sự cố.</p>')+
    '<h3>Kết quả gần đây</h3>'+(data.results.length?`<div class="table-scroll"><table><thead><tr><th>Thời điểm</th><th>Kết quả</th><th>Độ trễ</th></tr></thead><tbody>${data.results.map(r=>`<tr><td>${date(r.checked_at)}</td><td>${r.ok?'✓':'!'} ${esc(checkDetail(r.detail))}</td><td>${number(r.latency_ms)} ms</td></tr>`).join('')}</tbody></table></div>`:'<p class="muted">Chưa có kết quả.</p>');
}
