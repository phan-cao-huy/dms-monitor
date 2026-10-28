import {esc, button, field, select, area, empty, date} from './ui.js?v=1.0.23';

export function emailView(state, panel){
  const t=(vi,en)=>state.alertLanguage==='en'?en:vi;
  const rows=state.emailChannels||[];
  return panel(t('Kênh email','Email channels'),t('Gửi cảnh báo của không gian đến email đã chọn. Chỉ Owner/Admin được quản lý.','Send workspace alerts to selected email recipients. Owner/Admin only.'),
    `<div class="panel-body"><p class="notice">${t('Chỉ gửi cảnh báo mới phát sinh sau khi tạo kênh. Mỗi cảnh báo thử gửi tối đa 3 lần; lịch sử gửi độc lập với thông báo điện thoại.','Only new alerts created after the channel are delivered. Up to 3 attempts per alert; delivery is independent of phone notifications.')}</p></div>`+
    (rows.length?`<div class="table-scroll"><table><thead><tr>${[t('Tên kênh','Channel'), 'SMTP',t('Người nhận','Recipients'),t('Trạng thái','Status'),t('Thao tác','Actions')].map(x=>`<th>${x}</th>`).join('')}</tr></thead><tbody>${rows.map(c=>`<tr><td><strong>${esc(c.name)}</strong><div class="muted text-xs">${c.config.language==='en'?'English':'Tiếng Việt'}</div></td><td>${esc(c.config.host)}:${esc(c.config.port)}<div class="muted text-xs">TLS · ${t('Mật khẩu đã lưu','Password saved')}</div></td><td>${c.config.recipients.map(esc).join('<br>')}</td><td><span class="pill ${c.enabled?'online':'paused'}">${c.enabled?t('Đang bật','Enabled'):t('Tạm tắt','Disabled')}</span></td><td><div class="row wrap">${button(t('Gửi thử','Send test'),'test-email','sm','',`data-id="${c.channel_id}"`)}${button(t('Chỉnh sửa','Edit'),'edit-email','sm','edit',`data-id="${c.channel_id}"`)}${button(t('Lịch sử gửi','Delivery history'),'email-history','sm','',`data-id="${c.channel_id}"`)}${button(t('Xóa','Delete'),'delete-email','sm danger','trash',`data-id="${c.channel_id}"`)}</div></td></tr>`).join('')}</tbody></table></div>`:empty(t('Chưa có kênh email','No email channels'),t('Thêm SMTP và người nhận để bắt đầu nhận cảnh báo.','Add SMTP and recipients to receive alerts.'),'bell')),
    button(t('Thêm kênh email','Add email channel'),'add-email','primary sm','plus'));
}

export function emailForm(channel, english=false){
  const t=(vi,en)=>english?en:vi,c=channel?.config||{};
  return `<p class="intro">${t('Dùng SMTP TLS: cổng 587 (STARTTLS) hoặc 465 (SSL). Nếu nhà cung cấp yêu cầu, dùng mật khẩu ứng dụng.','Use TLS SMTP: port 587 (STARTTLS) or 465 (SSL). Use an app password if required by your provider.')}</p><p class="intro"><a href="https://myaccount.google.com/apppasswords" target="_blank" rel="noopener noreferrer">Gmail · App passwords</a> · ${t('Bật xác minh 2 bước và tạo mật khẩu ứng dụng.','Enable 2-Step Verification and create an app password.')}</p><div class="form-grid">`+
    field(t('Tên kênh','Channel name'),'name','text',channel?.name||'','required maxlength="100"')+
    field(t('Máy chủ SMTP','SMTP host'),'host','text',c.host||'smtp.gmail.com','required maxlength="253" placeholder="smtp.example.com"')+
    select(t('Cổng và mã hóa','Port and encryption'),'port',[[587,'587 · STARTTLS'],[465,'465 · SSL/TLS']],c.port||587)+
    field(t('Tên đăng nhập SMTP','SMTP username'),'username','text',c.username||'','required maxlength="255" autocomplete="off"')+
    field(t(channel?'Mật khẩu mới (để trống để giữ nguyên)':'Mật khẩu SMTP','SMTP password (leave blank to keep existing)'),'password','password','',`${channel?'':'required'} maxlength="1024" autocomplete="new-password"`)+
    field(t('Email gửi','From email'),'from_email','email',c.from_email||'','required')+
    area(t('Email nhận (mỗi dòng một địa chỉ, tối đa 20)','Recipients (one per line, up to 20)'),'recipients',(c.recipients||[]).join('\n'),'required maxlength="6000"')+
    select(t('Ngôn ngữ email','Email language'),'language',[['vi','Tiếng Việt'],['en','English']],c.language||'vi')+
    select(t('Trạng thái','Status'),'enabled',[['true',t('Đang bật','Enabled')],['false',t('Tạm tắt','Disabled')]],String(channel?.enabled??true))+'</div>';
}

export function emailPayload(form){
  const password=String(form.get('password')||'');
  return {name:String(form.get('name')).trim(),enabled:form.get('enabled')==='true',config:{
    host:String(form.get('host')).trim(),port:Number(form.get('port')),username:String(form.get('username')).trim(),
    from_email:String(form.get('from_email')).trim(),recipients:[...new Set(String(form.get('recipients')).split(/[\n,;]+/).map(x=>x.trim()).filter(Boolean))],
    language:form.get('language'),...(password?{password}:{}),
  }};
}

export function emailHistory(rows,english=false){
  const t=(vi,en)=>english?en:vi;
  const errors={SMTP_AUTH_FAILED:t('SMTP từ chối đăng nhập','SMTP authentication failed'),SMTP_CONNECTION_FAILED:t('Không kết nối được SMTP','SMTP connection failed'),SMTP_DELIVERY_FAILED:t('Gửi thất bại, kiểm tra cấu hình SMTP','Delivery failed; check SMTP settings')};
  return rows.length?`<div class="table-scroll"><table><thead><tr><th>${t('Cảnh báo','Alert')}</th><th>${t('Trạng thái','Status')}</th><th>${t('Số lần thử','Attempts')}</th><th>${t('Chi tiết','Details')}</th></tr></thead><tbody>${rows.map(r=>`<tr><td>#${esc(r.alert_id)}</td><td>${r.status==='SENT'?t('Đã gửi','Sent'):r.attempts>=3?t('Thất bại','Failed'):t('Chờ thử lại','Retry pending')}</td><td>${esc(r.attempts)} / 3</td><td>${r.sent_at?esc(date(r.sent_at)):esc(errors[r.last_error]||r.last_error||'—')}</td></tr>`).join('')}</tbody></table></div>`:empty(t('Chưa có lượt gửi','No deliveries yet'),t('Lịch sử chỉ gồm cảnh báo tự động, không gồm email gửi thử.','History contains automatic alerts, not test emails.'),'bell');
}
