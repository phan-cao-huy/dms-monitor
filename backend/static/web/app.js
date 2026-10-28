const WEB_VERSION='1.0.20';
import {API_BASE, SERVER, createClient, ApiError} from './api.js?v=1.0.20';
import {icon, esc, number, percent, date, ago, status, button, empty, meter, field, select, area, kv, brand, spark, chart, csvDownload, alertCopy, processHeat, commandFeedback} from './ui.js?v=1.0.20';
import {demoData} from './demo.js?v=1.0.20';

const $ = (selector, root=document) => root.querySelector(selector);
const root = $('#app');
const modalRoot = $('#modal-root');
const demo = demoData();
const state = {
  alertLanguage:localStorage.getItem("dms.alertLanguage")||"vi", demo:false, user:null, orgs:[], orgId:null, groups:[], devices:[], details:{}, alerts:[], rules:[], members:[], invitations:[], metrics:[], commands:[],
  selectedDevice:null, detailTab:'overview', range:'1h', search:'', statusFilter:'all', groupFilter:'all', page:1, alertFilter:'open', orgTab:'groups',
  loading:false, error:'', connection:null, lastUpdated:null, refreshBusy:false, version:null, theme:localStorage.getItem('dms.theme') || 'light', chartCpu:true, chartRam:true,
};
const pendingCommands=new Map();
let loadGeneration=0;
let chartGeneration=0;
let modalHandler=null;
let pollTimer=null;
let mobileNavOpen=false;
let logoutBusy=false;
const api = createClient({storage:sessionStorage,onUnauthorized:()=>{ state.user=null; loadGeneration++; navigate('/login'); toast('Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại.','error'); }});
const sendRequest=api.request.bind(api);
api.request=(path,options={})=>state.demo&&!['GET','HEAD'].includes(options.method||'GET')
  ?Promise.reject(new ApiError('Chế độ minh họa không ghi dữ liệu lên server.')):sendRequest(path,options);
const route = () => (location.hash.slice(1).split('?')[0] || '/');
const org = () => state.orgs.find(o=>String(o.org_id)===String(state.orgId));
const admin = () => ['Owner','Admin'].includes(org()?.role);
const manager = () => ['Owner','Admin','Manager'].includes(org()?.role);
const currentDevice = () => state.details[state.selectedDevice] || state.devices.find(d=>String(d.device_id)===String(state.selectedDevice));
const deviceName = id => state.devices.find(d=>String(d.device_id)===String(id))?.device_name || `Thiết bị #${id}`;
const initials = (name='') => name.trim().split(/\s+/).slice(-2).map(s=>s[0]).join('').toUpperCase() || 'DM';
const safeNumber = name => {const v=Number(name);if(!Number.isInteger(v)||v<=0)throw new Error('Mã dữ liệu không hợp lệ.');return v;};
const qs = params => new URLSearchParams(Object.entries(params).filter(([,v])=>v!==null&&v!==undefined&&v!=='')).toString();
const panel = (title, subtitle, body, action='', cls='') => `<section class="panel ${cls}"><div class="panel-heading"><div><h3>${title}</h3>${subtitle?`<p>${subtitle}</p>`:''}</div>${action}</div>${body}</section>`;
const loading = () => `<div class="skeleton-table">${Array.from({length:5},()=>'<div class="skeleton"></div>').join('')}</div>`;
const heading = (title, sub, actions='') => `<div class="page-heading"><div><h1>${title}</h1><p>${sub}</p></div>${actions?`<div class="heading-actions">${actions}</div>`:''}</div>`;
const numOptions = items => items.map(i=>[i.device_id,i.device_name]);

function toast(message, kind='') {
  const item=document.createElement('div'); item.className=`toast ${kind}`;item.textContent=message;$('#toasts').append(item);setTimeout(()=>item.remove(),5500);
}
function navigate(path) { if(route()===path) render(); else location.hash=path; mobileNavOpen=false; }
function preserveRender(html) {
  const active=document.activeElement;const id=active?.id;const start=active?.selectionStart;const end=active?.selectionEnd;
  root.innerHTML=html;
  if(id){const next=document.getElementById(id);if(next){next.focus({preventScroll:true});if(next.setSelectionRange&&start!=null&&next.type!=='number')next.setSelectionRange(start,end);}}
}
function applyTheme(){ document.documentElement.dataset.theme = route()==='/'||route()==='/login'||route()==='/register'?'light':state.theme; }
function goDemo(){ state.demo=true;Object.assign(state, structuredClone(demo));state.orgId=1;state.selectedDevice=1;state.details=Object.fromEntries(state.devices.map(d=>[d.device_id,d]));state.loading=false;state.error='';state.lastUpdated=new Date();state.connection=true;navigate('/overview'); }
function resetWorkspace(){pendingCommands.clear();state.commandNotice=null;loadGeneration++;chartGeneration++;Object.assign(state,{orgs:[],orgId:null,groups:[],devices:[],details:{},alerts:[],rules:[],members:[],invitations:[],metrics:[],commands:[],selectedDevice:null,error:'',page:1});}
function resetOrgData(){pendingCommands.clear();state.commandNotice=null;loadGeneration++;chartGeneration++;Object.assign(state,{groups:[],devices:[],details:{},alerts:[],rules:[],members:[],invitations:[],metrics:[],commands:[],selectedDevice:null,groupPermissions:{},search:'',page:1,groupFilter:'all',statusFilter:'all'});}

function preview() {
  const line1=demo.metrics.map(r=>r.cpu_usage);const line2=demo.metrics.map(r=>r.ram_usage);
  const line=(vals)=>vals.map((v,i)=>`${i?'L':'M'}${i*330/47},${110-v}`).join(' ');
  return `<div class="product-preview"><div class="preview-browser"><i class="traffic"></i><i class="traffic"></i><i class="traffic"></i><span class="preview-url">dms monitor / workspace</span><span class="text-xs muted">${icon('shield')}</span></div><div class="preview-body"><aside class="preview-side">${brand(true)}<div class="preview-menu"><span class="active">${icon('grid')}Tổng quan</span><span>${icon('monitor')}Thiết bị</span><span>${icon('bell')}Cảnh báo</span><span>${icon('workspace')}Không gian làm việc</span><span>${icon('activity')}Lịch sử lệnh</span><span>${icon('settings')}Cài đặt</span></div></aside><div class="preview-content"><div class="preview-title"><div><h3>Tổng quan hệ thống</h3><small class="muted">Mọi thiết bị, trong một không gian làm việc.</small></div><span class="pill online"><i class="dot"></i>Live view</span></div><div class="preview-kpis">${[['Tổng thiết bị','12'],['Trực tuyến','08'],['Cảnh báo','01'],['Ngoại tuyến','02']].map(([label,value])=>`<div class="preview-kpi">${label}<strong>${value}</strong></div>`).join('')}</div><div class="preview-panels"><div class="preview-panel"><strong>Hiệu năng thiết bị</strong><span class="muted" style="float:right;font-size:9px">60 phút gần nhất</span><svg class="preview-chart" viewBox="0 0 330 125" role="img" aria-label="Biểu đồ minh họa CPU và RAM"><path d="M0 20H330 M0 50H330 M0 80H330 M0 110H330" stroke="#edf1e9" stroke-dasharray="3 5"/><path d="${line(line1)}" fill="none" stroke="#6a9857" stroke-width="2"/><path d="${line(line2)}" fill="none" stroke="#8aabc9" stroke-width="2"/></svg><div class="chart-legend"><span><i class="legend-line"></i>CPU</span><span><i class="legend-line blue"></i>RAM</span></div></div><div class="preview-panel"><strong>Thiết bị đang hoạt động</strong><div class="preview-small-list">${demo.devices.slice(0,4).map(d=>`<div><span>${esc(d.device_name)}</span><span style="color:#6b9659">${d.cpu_usage}%</span></div>`).join('')}</div></div></div></div></div><div class="preview-caption">Dữ liệu minh họa giao diện · Dữ liệu thật hiển thị sau khi đăng nhập</div></div>`;
}
function landing(){
  return `<div class="landing"><div class="page-wrap"><header class="landing-nav">${brand(true)}<nav aria-label="Điều hướng trang chủ"><a href="#features" data-section="features">Tính năng</a><a href="#workflow" data-section="workflow">Cách hoạt động</a><a href="#/overview" data-action="demo">Khám phá giao diện</a></nav><div class="nav-actions"><a class="btn ghost" href="#/login">Đăng nhập</a><a class="btn dark" href="#/register">Bắt đầu sử dụng ${icon('arrow')}</a></div></header><main id="main"><section class="landing-hero"><span class="hero-label"><i class="dot"></i>DEVICE MONITORING, MADE CLEAR</span><h1>Toàn bộ hệ thống.<br>Trong <em>một tầm nhìn.</em></h1><p>Nắm bắt tình trạng máy tính, phát hiện vấn đề và phối hợp cùng đội ngũ. DMS đưa mọi thiết bị về một không gian quản trị rõ ràng.</p><div class="hero-actions"><a class="btn dark" href="#/login">Mở bảng điều khiển ${icon('arrow')}</a>${button('Khám phá giao diện','demo','','play')}</div><div class="hero-note"><span>${icon('check')}Dùng chung tài khoản Android</span><span>${icon('check')}Kết nối HTTPS</span><span>${icon('check')}Quyền truy cập theo không gian làm việc</span></div></section>${preview()}<div class="trusted-line"><span>${icon('monitor')}Windows Agent</span><span>${icon('globe')}Web Dashboard</span><span>${icon('wifi')}Android App</span><span>${icon('shield')}Một nguồn dữ liệu</span></div><section id="features" class="landing-section"><div class="section-intro"><div class="eyebrow muted">01 / MỌI THỨ BẠN CẦN</div><h2>Ít thao tác hơn.<br>Hiểu hệ thống nhiều hơn.</h2><p>Từ một máy trạm đến nhiều nhóm thiết bị, luôn biết điều gì đang diễn ra và điều gì cần được ưu tiên.</p></div><div class="feature-grid">${[['activity','Giám sát có ngữ cảnh','Theo dõi CPU, RAM, ổ đĩa, mạng, GPU và các tiến trình trong từng thiết bị. Xem lịch sử hiệu năng ngay khi cần.'],['bell','Đúng vấn đề, đúng thời điểm','Tập trung các cảnh báo vào một nơi. Thiết lập ngưỡng theo thiết bị hoặc nhóm, xác nhận và xử lý sự cố có theo dõi.'],['users','Cộng tác cùng đội ngũ','Chia thiết bị theo không gian và nhóm. Cấp quyền Owner, Admin, Manager hoặc Viewer phù hợp với từng thành viên.']].map(([ico,title,desc],i)=>`<article class="feature-card"><div class="feature-number">0${i+1}</div>${icon(ico)}<h3>${title}</h3><p>${desc}</p></article>`).join('')}</div></section><section id="workflow" class="landing-section workflow"><div class="section-intro"><div class="eyebrow muted">02 / TỪ KẾT NỐI ĐẾN KIỂM SOÁT</div><h2>Sẵn sàng làm việc.<br>Không thêm phức tạp.</h2><p>Web và Android sử dụng cùng API, cùng tài khoản và cùng dữ liệu thiết bị. Những thay đổi được ghi nhận trên server DMS hiện tại.</p><a href="#/register" class="text-link" style="display:inline-flex;gap:8px;margin-top:24px;font-size:12px">Tạo không gian ${icon('arrow')}</a></div><div class="steps">${[['Tạo tài khoản hoặc đăng nhập','Sử dụng tài khoản DMS đã có trên điện thoại, hoặc đăng ký và bắt đầu một không gian làm việc mới.'],['Kết nối thiết bị','Tạo thiết bị, sao chép câu lệnh cài agent và chạy trên máy tính thuộc quyền quản lý của bạn.'],['Theo dõi và cộng tác','Kiểm tra hiệu năng, thiết lập cảnh báo và mời đội ngũ cùng quản lý trong một bảng điều khiển.']].map(([title,desc],i)=>`<div class="step"><div class="step-no">0${i+1}</div><div><h3>${title}</h3><p>${desc}</p></div></div>`).join('')}</div></section><section class="landing-section"><div class="landing-cta"><div><h2>Một góc nhìn rõ ràng.<br>Một hệ thống chủ động hơn.</h2><p>Bắt đầu từ những thiết bị bạn đang quản lý.</p></div><a class="btn primary" href="#/login">Đi đến bảng điều khiển ${icon('arrow')}</a></div></section></main><footer class="footer">${brand(true)}<span>© ${new Date().getFullYear()} DMS Monitor. Device Monitoring System.</span><div class="footer-links"><a href="#/login">Đăng nhập</a><a href="${SERVER}/static/dms-app.apk">Android APK</a><a href="https://github.com/phan-cao-huy/dms-monitor" target="_blank" rel="noopener noreferrer">Mã nguồn ${icon('arrow')}</a></div></footer></div></div>`;
}
function authView(register=false){
  return `<div class="auth-layout"><aside class="auth-art">${brand(true)}<div class="auth-copy"><div class="eyebrow" style="color:#839e8a;margin-bottom:25px">YOUR DEVICES. ONE CLEAR VIEW.</div><h1>Hiểu hệ thống.<br><span>Làm chủ mỗi ngày.</span></h1><p>Một không gian cho thiết bị, đội ngũ và những quyết định kịp thời. Kết nối với dữ liệu DMS bạn đang sử dụng.</p><div class="row" style="margin-top:30px;color:#a8c28f;font-size:12px">${icon('shield')} Kết nối được bảo vệ bằng HTTPS</div></div><div class="auth-footer">DMS Monitor / Web workspace<br>Thiết bị của bạn. Dữ liệu của bạn.</div></aside><main id="main" class="auth-form-wrap"><div class="auth-form"><h2>${register?'Tạo tài khoản DMS':'Chào mừng trở lại.'}</h2><p>${register?'Bắt đầu với không gian làm việc dành cho bạn.':'Đăng nhập để tiếp tục quản lý hệ thống của bạn.'}</p><form id="auth-form" data-register="${register}" autocomplete="on"><div id="auth-error" role="alert"></div>${register?field('Tên đầy đủ','full_name','text','','autocomplete="name" maxlength="150"')+field('Tên đăng nhập','username','text','','required minlength="3" maxlength="50" pattern="[A-Za-z0-9_.\\-]+" autocomplete="username"')+field('Địa chỉ email','email','email','','required autocomplete="email"'):field('Email hoặc tên đăng nhập','identifier','text','','required autocomplete="username" maxlength="100"')}<label class="form-field">Mật khẩu<div class="password-wrap"><input name="password" type="password" required minlength="${register?8:1}" maxlength="72" autocomplete="${register?'new-password':'current-password'}"><button type="button" data-action="show-password" aria-label="Hiện hoặc ẩn mật khẩu">${icon('eye')}</button></div>${register?'<small>Tối thiểu 8 ký tự, tối đa 72 byte UTF-8.</small>':''}</label><button class="btn primary" type="submit">${register?'Tạo tài khoản':'Đăng nhập'} ${icon('arrow')}</button></form><div class="auth-switch">${register?'Đã có tài khoản? <a class="text-link" href="#/login">Đăng nhập</a>':'Chưa có tài khoản? <a class="text-link" href="#/register">Tạo tài khoản</a>'}</div><div class="auth-bottom"><a href="#/">${icon('home')} Về trang chủ</a><span style="margin:0 12px">·</span><button class="btn ghost sm" data-action="demo">Xem giao diện minh họa</button></div></div></main></div>`;
}
const navItems=[['/overview','grid','Tổng quan'],['/devices','monitor','Thiết bị'],['/alerts','bell','Cảnh báo'],['/organizations','workspace','Không gian & thành viên'],['/activity','activity','Lịch sử lệnh'],['/settings','settings','Cài đặt']];
function shell(body){
  const path=route();const item=navItems.find(([r])=>r===path)||(path.startsWith('/devices/')?navItems[1]:navItems[0]);
  const openAlerts=state.alerts.filter(a=>!a.resolved_at).length;const name=state.user?.full_name||state.user?.username||'Tài khoản DMS';
  return `<div class="shell"><aside class="sidebar ${mobileNavOpen?'open':''}" aria-label="Điều hướng quản trị">${brand()}<div class="workspace-picker">${icon('workspace')}<div style="min-width:0;flex:1"><small>KHÔNG GIAN LÀM VIỆC</small><select id="org-picker" aria-label="Chọn không gian làm việc">${state.orgs.length?state.orgs.map(o=>`<option value="${o.org_id}" ${String(o.org_id)===String(state.orgId)?'selected':''}>${esc(o.org_name)}</option>`).join(''):'<option>Chưa có không gian làm việc</option>'}</select></div></div><div class="nav-label">WORKSPACE</div><nav class="side-nav">${navItems.map(([url,ico,label])=>`<a href="#${url}" class="${item[0]===url?'active':''}" ${item[0]===url?'aria-current="page"':''}>${icon(ico)}<span>${label}</span>${url==='/alerts'&&openAlerts?`<span class="nav-count">${openAlerts}</span>`:''}</a>`).join('')}</nav><div class="sidebar-bottom"><div class="side-help"><strong>Mọi thiết bị. Cùng một nơi.</strong>Web và Android đồng bộ trên cùng server DMS.<a href="${SERVER}/static/dms-app.apk" class="text-link">Tải ứng dụng Android ${icon('arrow')}</a></div><div class="sidebar-user"><div class="avatar">${esc(initials(name))}</div><div><strong>${esc(name)}</strong><small>${esc(org()?.role||'Thành viên')}</small></div><button data-action="logout" aria-label="Đăng xuất">${icon('logout')}</button></div></div></aside><div class="workspace-main"><header class="topbar"><div class="row"><button class="icon-btn mobile-menu" data-action="mobile-menu" aria-label="Mở điều hướng">${icon('menu')}</button><div class="breadcrumbs"><a href="#/overview">Workspace</a>${icon('chevron')}<strong>${esc(item[2])}</strong></div></div><div class="topbar-actions"><span class="connected" title="${state.demo?'Dữ liệu minh họa':API_BASE}"><i class="dot" style="background:${state.connection===false?'#c95a53':'#81a76a'}"></i>${state.demo?'Không gian minh họa':state.connection===false?'Mất kết nối':state.loading?'Đang kết nối…':'Đồng bộ trực tiếp'}</span><button class="icon-btn" data-action="theme" aria-label="Chuyển giao diện sáng tối">${icon(state.theme==='dark'?'sun':'moon')}</button><a class="icon-btn" href="#/alerts" aria-label="Xem cảnh báo">${icon('bell')}</a><a href="#/settings" class="avatar" aria-label="Tài khoản">${esc(initials(name))}</a></div></header>${state.demo?'<div class="demo-banner"><span>Dữ liệu minh họa · Các thao tác quản trị chưa được kết nối.</span><a href="#/login" data-action="exit-demo">Đăng nhập dữ liệu thật →</a></div>':''}<main id="main" class="content">${state.version?.web_version&&state.version.web_version!==WEB_VERSION?`<div class="notice split" role="status"><div><strong>Web đã có bản cập nhật mới</strong><p>Tải lại trang để sử dụng phiên bản ${esc(state.version.web_version)}.</p></div>${button('Tải lại trang','reload-web','primary','refresh')}</div>`:''}${state.error?`<div class="notice error" role="alert">${icon('warning')} ${esc(state.error)} ${button('Thử lại','refresh','ghost sm','refresh')}</div>`:''}${body}<footer class="subtle-footer"><span>DMS Monitor · ${esc(org()?.org_name||'Workspace')}</span><span>${state.lastUpdated?'Cập nhật '+date(state.lastUpdated.toISOString()):'Đang kết nối tới server'} · ${state.demo?'Minh họa':'HTTPS'}</span></footer></main></div></div>`;
}
function statCards(){
  const total=state.devices.length;const counts=Object.fromEntries(['Online','Offline','Warning','Paused'].map(s=>[s,state.devices.filter(d=>d.status===s).length]));
  const cards=[['Tổng thiết bị',total,'monitor',`${state.groups.length} nhóm trong không gian làm việc`,'#789c70'],['Trực tuyến',counts.Online,'wifi',total?`${Math.round(counts.Online/total*100)}% đang hoạt động`:'Chờ kết nối đầu tiên','#78a46d'],['Cần chú ý',counts.Warning,'warning',`${state.alerts.filter(a=>!a.resolved_at).length} cảnh báo chưa xử lý`,'#ccad69'],['Ngoại tuyến',counts.Offline,'server',`${counts.Paused} thiết bị tạm dừng`,'#99a6b8']];
  return `<div class="kpi-grid">${cards.map(([label,value,ico,note,color],i)=>`<article class="kpi"><div class="kpi-top">${label}<span class="kpi-icon">${icon(ico)}</span></div><div class="kpi-value">${state.loading&&!state.lastUpdated?'—':String(value).padStart(2,'0')}</div><div class="kpi-bottom">${i===1?'<span style="color:#72965c">●</span>':''}${esc(note)}</div>${state.demo?spark([20,32,25,48,37,44,34,56,45,52],color):''}</article>`).join('')}</div>`;
}
function chartPanel(){
  return panel('Hiệu năng thiết bị','Mức sử dụng CPU và bộ nhớ theo thời gian',`<div class="chart-controls"><select id="chart-device" aria-label="Thiết bị hiển thị biểu đồ">${state.devices.map(d=>`<option value="${d.device_id}" ${String(state.selectedDevice)===String(d.device_id)?'selected':''}>${esc(d.device_name)}</option>`).join('')||'<option>Chưa có thiết bị</option>'}</select><div class="chart-legend" style="margin:0 0 0 auto"><button class="btn ghost sm" data-action="chart-cpu" aria-pressed="${state.chartCpu}" style="opacity:${state.chartCpu?1:.4}"><i class="legend-line"></i>CPU</button><button class="btn ghost sm" data-action="chart-ram" aria-pressed="${state.chartRam}" style="opacity:${state.chartRam?1:.4}"><i class="legend-line blue"></i>RAM</button></div></div><div class="chart-wrap">${chart(state.metrics,{hourly:state.range!=='1h',cpu:state.chartCpu,ram:state.chartRam})}</div>`,`<div class="segmented">${[['1h','1 giờ'],['24h','24 giờ'],['7d','7 ngày']].map(([v,label])=>`<button data-action="range" data-value="${v}" class="${v===state.range?'active':''}">${label}</button>`).join('')}</div>`);
}
function statusPanel(){
  const total=state.devices.length;const entries=[['Online','Trực tuyến','#83ad6c'],['Warning','Cảnh báo','#d9b46b'],['Offline','Ngoại tuyến','#c7d1d8'],['Paused','Tạm dừng','#8ca8ba']];let offset=0;
  const stops=entries.map(([key,,color])=>{const n=state.devices.filter(d=>d.status===key).length;const previous=offset;offset+=total?n/total*100:0;return `${color} ${previous}% ${offset}%`;});
  return panel('Trạng thái hệ thống','Phân bố các thiết bị trong không gian làm việc',`<div class="panel-body"><div class="donut-row"><div class="donut" style="background:${total?`conic-gradient(${stops.join(',')})`:'#edf1ed'}"><div class="donut-center"><strong>${total}</strong><small>thiết bị</small></div></div><div class="status-legend">${entries.map(([key,label,color])=>`<div><i class="dot" style="background:${color}"></i><span class="muted">${label}</span><strong>${state.devices.filter(d=>d.status===key).length}</strong></div>`).join('')}</div></div></div><div class="mini-alert">${icon('warning')}<span>${state.alerts.filter(a=>!a.resolved_at).length} cảnh báo cần được xem xét</span><a class="text-link" style="margin-left:auto;white-space:nowrap" href="#/alerts">Xem tất cả →</a></div>`,'','status-panel');
}
function filteredDevices(){
  const query=state.search.toLocaleLowerCase('vi');
  return state.devices.filter(d=>(!query||`${d.device_name} ${d.group_name||''} ${d.description||''}`.toLocaleLowerCase('vi').includes(query))&&(state.statusFilter==='all'||d.status===state.statusFilter)&&(state.groupFilter==='all'||(state.groupFilter==='none'?!d.group_id:String(d.group_id)===state.groupFilter)));
}
function deviceTable(compact=false){
  const devices=filteredDevices();const pages=Math.max(1,Math.ceil(devices.length/8));state.page=Math.min(state.page,pages);const visible=devices.slice((state.page-1)*8,state.page*8);
  return panel(compact?'Thiết bị trong không gian':'Danh sách thiết bị',`${state.devices.length} thiết bị · ${state.groups.length} nhóm`,`${compact?'':`<div class="toolbar"><div class="toolbar-left"><div class="search-box">${icon('search')}<input id="device-search" placeholder="Tìm kiếm thiết bị…" aria-label="Tìm thiết bị" value="${esc(state.search)}"></div><select class="filter-select" id="status-filter" aria-label="Lọc trạng thái">${[['all','Mọi trạng thái'],['Online','Trực tuyến'],['Warning','Cảnh báo'],['Offline','Ngoại tuyến'],['Paused','Tạm dừng']].map(([v,l])=>`<option value="${v}" ${v===state.statusFilter?'selected':''}>${l}</option>`).join('')}</select><select class="filter-select" id="group-filter" aria-label="Lọc theo nhóm">${[['all','Tất cả các nhóm'],['none','Chưa phân nhóm'],...state.groups.map(g=>[String(g.group_id),g.group_name])].map(([v,l])=>`<option value="${v}" ${v===state.groupFilter?'selected':''}>${esc(l)}</option>`).join('')}</select></div>${button('Xuất CSV','export-devices','sm','download')}</div>`}${state.loading&&!state.devices.length?loading():visible.length?`<div class="table-scroll"><table><thead><tr><th>Tên thiết bị</th><th>Trạng thái</th><th>Nhóm thiết bị</th><th>CPU</th><th>RAM</th><th>Cập nhật lần cuối</th><th></th></tr></thead><tbody>${visible.map(d=>{const info=state.details[d.device_id]||d;return `<tr><td><a class="device-cell" href="#/devices/${d.device_id}"><span class="device-icon">${icon(d.group_name?.includes('Hạ tầng')?'server':'monitor')}</span><span><strong>${esc(d.device_name)}</strong><small>${esc(info.static_info?.os_name||'Windows PC')} ${esc(info.static_info?.ip_private||info.static_info?.local_ip||'')}</small></span></a></td><td>${status(d.status)}</td><td class="muted">${esc(d.group_name||'Chưa phân nhóm')}</td><td>${meter(d.status==='Paused'||d.status==='Offline'?null:info.cpu_usage)}</td><td>${meter(d.status==='Paused'||d.status==='Offline'?null:info.ram_usage)}</td><td class="muted" title="${esc(date(d.last_heartbeat))}">${esc(ago(d.last_heartbeat))}</td><td><a href="#/devices/${d.device_id}" class="icon-btn" aria-label="Chi tiết ${esc(d.device_name)}" style="width:26px;height:26px;border:0">${icon('chevron')}</a></td></tr>`;}).join('')}</tbody></table></div><div class="table-footer"><span>Hiển thị ${(state.page-1)*8+1}–${Math.min(state.page*8,devices.length)} / ${devices.length} thiết bị</span><div class="pagination">${button('←','page','sm','',`data-value="${state.page-1}" ${state.page<=1?'disabled':''}`)}<span style="padding:0 7px">${state.page} / ${pages}</span>${button('→','page','sm','',`data-value="${state.page+1}" ${state.page>=pages?'disabled':''}`)}</div></div>`:empty(state.devices.length?'Không tìm thấy thiết bị':'Chưa có thiết bị nào',state.devices.length?'Thử thay đổi từ khóa hoặc điều kiện lọc.':'Kết nối máy tính đầu tiên để bắt đầu theo dõi tài nguyên và trạng thái.', 'monitor',admin()?button('Thêm thiết bị đầu tiên','add-device','primary','plus'):'' )}`,compact?'<a class="text-link text-xs" href="#/devices">Tất cả thiết bị →</a>':'');
}
function overview(){return heading('Tổng quan hệ thống','Một góc nhìn rõ ràng về thiết bị và hoạt động của đội ngũ.',button('Làm mới','refresh','','refresh',state.refreshBusy?'disabled':'')+(admin()?button('Thêm thiết bị','add-device','primary','plus'):''))+statCards()+`<div class="grid-chart">${chartPanel()}${statusPanel()}</div>`+deviceTable(false);}
function devicesView(){return heading('Thiết bị','Quản lý máy tính, trạng thái kết nối và hiệu năng.',button('Làm mới','refresh','','refresh')+(admin()?button('Thêm thiết bị','add-device','primary','plus'):''))+deviceTable(false);}

function resourceCharts(){
  const hourly=state.range!=='1h';
  const ranges='<div class="segmented">'+[['1h','1 giờ'],['24h','24 giờ'],['7d','7 ngày']].map(([v,t])=>'<button data-action="range" data-value="'+v+'" class="'+(state.range===v?'active':'')+'">'+t+'</button>').join('')+'</div>';
  const configs=[
    ['CPU','Mức sử dụng bộ xử lý','cpu',[[hourly?'cpu_avg':'cpu_usage','#27795c',true,'CPU']], '%',100],
    ['RAM','Mức sử dụng bộ nhớ','memory',[[hourly?'ram_avg':'ram_usage','#6489c2',true,'RAM']], '%',100],
    ['Ổ đĩa','Tỷ lệ dung lượng đã sử dụng','disk',[[hourly?'disk_avg':'disk_usage','#b58b45',true,'Ổ đĩa']], '%',100],
    ['Lưu lượng mạng','Tốc độ tải xuống và tải lên','wifi',[[hourly?'net_in_avg_kbps':'net_in_kbps','#608a84',true,'Tải xuống'],[hourly?'net_out_avg_kbps':'net_out_kbps','#9674ac',true,'Tải lên']], ' KB/s',null]
  ];
  return '<div class="metrics-toolbar"><div><strong>Phân tích tài nguyên</strong><p>Cùng khoảng thời gian cho tất cả biểu đồ</p></div>'+ranges+'</div><div class="resource-charts">'+configs.map(([title,sub,ico,series,unit,ceiling])=>panel(icon(ico)+' '+title,sub,'<div class="chart-wrap">'+chart(state.metrics,{hourly,series,unit,ceiling})+'</div>','','metric-panel')).join('')+'</div><div class="notice gap-top">GPU: API hiện cung cấp số liệu mới nhất ở tab Tổng quan; chưa có dữ liệu lịch sử GPU.</div>';
}
function canControl(d){return !d?.archived_at&&(admin()||(d?.group_id?state.groupPermissions?.[d.group_id]==='Manager':org()?.role==='Manager'));}
function deviceOverview(d){
  const info=d.static_info||{},stale=['Offline','Paused'].includes(d.status),rows=state.metrics,hourly=state.range!=='1h';
  const disks=[...(d.disks||[])].sort((a,b)=>String(a.mountpoint).localeCompare(String(b.mountpoint)));
  const alerts=state.alerts.filter(a=>String(a.device_id)===String(d.device_id)&&!a.resolved_at);
  const resource=(name,value,ico,sub,key,color)=>`<article class="resource-stat"><div class="split"><small>${name}</small>${icon(ico)}</div><strong>${percent(value)}</strong><p class="text-sm muted">${sub}</p>${meter(value,' ')}<div class="resource-history">${key?spark(rows.map(r=>r[key]).filter(v=>v!=null&&Number.isFinite(Number(v))),color)||'<span>Chưa có lịch sử</span>':'<span>Số liệu mới nhất · chưa có lịch sử GPU</span>'}</div></article>`;
  const ramTotal=info.ram_total_mb?info.ram_total_mb/1024:null;
  const ram=ramTotal&&d.ram_usage!=null?`~${number(ramTotal*d.ram_usage/100)} / ${number(ramTotal)} GB`:'Chưa có dung lượng bộ nhớ';
  const gpuSub=`${esc(d.gpu_name||info.gpu_name||'Chưa nhận tên GPU')} · VRAM ${d.gpu_vram_used_mb==null?'—':number(d.gpu_vram_used_mb/1024)} / ${d.gpu_vram_total_mb==null?'—':number(d.gpu_vram_total_mb/1024)} GB`;
  const resources='<div class="overview-resources">'+resource('CPU',d.cpu_usage,'cpu',d.cpu_temp_c>0?number(d.cpu_temp_c)+' °C':'Không có cảm biến nhiệt độ',hourly?'cpu_avg':'cpu_usage','#27795c')+resource('RAM',d.ram_usage,'memory',ram,hourly?'ram_avg':'ram_usage','#6489c2')+resource('GPU',d.gpu_usage_pct,'monitor',gpuSub,null,'#9674ac')+'</div>';
  const driveCards=disks.map(disk=>`<article class="drive-card"><div class="split"><h3>${icon('disk')} ${esc(disk.mountpoint||'—')}</h3><span class="pill">${esc(disk.fstype||'Phân vùng')}</span></div><strong class="drive-usage">${percent(disk.used_percent)}</strong>${meter(disk.used_percent,' ')}<div class="drive-capacity"><span>Đã dùng<b>${number(disk.used_gb)} GB</b></span><span>Còn trống<b>${number(disk.free_gb)} GB</b></span><span>Tổng<b>${number(disk.total_gb)} GB</b></span></div>${disk.used_percent>=90?'<p class="drive-warning">Phân vùng sắp đầy (≥ 90%)</p>':''}</article>`).join('');
  const processes=[...(d.top_processes||[])].sort((a,b)=>state.overviewSort==='ram'?(b.ram_mb||0)-(a.ram_mb||0):state.overviewSort==='gpu'?(b.gpu_usage_pct??-1)-(a.gpu_usage_pct??-1):(b.cpu_percent??b.cpu_pct??0)-(a.cpu_percent??a.cpu_pct??0)).slice(0,5);
  return panel('Trạng thái thiết bị','Trạng thái kết nối và thời điểm cập nhật',`<div class="panel-body"><div class="row wrap">${status(d.status)}<strong>Cập nhật cuối: ${esc(date(d.last_heartbeat))}</strong><span class="muted">Hoạt động: ${d.uptime_seconds==null?'—':Math.floor(d.uptime_seconds/86400)+' ngày '+Math.floor(d.uptime_seconds%86400/3600)+' giờ'}</span></div>${stale?'<div class="notice gap-top">Dữ liệu lần cuối đã lưu; không phản ánh trạng thái sử dụng hiện tại.</div>':''}</div>`)+
    (alerts.length?`<div class="notice error gap-top"><strong>${alerts.length} vấn đề cần chú ý</strong>${alerts.map(a=>`<p>${esc(alertCopy(a,state.rules.find(r=>r.rule_id===a.rule_id),d.device_name,state.alertLanguage).body)}</p>`).join('')}<a class="text-link" href="#/alerts">Xem cảnh báo →</a></div>`:'')+
    '<div class="gap-top">'+resources+'</div>'+
    '<div class="gap-top">'+panel('Ổ đĩa & phân vùng',disks.length+' phân vùng do agent báo về',disks.length?'<div class="panel-body"><div class="drive-grid">'+driveCards+'</div><p class="muted text-sm gap-top">Các phân vùng có thể thuộc cùng một ổ cứng vật lý. Chưa thu thập tốc độ đọc/ghi, loại SSD/HDD hoặc tình trạng sức khỏe ổ.</p></div>':empty('Chưa có danh sách phân vùng','Agent chưa gửi danh sách. Không mặc định số liệu tổng hợp là ổ C:.','disk'))+'</div>'+
    '<div class="detail-grid gap-top">'+panel('Kết nối mạng','Tốc độ tải xuống / tải lên · KB/s',`<div class="panel-body"><div class="network-speeds"><div>↓ Tải xuống<strong>${number(d.net_in_kbps)} KB/s</strong></div><div>↑ Tải lên<strong>${number(d.net_out_kbps)} KB/s</strong></div></div>${kv('IP nội bộ',info.ip_private||info.local_ip)}${kv('IP công khai',info.ip_public)}</div><div class="chart-wrap">${chart(rows,{hourly,series:[[hourly?'net_in_avg_kbps':'net_in_kbps','#608a84',true,'Tải xuống'],[hourly?'net_out_avg_kbps':'net_out_kbps','#9674ac',true,'Tải lên']],unit:' KB/s',ceiling:null})}</div>`)+
    panel('Tiến trình nổi bật','Top 5 trong snapshot gần nhất',processes.length?`<div class="table-scroll"><table><thead><tr><th>Tiến trình / PID</th><th>CPU</th><th>RAM</th></tr></thead><tbody>${processes.map(p=>`<tr><td><strong>${esc(p.name)}</strong><div class="muted text-xs">PID ${esc(p.pid)}</div></td><td>${percent(p.cpu_percent??p.cpu_pct)}</td><td>${number(p.ram_mb)} MB</td></tr>`).join('')}</tbody></table></div>`:empty('Chưa có dữ liệu tiến trình','Đợi snapshot từ agent.','terminal'),`<div class="row">${button('CPU','overview-sort','sm','','data-value="cpu"')}${button('RAM','overview-sort','sm','','data-value="ram"')}${button('GPU','overview-sort','sm','','data-value="gpu"')}${button('Xem tất cả','detail-tab','sm','arrow','data-value="processes"')}</div>` )+'</div>'+
    `<details class="panel hardware-details gap-top"><summary>Thông tin máy · Xem cấu hình phần cứng</summary><div class="panel-body">${kv('Hostname',info.hostname||d.device_name)}${kv('Hệ điều hành',[info.os_name,info.os_version].filter(Boolean).join(' ')||'—')}${kv('CPU',info.cpu_model)}${kv('Số CPU logic',info.cpu_cores)}${kv('RAM',ramTotal?number(ramTotal)+' GB':'—')}${kv('GPU',d.gpu_name||info.gpu_name)}${kv('Nhiệt độ GPU',d.gpu_temp_c>0?number(d.gpu_temp_c)+' °C':'Không có cảm biến')}${kv('Kernel',info.kernel_version)}${kv('Phiên bản Agent',info.agent_version)}</div></details>`;
}
function processesPanel(d){
  const metric=state.processSort||'cpu',ram=d.static_info?.ram_total_mb;
  const value=p=>metric==='ram'?p.ram_mb:metric==='gpu'?p.gpu_usage_pct:(p.cpu_percent??p.cpu_pct);
  const processes=[...(d.top_processes||[])].sort((a,b)=>(value(b)??-1)-(value(a)??-1));
  const gpuAvailable=processes.some(p=>p.gpu_usage_pct!=null);
  const controls='<div class="row wrap">'+[['cpu','CPU'],['ram','RAM'],['gpu','GPU (VGA)']].map(([key,label])=>button(label+(metric===key?' ↓':''),'process-sort',metric===key?'sm primary':'sm','',`data-value="${key}" ${key==='gpu'&&!gpuAvailable?'disabled':''}`)).join('')+'</div>';
  const cell=(text,v,warm,hot)=>`<td class="proc-heat ${processHeat(v,warm,hot)}" style="--heat:${v==null?0:.08+Math.max(0,Math.min(100,v))/100*.2}">${text}</td>`;
  return panel('Tiến trình dùng tài nguyên nhiều nhất','Sắp xếp giảm dần · Xanh: thấp · Vàng: vừa · Đỏ: cao',`<div class="panel-body">${controls}<p class="muted text-sm gap-top">Snapshot top CPU/RAM/GPU do agent gửi; không phải toàn bộ tiến trình trên máy. Nhấn hàng để xem chi tiết.</p>${!gpuAvailable?'<p class="notice gap-top">Chưa có GPU theo tiến trình. Cần cập nhật Agent Windows và bộ đếm GPU được hỗ trợ.</p>':''}</div>`+(processes.length?`<div class="table-scroll"><table class="process-table"><thead><tr><th>Tiến trình</th><th>PID</th><th>CPU</th><th>RAM</th><th>GPU</th><th></th></tr></thead><tbody>${processes.map(p=>{
    const selected=state.processSelected===p.pid,share=ram>0?p.ram_mb/ram*100:null;
    return `<tr data-action="process-select" data-id="${p.pid}" class="${selected?'selected':''}" tabindex="0"><td><strong>${esc(p.name)}</strong></td><td class="mono">${esc(p.pid)}</td>${cell(percent(p.cpu_percent??p.cpu_pct),p.cpu_percent??p.cpu_pct,20,50)}${cell(number(p.ram_mb)+' MB'+(share==null?'':`<small>${percent(share)} RAM toàn máy</small>`),share,10,25)}${cell(percent(p.gpu_usage_pct),p.gpu_usage_pct,40,80)}<td>${canControl(d)&&d.is_monitoring?button('Dừng','kill-process','sm danger','close',`data-id="${p.pid}" data-name="${esc(p.name)}"`):''}</td></tr>${selected?`<tr class="process-detail"><td colspan="6"><strong>${esc(p.name)} · PID ${p.pid}</strong><p>Người dùng: ${esc(p.user||'—')}</p><p class="break">${esc(p.exe_path||'Chưa có đường dẫn chương trình')}</p></td></tr>`:''}`;
  }).join('')}</tbody></table></div>`:empty('Chưa có dữ liệu tiến trình','Đợi snapshot từ agent.','terminal')));
}
function deviceView(){
  const d=currentDevice();
  if(!d)return heading('Chi tiết thiết bị','Thông tin được đồng bộ từ agent.')+(state.loading?loading():empty('Không tải được thiết bị','Thiết bị không tồn tại hoặc tài khoản chưa được cấp quyền.','shield','<a href="#/devices" class="btn">Về danh sách thiết bị</a>'));
  const info=d.static_info||{};
  const title=`<div class="detail-heading"><span class="device-icon">${icon('monitor')}</span><div><h1>${esc(d.device_name)}</h1><p>${esc(d.group_name||'Chưa phân nhóm')} <span style="padding:0 6px">·</span> ${status(d.status)}</p></div></div>`;
  const controls=button('Làm mới','refresh','','refresh')+(canControl(d)?button(d.is_monitoring?'Tạm dừng theo dõi':'Bật theo dõi','toggle-monitoring','',d.is_monitoring?'pause':'play')+button('Điều khiển','device-command','primary','terminal')+button('Xóa thiết bị','delete-device','danger','trash'):'');
  let body='';
  if(state.detailTab==='overview'){
    body=deviceOverview(d);
  }else if(state.detailTab==='metrics'){
    body=heading('Lịch sử hiệu năng','Theo dõi từng tài nguyên, so sánh mức trung bình và các đỉnh sử dụng.',button('Xuất CSV','export-metrics','','download'))+resourceCharts();
  }else if(state.detailTab==='processes'){
    body=processesPanel(d);
  }else if(state.detailTab==='commands')body=commandsTable();
  else body=`<div class="detail-grid">${panel('Thông tin thiết bị','Cập nhật tên, mô tả và nhóm thiết bị.',`<div class="panel-body">${kv('Tên hiển thị',d.device_name)}${kv('Mô tả',d.description||'Chưa có mô tả')}${kv('Nhóm thiết bị',d.group_name||'Chưa phân nhóm')}${kv('Theo dõi',d.is_monitoring?'Đang bật':'Tạm dừng')}${canControl(d)?`<div class="gap-top">${button('Chỉnh sửa thiết bị','edit-device','','edit')}</div>`:''}</div>`)}${panel('Tác vụ quản trị','Thao tác nhạy cảm cần được xác nhận.',`<div class="panel-body">${admin()?`<div class="setting-row"><div><strong>Tạo lại token agent</strong><p>Token hiện tại sẽ hết hiệu lực.</p></div>${button('Tạo lại','regenerate-token','sm','key')}</div>`:''}${canControl(d)?`<div class="setting-row"><div><strong>Xóa thiết bị & gỡ agent</strong><p>Gửi lệnh gỡ phần mềm xuống máy tính.</p></div>${button('Xóa thiết bị','delete-device','sm danger','trash')}</div>`:'<p class="muted text-sm">Tài khoản của bạn không có quyền thay đổi thiết bị này.</p>'}</div>`)}</div>`;
  return `<a class="text-link text-xs" href="#/devices" style="display:inline-block;margin-bottom:18px">← Danh sách thiết bị</a><div class="page-heading">${title}<div class="heading-actions">${controls}</div></div>${state.commandNotice&&String(state.commandNotice.deviceId)===String(d.device_id)?`<div class="notice ${['FAILED','TIMEOUT'].includes(state.commandNotice.command.status)?'error':''}" role="status"><strong>${esc(commandFeedback(state.commandNotice.command))}</strong></div>`:''}<div class="tabs" role="tablist">${[['overview','Tổng quan'],['metrics','Lịch sử hiệu năng'],['processes','Tiến trình'],['commands','Lịch sử lệnh'],['settings','Cấu hình']].map(([key,label])=>`<button role="tab" aria-selected="${key===state.detailTab}" data-action="detail-tab" data-value="${key}" class="${key===state.detailTab?'active':''}">${label}</button>`).join('')}</div>${body}`;
}
function commandsTable(){
  return panel('Lịch sử lệnh điều khiển','Kết quả được agent báo về server.',state.commands.length?`<div class="table-scroll"><table><thead><tr><th>Lệnh</th><th>Trạng thái</th><th>Lý do</th><th>Thời gian</th><th>Kết quả</th></tr></thead><tbody>${[...state.commands].sort((a,b)=>b.command_id-a.command_id).map(c=>`<tr><td class="mono">${esc(c.command_type)}</td><td>${status(c.status)}</td><td class="alert-message">${esc(c.reason)}</td><td class="muted">${esc(date(c.created_at))}</td><td><div class="command-output">${esc(c.output||'Chưa có kết quả')}</div></td></tr>`).join('')}</tbody></table></div>`:empty('Chưa có lệnh nào','Các lệnh đã gửi từ web và Android cùng xuất hiện ở đây.','terminal'));
}
function activityView(){return heading('Lịch sử lệnh','Theo dõi các yêu cầu điều khiển đã gửi tới từng thiết bị.',button('Làm mới','refresh','','refresh'))+`<div class="toolbar-left" style="margin-bottom:20px"><span class="muted text-sm">Thiết bị:</span><select class="filter-select" id="activity-device" aria-label="Chọn thiết bị xem lịch sử lệnh">${state.devices.map(d=>`<option value="${d.device_id}" ${String(state.selectedDevice)===String(d.device_id)?'selected':''}>${esc(d.device_name)}</option>`).join('')||'<option>Chưa có thiết bị</option>'}</select>${button('Xuất CSV','export-commands','sm','download')}</div>`+commandsTable();}
function alertsView(){
  const alerts=state.alerts.filter(a=>state.alertFilter==='all'||(state.alertFilter==='open'?!a.resolved_at:!!a.resolved_at));
  return heading('Cảnh báo','Những vấn đề cần chú ý, và các sự cố đã được xử lý.',select('Ngôn ngữ nội dung / Alert language','alert_language',[['vi','Tiếng Việt'],['en','English']],state.alertLanguage)+button('Làm mới','refresh','','refresh')+(admin()?button('Tạo quy tắc','add-rule','primary','plus'):''))+`<div class="tabs"><button data-action="alert-tab" data-value="alerts" class="${state.alertTab!=='rules'?'active':''}">Lịch sử cảnh báo</button><button data-action="alert-tab" data-value="rules" class="${state.alertTab==='rules'?'active':''}">Quy tắc cảnh báo <span class="muted">${state.rules.length}</span></button></div>`+(state.alertTab==='rules'?rulesView():panel('Các cảnh báo',`${state.alerts.filter(a=>!a.resolved_at).length} chưa xử lý`,`${alerts.length?`<div class="table-scroll"><table><thead><tr><th>Mức độ</th><th>Nội dung</th><th>Thiết bị</th><th>Thời gian</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>${alerts.map(a=>`<tr><td><span class="pill ${a.severity==='CRITICAL'?'offline':a.severity==='WARNING'?'warning':'paused'}">${a.severity==='CRITICAL'?'Nghiêm trọng':a.severity==='WARNING'?'Cảnh báo':'Thông tin'}</span></td><td><div class="alert-message"><strong>${esc(alertCopy(a,state.rules.find(r=>r.rule_id===a.rule_id),deviceName(a.device_id),state.alertLanguage).title)}</strong><small>${esc(alertCopy(a,state.rules.find(r=>r.rule_id===a.rule_id),deviceName(a.device_id),state.alertLanguage).body)}</small>${a.metric_value!=null?`<small>Giá trị: ${number(a.metric_value)}${a.threshold_value!=null?' · Ngưỡng: '+number(a.threshold_value):''}</small>`:''}</div></td><td><a class="text-link" href="#/devices/${a.device_id}">${esc(deviceName(a.device_id))}</a></td><td class="muted">${esc(date(a.created_at))}</td><td><span class="pill ${a.resolved_at?'online':a.acknowledged_at?'paused':'warning'}">${a.resolved_at?'Đã xử lý':a.acknowledged_at?'Đã tiếp nhận':'Chưa xử lý'}</span></td><td><div class="row">${!a.resolved_at?(!a.acknowledged_at?button('Tiếp nhận','ack-alert','sm','check',`data-id="${a.alert_id}"`):'')+button('Xử lý','resolve-alert','sm','check',`data-id="${a.alert_id}"`):'—'}</div></td></tr>`).join('')}</tbody></table></div>`:empty('Không có cảnh báo trong bộ lọc này','Các cảnh báo từ thiết bị và quy tắc sẽ được hiển thị tại đây.','shield')}`,`<div class="segmented">${[['open','Chưa xử lý'],['resolved','Đã xử lý'],['all','Tất cả']].map(([value,label])=>`<button data-action="alert-filter" data-value="${value}" class="${state.alertFilter===value?'active':''}">${label}</button>`).join('')}</div>`));
}
function thresholdControl(value=90,metric='CPU'){
  const pct=['CPU','RAM','GPU','DISK'].includes(metric),max=pct?100:Math.max(metric==='OFFLINE'?1440:10000,Number(value)||0),min=metric==='OFFLINE'?1:0;
  return '<div class="threshold-control" data-metric="'+esc(metric)+'">'+field('Ngưỡng ('+(pct?'%':metric==='OFFLINE'?'phút':'KB/s')+')','threshold','number',value,'required min="'+min+'" max="'+max+'" step="0.1"')+'<input type="range" aria-label="Kéo chỉnh ngưỡng cảnh báo" data-threshold-slider min="'+min+'" max="'+max+'" step="0.1" value="'+esc(value)+'"><small>Nhập số hoặc kéo thanh để điều chỉnh</small></div>';
}
function rulesView(){
  return panel('Quy tắc đang áp dụng','Ngưỡng, thời lượng và phạm vi theo dõi.',state.rules.length?`<div class="table-scroll"><table><thead><tr><th>Tên quy tắc</th><th>Chỉ số</th><th>Điều kiện</th><th>Phạm vi</th><th>Thời lượng</th><th>Mức độ</th><th></th></tr></thead><tbody>${state.rules.map(r=>`<tr><td><strong>${esc(r.rule_name||r.metric_type)}</strong></td><td><span class="pill">${esc(r.metric_type)}</span></td><td class="mono">${esc(r.metric_type==='SERVICE'?r.service_name:r.metric_type==='PROCESS_MISSING'?r.process_name:`${r.condition} ${r.threshold}`)}</td><td>${esc(r.device_id?deviceName(r.device_id):state.groups.find(g=>g.group_id===r.group_id)?.group_name||'Nhóm #'+r.group_id)}</td><td>${r.duration_minutes} phút</td><td>${esc(r.severity)}</td><td>${admin()?button('Chỉnh ngưỡng','edit-rule','sm','edit',`data-id="${r.rule_id}"`)+button('Xóa','delete-rule','sm danger','trash',`data-id="${r.rule_id}"`):''}</td></tr>`).join('')}</tbody></table></div>`:empty('Chưa có quy tắc tùy chỉnh','Tạo quy tắc để nhận cảnh báo theo điều kiện của đội ngũ.','bell',admin()?button('Tạo quy tắc đầu tiên','add-rule','primary','plus'):''));
}
function organizationsView(){
  const top=heading('Không gian & thành viên','Chia sẻ không gian làm việc, thiết bị và trách nhiệm.',button('Tham gia không gian','join-org','','key')+button('Tạo không gian','create-org','primary','plus'));
  const tabs=`<div class="tabs">${[['groups','Nhóm thiết bị'],['members','Thành viên'],['invitations','Mã mời'],['orgs','Không gian làm việc của tôi']].filter(([key])=>key!=='invitations'||admin()).map(([key,label])=>`<button data-action="org-tab" data-value="${key}" class="${state.orgTab===key?'active':''}">${label}</button>`).join('')}</div>`;
  let content='';
  if(state.orgTab==='orgs')content=`<div class="card-grid">${state.orgs.map(o=>`<article class="org-card"><div class="card-top"><span class="device-icon">${icon('workspace')}</span><span class="pill">${esc(o.role)}</span></div><h3>${esc(o.org_name)}</h3><p>${esc(o.description||'Không gian quản trị thiết bị DMS.')}</p><small class="muted">Tạo ngày ${date(o.created_at)}</small><div class="card-actions">${button(String(o.org_id)===String(state.orgId)?'Đang sử dụng':'Chuyển không gian','switch-org','sm','arrow',`data-id="${o.org_id}" ${String(o.org_id)===String(state.orgId)?'disabled':''}`)}${o.role!=='Owner'?button('Rời không gian','leave-org','sm danger','logout',`data-id="${o.org_id}"`):''}</div></article>`).join('')}</div>`;
  else if(!state.orgId)content=empty('Bạn chưa có không gian làm việc','Tạo không gian mới hoặc tham gia bằng mã mời.','workspace',button('Tạo không gian','create-org','primary','plus'));
  else if(state.orgTab==='groups')content=`<div class="split" style="margin-bottom:20px"><p class="muted text-sm">${state.groups.length} nhóm trong ${esc(org()?.org_name)}</p>${admin()?button('Tạo nhóm thiết bị','add-group','sm','plus'):''}</div>`+(state.groups.length?`<div class="card-grid">${state.groups.map(g=>`<article class="group-card"><div class="card-top"><span class="device-icon">${icon('folder')}</span><span class="pill">${state.devices.filter(d=>d.group_id===g.group_id).length} thiết bị</span></div><h3>${esc(g.group_name)}</h3><p>${esc(g.description||'Quản lý các thiết bị trong nhóm này.')}</p><div class="row text-xs muted">${icon('users')}Quyền truy cập theo thành viên</div><div class="card-actions">${button('Quyền truy cập','group-members','sm','users',`data-id="${g.group_id}"`)}${admin()?button('Sửa','edit-group','sm','edit',`data-id="${g.group_id}"`)+button('Xóa','delete-group','sm danger','trash',`data-id="${g.group_id}"`):''}</div></article>`).join('')}</div>`:empty('Chưa có nhóm thiết bị','Tạo nhóm để sắp xếp máy tính và cấp quyền chi tiết cho thành viên.','folder'));
  else if(state.orgTab==='members')content=panel('Thành viên',`${state.members.length} thành viên`,state.members.length?`<div class="table-scroll"><table><thead><tr><th>Thành viên</th><th>Email</th><th>Vai trò</th><th>Tham gia</th><th></th></tr></thead><tbody>${state.members.map(m=>`<tr><td><div class="device-cell"><div class="avatar">${esc(initials(m.full_name||m.username))}</div><div><strong>${esc(m.full_name||m.username)}</strong><small>@${esc(m.username)}</small></div></div></td><td class="muted">${esc(m.email)}</td><td><span class="pill">${esc(m.role)}</span></td><td class="muted">${date(m.joined_at)}</td><td><div class="row">${admin()&&m.role!=='Owner'&&(org()?.role==='Owner'||m.role!=='Admin')?button('Đổi quyền','member-role','sm','edit',`data-id="${m.user_id}"`)+button('Xóa','remove-member','sm danger','trash',`data-id="${m.user_id}"`):''}</div></td></tr>`).join('')}</tbody></table></div>`:loading());
  else content=panel('Mã mời tham gia','Mã mời chỉ được chia sẻ bởi quản trị viên.',state.invitations.length?`<div class="table-scroll"><table><thead><tr><th>Mã mời</th><th>Vai trò</th><th>Đã sử dụng</th><th>Hết hạn</th><th></th></tr></thead><tbody>${state.invitations.map(i=>`<tr><td><strong class="mono">${esc(i.invite_code)}</strong></td><td>${esc(i.default_role)}</td><td>${i.use_count} / ${i.max_uses??'Không giới hạn'}</td><td class="muted">${i.expires_at?date(i.expires_at):'Không giới hạn'}</td><td>${button('Sao chép','copy','sm','copy',`data-text="${esc(i.invite_code)}"`)}</td></tr>`).join('')}</tbody></table></div>`:empty('Chưa có mã mời','Tạo mã để mời thành viên vào không gian làm việc.','key'),admin()?button('Tạo mã mời','add-invitation','sm primary','plus'):'');
  return top+tabs+content;
}
function settingsView(){
  const u=state.user||{};
  return heading('Cài đặt & tài khoản','Thông tin tài khoản, giao diện và kết nối của bạn.')+`<div class="account-grid">${panel('Tài khoản DMS','Dùng chung với ứng dụng Android.',`<div class="panel-body">${kv('Họ tên',u.full_name)}${kv('Tên đăng nhập',u.username)}${kv('Email',u.email)}${kv('Vai trò hiện tại',org()?.role)}${kv('Mã người dùng',u.user_id)}<p class="muted text-xs gap-top">Backend hiện chưa cung cấp API đổi hồ sơ hoặc mật khẩu. Web chỉ hiển thị thông tin đã lưu trên server.</p><div class="gap-top">${button('Đăng xuất','logout','danger','logout')}</div></div>`)}${panel('Tùy chọn & kết nối','Các thay đổi giao diện được lưu trên trình duyệt này.',`<div class="panel-body"><div class="setting-row"><div><strong>Giao diện tối</strong><p>Điều chỉnh độ sáng của không gian làm việc.</p></div><button class="toggle ${state.theme==='dark'?'on':''}" role="switch" aria-checked="${state.theme==='dark'}" aria-label="Giao diện tối" data-action="theme"></button></div><div class="setting-row"><div><strong>Đồng bộ tự động</strong><p>Mỗi 15 giây khi tab đang hiển thị.</p></div><span class="pill online">Đang bật</span></div><div class="setting-row"><div><strong>Kết nối API</strong><p class="break">${API_BASE}</p></div>${icon('shield')}</div><div class="setting-row"><div><strong>Ứng dụng Android</strong><p>${state.version?'Phiên bản '+esc(state.version.version_name):'Kiểm tra bản phát hành mới nhất.'}</p></div><a href="${SERVER}/static/dms-app.apk" class="btn sm">${icon('download')}Tải APK</a></div><div class="setting-row"><div><strong>Trạng thái server</strong><p>${state.connection?'Đã nhận dữ liệu từ API.':'Chưa xác minh kết nối.'}</p></div>${button('Kiểm tra','check-health','sm','refresh')}</div></div>`)}</div>`;
}

function render(){
  applyTheme();const path=route();
  if(path==='/'){document.title='DMS Monitor — Một tầm nhìn cho toàn bộ hệ thống';preserveRender(landing());return;}
  if(path==='/login'||path==='/register'){document.title=(path==='/login'?'Đăng nhập':'Đăng ký')+' · DMS Monitor';preserveRender(authView(path==='/register'));return;}
  if(!state.user){if(api.session){preserveRender(`<main class="boot"><span class="brand-mark">d</span><div><p>${state.error?esc(state.error):'Đang mở không gian làm việc…'}</p>${state.error?button('Thử lại','refresh','sm','refresh')+button('Đăng xuất','logout','sm'):''}</div></main>`);return;}navigate('/login');return;}
  document.title=(navItems.find(([r])=>r===path)?.[2]||'Chi tiết thiết bị')+' · DMS Monitor';
  let content=path==='/overview'?overview():path==='/devices'?devicesView():path.startsWith('/devices/')?deviceView():path==='/alerts'?alertsView():path==='/organizations'?organizationsView():path==='/activity'?activityView():path==='/settings'?settingsView():empty('Không tìm thấy trang','Về trang tổng quan để tiếp tục.','home','<a href="#/overview" class="btn primary">Về tổng quan</a>');
  preserveRender(shell(content));
}

async function mapPool(items, fn, limit=4){let index=0;const result=[];await Promise.all(Array.from({length:Math.min(limit,items.length)},async()=>{while(index<items.length){const i=index++;result[i]=await fn(items[i],i);}}));return result;}
async function loadChart(){
  const id=state.selectedDevice;if(!id){state.metrics=[];return;}
  if(state.demo){state.metrics=structuredClone(demo.metrics).map(m=>state.range==='1h'?m:{hour_bucket:m.created_at,cpu_avg:m.cpu_usage,ram_avg:m.ram_usage,disk_avg:m.disk_usage,net_in_avg_kbps:m.net_in_kbps,net_out_avg_kbps:m.net_out_kbps});return;}
  const generation=++chartGeneration;const hours=state.range==='1h'?1:state.range==='24h'?24:168;
  const query=qs({from:new Date(Date.now()-hours*3600000).toISOString(),to:new Date().toISOString(),interval:state.range==='1h'?'raw':'hourly',limit:1000});
  const data=await api.request(`devices/${safeNumber(id)}/metrics?${query}`);
  if(generation===chartGeneration&&String(id)===String(state.selectedDevice))state.metrics=data;
}
function syncCommandNotice(commands){
  const notice=state.commandNotice;
  if(!notice)return;
  const command=commands.find(c=>c.command_id===notice.command.command_id);
  if(!command)return;
  const terminal=['SUCCESS','FAILED','TIMEOUT'].includes(command.status);
  if(terminal&&!['SUCCESS','FAILED','TIMEOUT'].includes(notice.command.status))
    toast(commandFeedback(command),command.status==='SUCCESS'?'':'error');
  notice.command=command;
}
async function trackCommand(command,deviceId){
  const watch={command,deviceId};
  pendingCommands.set(command.command_id,watch);
  state.commandNotice=watch;render();
  for(let i=0;i<150;i++){
    await new Promise(resolve=>setTimeout(resolve,2000));
    if(pendingCommands.get(command.command_id)!==watch||!api.session)return;
    try{
      const commands=await api.request(`devices/${deviceId}/commands`);
      if(pendingCommands.get(command.command_id)!==watch)return;
      const result=commands.find(c=>c.command_id===command.command_id);
      syncCommandNotice(commands);
      if(String(state.selectedDevice)===String(deviceId))state.commands=commands;
      if(result&&['SUCCESS','FAILED','TIMEOUT'].includes(result.status)){
        pendingCommands.delete(command.command_id);
        if(String(state.selectedDevice)===String(deviceId)){
          try{await loadDetail(deviceId);}catch{}
        }
        render();return;
      }
      render();
    }catch{ /* Keep waiting; a temporary network error is not a failed command. */ }
  }
  pendingCommands.delete(command.command_id);
}
async function loadDetail(id){
  const generation=loadGeneration;
  if(state.demo){state.details[id]=demo.devices.find(d=>d.device_id===Number(id));state.commands=demo.commands;await loadChart();return;}
  const data=await api.request(`devices/${safeNumber(id)}`);if(generation!==loadGeneration)return;state.details[id]=data;
  if(state.selectedDevice===id||String(state.selectedDevice)===String(id)){
    const [commands]=await Promise.all([api.request(`devices/${id}/commands`),loadChart()]);if(generation===loadGeneration){state.commands=commands;syncCommandNotice(commands);}
  }
}
async function loadWorkspace({silent=false}={}){
  if(state.demo){state.lastUpdated=new Date();render();return;}
  if(!api.session||!state.user)return;
  const generation=++loadGeneration;state.refreshBusy=true;if(!silent)state.loading=true;state.error='';render();
  try{
    const organizations=await api.request('orgs');if(generation!==loadGeneration)return;state.orgs=organizations;
    if(!organizations.some(o=>String(o.org_id)===String(state.orgId)))state.orgId=organizations[0]?.org_id??null;
    if(!state.orgId){state.devices=[];state.groups=[];state.alerts=[];state.loading=false;state.lastUpdated=new Date();return;}
    const id=safeNumber(state.orgId);
    const [devices,groups,alerts,rules,members,version]=await Promise.all([
      api.request(`orgs/${id}/devices`),api.request(`orgs/${id}/groups`),api.request(`orgs/${id}/alerts?limit=200`),api.request(`orgs/${id}/alert-rules`),api.request(`orgs/${id}/members`),api.request('app/version',{auth:false}),
    ]);
    if(generation!==loadGeneration)return;
    Object.assign(state,{devices,groups,alerts,rules,members,version,connection:true});
    if(!devices.some(d=>String(d.device_id)===String(state.selectedDevice)))state.selectedDevice=devices.find(d=>d.status==='Online')?.device_id||devices[0]?.device_id||null;
    if(!admin()){
      const rights=await mapPool(groups,async g=>{try{const members=await api.request(`groups/${g.group_id}/members`);return [g.group_id,members.find(m=>m.user_id===state.user.user_id)?.role];}catch{return[g.group_id,null];}});
      if(generation!==loadGeneration)return;state.groupPermissions=Object.fromEntries(rights);
    }
    if(route()==='/organizations'&&admin())state.invitations=await api.request(`orgs/${id}/invitations`);
    if(route().startsWith('/devices/')){state.selectedDevice=Number(route().split('/')[2]);await loadDetail(state.selectedDevice);}
    else{
      const visible=filteredDevices().slice((state.page-1)*8,state.page*8);
      const details=await mapPool(visible,async d=>{try{return await api.request(`devices/${d.device_id}`);}catch{return d;}});
      if(generation!==loadGeneration)return;for(const d of details)state.details[d.device_id]=d;
      if(state.selectedDevice&&['/overview','/activity'].includes(route())){
        await loadChart();if(route()==='/activity'){const commands=await api.request(`devices/${state.selectedDevice}/commands`);if(generation===loadGeneration)state.commands=commands;}
      }
    }
    if(generation===loadGeneration){state.lastUpdated=new Date();state.error='';}
  }catch(error){if(generation===loadGeneration){state.error=error.message;state.connection=false;}}
  finally{if(generation===loadGeneration){state.loading=false;state.refreshBusy=false;render();}}
}
async function bootstrap(){
  if(!api.session)return;
  try{state.user=await api.request('auth/me');if(['/', '/login','/register'].includes(route()))navigate('/overview');await loadWorkspace();}
  catch(error){if(error.status===401){api.clearSession();state.user=null;navigate('/login');}else{state.error=error.message;toast(error.message,'error');render();}}
}

let previousFocus=null;
let modalBusy=false;
function closeModal(){if(modalBusy)return;modalRoot.innerHTML='';modalHandler=null;previousFocus?.focus?.({preventScroll:true});}
function openModal(title, html, {onSubmit=null, submit='Lưu thay đổi', wide=false, intro=''}={}){
  previousFocus=document.activeElement;modalHandler=onSubmit;modalBusy=false;
  modalRoot.innerHTML=`<div class="modal-backdrop"><section class="modal ${wide?'wide':''}" role="dialog" aria-modal="true" aria-labelledby="modal-title"><div class="modal-header"><h2 id="modal-title">${esc(title)}</h2><button class="icon-btn" data-action="close-modal" aria-label="Đóng hộp thoại">${icon('close')}</button></div>${intro?`<p class="intro">${intro}</p>`:''}<form id="modal-form"><div id="modal-error" role="alert"></div>${html}<div class="modal-footer">${button('Đóng','close-modal')} ${onSubmit?`<button class="btn primary" type="submit">${submit}</button>`:''}</div></form></section></div>`;
  setTimeout(()=>modalRoot.querySelector('input,select,textarea,button')?.focus(),0);
}
function confirmAction(title, description, callback, {typed=null, reason=false}={}){
  openModal(title,`<p class="intro">${description}</p>${typed?field(`Nhập <strong>${esc(typed)}</strong> để xác nhận`,'confirmation','text','','required autocomplete="off"'):''}${reason?area('Lý do thực hiện','reason','','required maxlength="1000"'):''}`,{submit:'Xác nhận',onSubmit:async form=>{
    if(typed&&form.get('confirmation')!==typed)throw new Error('Tên xác nhận chưa khớp.');
    await callback(form);toast('Thao tác đã được ghi nhận trên server.');await loadWorkspace();
  }});
}
function showSecret(data){
  const command=`& ([scriptblock]::Create((irm '${SERVER}/static/install.ps1'))) -Server ${SERVER} -Token '${String(data.secret_token).replaceAll("'","''")}' -DeviceId ${safeNumber(data.device_id)}`;
  openModal('Kết nối agent với thiết bị',`<p class="intro">Mở PowerShell với quyền quản trị trên máy tính của bạn và chạy câu lệnh bên dưới.</p><pre class="code-block">${esc(command)}</pre><div class="gap-top">${button('Sao chép câu lệnh','copy','primary','copy',`data-text="${esc(command)}"`)}</div><p class="secret-note">Token bí mật chỉ được hiển thị trong lần này. Không chia sẻ công khai.</p><details><summary class="text-xs muted">Xem token và mã thiết bị</summary><pre class="code-block gap-top">Device ID: ${esc(data.device_id)}\nToken: ${esc(data.secret_token)}</pre></details>`,{wide:true});
}
async function openGroupMembers(id){
  const group=state.groups.find(g=>String(g.group_id)===String(id));if(!group)throw new Error('Không tìm thấy nhóm.');
  const members=state.demo?demo.members.filter(m=>m.role!=='Owner').map((m,i)=>({...m,access_id:i+1,group_id:Number(id)})):await api.request(`groups/${safeNumber(id)}/members`);
  const table=members.length?`<div class="table-scroll"><table><thead><tr><th>Thành viên</th><th>Quyền trong nhóm</th><th></th></tr></thead><tbody>${members.map(m=>`<tr><td>${esc(m.full_name||m.username)}</td><td>${esc(m.role)}</td><td>${admin()?button('Thu hồi','remove-group-member','sm danger','trash',`data-id="${m.user_id}" data-group="${id}"`):''}</td></tr>`).join('')}</tbody></table></div>`:empty('Chưa cấp quyền theo nhóm','Owner và Admin vẫn có quyền truy cập toàn không gian làm việc.','users');
  openModal('Quyền truy cập · '+group.group_name,table+(admin()?`<div class="gap-top">${select('Thành viên','user_id',state.members.filter(m=>!['Owner','Admin'].includes(m.role)).map(m=>[m.user_id,m.full_name||m.username]),'','required')}${select('Vai trò trong nhóm','role',[['Viewer','Viewer — chỉ xem'],['Manager','Manager — quản lý thiết bị']])}<p class="muted text-xs">Chọn lại thành viên đã có quyền để đổi vai trò.</p></div>`:''),{wide:true,onSubmit:admin()?async form=>{
    await api.request(`groups/${id}/members`,{method:'POST',body:{user_id:safeNumber(form.get('user_id')),role:form.get('role')}});toast('Đã cập nhật quyền trong nhóm.');await loadWorkspace();await openGroupMembers(id);return false;
  }:null,submit:'Cấp / cập nhật quyền'});
}
async function copyText(text){try{await navigator.clipboard.writeText(text);toast('Đã sao chép vào clipboard.');}catch{toast('Trình duyệt chưa cho phép sao chép. Hãy chọn và sao chép nội dung trực tiếp.','error');}}
async function logout(){
  if(logoutBusy)return;logoutBusy=true;const token=api.session?.refresh_token;
  state.demo=false;state.user=null;api.clearSession();resetWorkspace();closeModal();navigate('/login');render();
  if(token){try{await api.request('auth/logout',{method:'POST',body:{refresh_token:token},auth:false});}catch{toast('Đã đăng xuất trên trình duyệt. Server chưa xác nhận thu hồi phiên.','error');}}
  logoutBusy=false;
}
async function refreshCurrent(){
  if(!state.user&&api.session)return bootstrap();
  await loadWorkspace();
}
const writeActions=new Set(['add-device','edit-device','toggle-monitoring','device-command','kill-process','delete-device','regenerate-token','create-org','join-org','leave-org','add-group','edit-group','delete-group','add-invitation','member-role','remove-member','remove-group-member','add-rule','edit-rule','delete-rule','ack-alert','resolve-alert']);

async function action(name, element){
  const id=element?.dataset.id;const value=element?.dataset.value;
  if(state.demo&&writeActions.has(name)){toast('Đây là chế độ minh họa. Đăng nhập để thao tác với dữ liệu thật.');return;}
  if(name==='demo'){goDemo();return;}
  if(name==='exit-demo'){state.demo=false;state.user=null;resetWorkspace();navigate('/login');return;}
  if(name==='close-modal'){closeModal();return;}
  if(name==='logout'){await logout();return;}
  if(name==='theme'){state.theme=state.theme==='dark'?'light':'dark';localStorage.setItem('dms.theme',state.theme);render();return;}
  if(name==='mobile-menu'){mobileNavOpen=!mobileNavOpen;render();return;}
  if(name==='show-password'){const input=element.parentElement.querySelector('input');input.type=input.type==='password'?'text':'password';return;}
  if(name==='refresh'){await refreshCurrent();return;}
  if(name==='check-health'){const response=await api.request('health',{auth:false});state.connection=response.status==='ok';toast(state.connection?'Server DMS đang hoạt động.':'Chưa nhận được trạng thái hợp lệ.');render();return;}
  if(name==='copy'){await copyText(element.dataset.text);return;}
  if(name==='switch-org'){resetOrgData();state.orgId=safeNumber(id);navigate('/overview');await loadWorkspace();return;}
  if(name==='page'){state.page=Math.max(1,Number(value)||1);render();if(!state.demo)await loadWorkspace({silent:true});return;}
  if(name==='process-sort'){state.processSort=value;render();return;}
  if(name==='process-select'){state.processSelected=state.processSelected===Number(id)?null:Number(id);render();return;}
  if(name==='overview-sort'){state.overviewSort=value;render();return;}
  if(name==='reload-web'){location.reload();return;}
  if(name==='range'){state.range=value;state.metrics=[];render();await loadChart();render();return;}
  if(name==='chart-cpu'){state.chartCpu=!state.chartCpu;render();return;}
  if(name==='chart-ram'){state.chartRam=!state.chartRam;render();return;}
  if(name==='detail-tab'){state.detailTab=value;render();if(state.selectedDevice){await loadDetail(state.selectedDevice);render();}return;}
  if(name==='alert-tab'){state.alertTab=value;render();return;}
  if(name==='alert-filter'){state.alertFilter=value;render();return;}
  if(name==='org-tab'){state.orgTab=value;render();if(value==='invitations'&&!state.demo){state.invitations=await api.request(`orgs/${state.orgId}/invitations`);render();}return;}
  if(name==='group-members'){await openGroupMembers(id);return;}
  if(name==='export-devices'){csvDownload('dms-thiet-bi.csv',[['Thiết bị','Trạng thái','Nhóm','CPU %','RAM %','Heartbeat'],...filteredDevices().map(d=>{const info=state.details[d.device_id]||d;return[d.device_name,d.status,d.group_name,info.cpu_usage,info.ram_usage,d.last_heartbeat];})]);return;}
  if(name==='export-metrics'){csvDownload('dms-hieu-nang.csv',[['Thời gian','CPU %','RAM %','Ổ đĩa %','Mạng vào KB/s','Mạng ra KB/s'],...state.metrics.map(m=>[m.created_at||m.hour_bucket,m.cpu_usage??m.cpu_avg,m.ram_usage??m.ram_avg,m.disk_usage??m.disk_avg,m.net_in_kbps??m.net_in_avg_kbps,m.net_out_kbps??m.net_out_avg_kbps])]);return;}
  if(name==='export-commands'){csvDownload('dms-lich-su-lenh.csv',[['ID','Lệnh','Trạng thái','Lý do','Thời gian','Kết quả'],...state.commands.map(c=>[c.command_id,c.command_type,c.status,c.reason,c.created_at,c.output])]);return;}
  if(name==='create-org'){
    openModal('Tạo không gian mới',field('Tên không gian làm việc','org_name','text','','required maxlength="100"')+area('Mô tả','description'),{submit:'Tạo không gian',onSubmit:async f=>{const o=await api.request('orgs',{method:'POST',body:{org_name:f.get('org_name').trim(),description:f.get('description')}});state.orgId=o.org_id;state.orgTab='groups';toast('Đã tạo không gian làm việc.');await loadWorkspace();}});return;
  }
  if(name==='join-org'){
    openModal('Tham gia không gian',field('Mã mời gồm 6 ký tự','invite_code','text','','required minlength="6" maxlength="6" pattern="[A-Za-z0-9]{6}" autocomplete="off"'),{submit:'Tham gia',onSubmit:async f=>{const o=await api.request('orgs/join',{method:'POST',body:{invite_code:f.get('invite_code').toUpperCase()}});state.orgId=o.org_id;state.orgTab='groups';toast('Đã tham gia không gian làm việc.');await loadWorkspace();}});return;
  }
  if(name==='leave-org'){const o=state.orgs.find(o=>String(o.org_id)===String(id));confirmAction('Rời không gian',`Bạn sẽ mất quyền truy cập dữ liệu trong ${esc(o?.org_name)}.`,async()=>{await api.request(`orgs/${safeNumber(id)}/leave`,{method:'POST'});state.orgId=null;},{typed:o?.org_name});return;}
  if(name==='add-device'){
    if(!state.orgId){toast('Hãy tạo hoặc tham gia một không gian làm việc trước.','error');return;}
    openModal('Thêm thiết bị mới',field('Tên thiết bị','device_name','text','','required maxlength="100" placeholder="Ví dụ: DESIGN-WS-01"')+select('Nhóm thiết bị','group_id',[['','Chưa phân nhóm'],...state.groups.map(g=>[g.group_id,g.group_name])])+field('Ngưỡng ngoại tuyến (phút)','offline_threshold_minutes','number',5,'required min="1" max="1440"')+area('Mô tả','description'),{submit:'Tạo thiết bị & lấy lệnh cài',onSubmit:async f=>{const created=await api.request(`orgs/${state.orgId}/devices`,{method:'POST',body:{device_name:f.get('device_name').trim(),group_id:f.get('group_id')?safeNumber(f.get('group_id')):null,description:f.get('description'),offline_threshold_minutes:Number(f.get('offline_threshold_minutes'))}});await loadWorkspace();showSecret(created);return false;}});return;
  }
  if(name==='edit-device'){
    const d=currentDevice();openModal('Chỉnh sửa thiết bị',field('Tên thiết bị','device_name','text',d.device_name,'required maxlength="100"')+select('Nhóm thiết bị','group_id',[[0,'Chưa phân nhóm'],...state.groups.map(g=>[g.group_id,g.group_name])],d.group_id||0)+field('Ngưỡng ngoại tuyến (phút)','offline_threshold_minutes','number',d.offline_threshold_minutes||5,'required min="1" max="1440"')+area('Mô tả','description',d.description||'')+`<details><summary class="text-xs muted">Cấu hình theo dõi dịch vụ / tiến trình (JSON)</summary><div class="gap-top">${area('Watch config','watch_config',JSON.stringify(d.watch_config||{services:[],processes:[]},null,2),'class="mono"')}</div></details>`,{onSubmit:async f=>{let watch;try{watch=JSON.parse(f.get('watch_config'));}catch{throw new Error('Watch config phải là JSON hợp lệ.');}if(!watch||Array.isArray(watch)||typeof watch!=='object')throw new Error('Watch config phải là một object.');await api.request(`devices/${d.device_id}`,{method:'PATCH',body:{device_name:f.get('device_name').trim(),group_id:Number(f.get('group_id')),description:f.get('description'),offline_threshold_minutes:Number(f.get('offline_threshold_minutes')),watch_config:watch}});toast('Đã cập nhật thiết bị.');await loadWorkspace();}});return;
  }
  if(name==='toggle-monitoring'){
    const d=currentDevice();confirmAction(d.is_monitoring?'Tạm dừng theo dõi':'Bật theo dõi',d.is_monitoring?'Agent sẽ ngừng thu thập tài nguyên và tiến trình, nhưng vẫn giữ kết nối để nhận lệnh.':'Agent sẽ tiếp tục thu thập tài nguyên và tiến trình.',async()=>{await api.request(`devices/${d.device_id}/toggle-monitoring`,{method:'POST',body:{is_monitoring:!d.is_monitoring}});});return;
  }
  if(name==='device-command'||name==='kill-process'){
    const d=currentDevice();const kill=name==='kill-process';
    openModal(kill?'Dừng tiến trình':'Điều khiển thiết bị',`<p class="intro">Thiết bị: <strong>${esc(d.device_name)}</strong>. Lệnh sẽ được thực thi khi agent nhận được yêu cầu.</p>${kill?`<p class="notice">${esc(element.dataset.name)} · PID ${esc(id)}</p>${select('Phạm vi dừng','kill_scope',[['pid','Chỉ PID đã chọn'],['application','Tất cả tiến trình cùng tên trên máy']])}<p class="text-sm muted">Nếu chọn tất cả: đóng mọi tiến trình/cửa sổ cùng tên, kể cả phiên người dùng khác; có thể mất dữ liệu chưa lưu.</p>`:select('Loại lệnh','command_type',[['REBOOT','Khởi động lại máy tính'],['RESTART_SERVICE','Khởi động lại Windows Service']])}${!kill?field('Tên Windows Service (chỉ dùng cho lệnh restart service)','service_name','text','','placeholder="Ví dụ: Spooler"')+field('Độ trễ khởi động lại (giây)','delay_seconds','number',10,'min="0" max="3600"'):''}${area('Lý do thực hiện','reason','','required maxlength="1000"')}`,{submit:'Xác nhận gửi lệnh',onSubmit:async f=>{const command=kill?'KILL_PROCESS':f.get('command_type');const params=kill?(f.get('kill_scope')==='application'?{process_name:element.dataset.name}:{pid:safeNumber(id)}):command==='REBOOT'?{delay_seconds:Number(f.get('delay_seconds'))}:{service_name:f.get('service_name').trim()};if(command==='RESTART_SERVICE'&&!params.service_name)throw new Error('Vui lòng nhập tên Windows Service.');const created=await api.request(`devices/${d.device_id}/commands`,{method:'POST',body:{command_type:command,parameters:params,reason:f.get('reason').trim()}});toast(commandFeedback(created));state.detailTab='commands';void trackCommand(created,d.device_id);await loadWorkspace();}});return;
  }
  if(name==='delete-device'){const d=currentDevice();confirmAction('Xóa thiết bị & gỡ agent',`Thiết bị <strong>${esc(d.device_name)}</strong> sẽ được lưu trữ. Server gửi lệnh UNINSTALL để gỡ agent khi máy kết nối lại.`,async()=>{await api.request(`devices/${d.device_id}`,{method:'DELETE'});state.selectedDevice=null;navigate('/devices');},{typed:d.device_name});return;}
  if(name==='regenerate-token'){const d=currentDevice();openModal('Tạo lại token agent',`<p class="intro">Token cũ của <strong>${esc(d.device_name)}</strong> sẽ mất hiệu lực. Bạn cần cập nhật lại agent.</p>${field('Nhập tên thiết bị để xác nhận','confirmation','text','','required')}`,{submit:'Tạo token mới',onSubmit:async f=>{if(f.get('confirmation')!==d.device_name)throw new Error('Tên thiết bị chưa khớp.');const created=await api.request(`devices/${d.device_id}/regenerate-token`,{method:'POST'});showSecret(created);return false;}});return;}
  if(name==='add-group'||name==='edit-group'){
    const group=state.groups.find(g=>String(g.group_id)===String(id));openModal(group?'Chỉnh sửa nhóm':'Tạo nhóm thiết bị',field('Tên nhóm','group_name','text',group?.group_name||'','required maxlength="100"')+area('Mô tả','description',group?.description||''),{onSubmit:async f=>{await api.request(group?`groups/${id}`:`orgs/${state.orgId}/groups`,{method:group?'PATCH':'POST',body:{group_name:f.get('group_name').trim(),description:f.get('description')}});toast(group?'Đã cập nhật nhóm.':'Đã tạo nhóm thiết bị.');await loadWorkspace();}});return;
  }
  if(name==='delete-group'){const group=state.groups.find(g=>String(g.group_id)===String(id));confirmAction('Xóa nhóm thiết bị','Thiết bị trong nhóm không bị xóa, nhưng sẽ chuyển về chưa phân nhóm.',async()=>{await api.request(`groups/${safeNumber(id)}`,{method:'DELETE'});},{typed:group?.group_name});return;}
  if(name==='add-invitation'){
    openModal('Tạo mã mời',select('Vai trò khi tham gia','default_role',[['Viewer','Viewer — chỉ xem'],['Manager','Manager — quản lý']])+field('Số lượt sử dụng tối đa','max_uses','number','','min="1" placeholder="Để trống nếu không giới hạn"')+field('Thời điểm hết hạn','expires_at','datetime-local',''),{submit:'Tạo mã mời',onSubmit:async f=>{const invitation=await api.request(`orgs/${state.orgId}/invitations`,{method:'POST',body:{default_role:f.get('default_role'),max_uses:f.get('max_uses')?Number(f.get('max_uses')):null,expires_at:f.get('expires_at')?new Date(f.get('expires_at')).toISOString():null}});await loadWorkspace();openModal('Mã mời đã sẵn sàng',`<p class="intro">Chia sẻ mã với người bạn muốn mời vào không gian làm việc.</p><pre class="code-block" style="font-size:27px;text-align:center;letter-spacing:.15em">${esc(invitation.invite_code)}</pre><div class="gap-top">${button('Sao chép mã','copy','primary','copy',`data-text="${esc(invitation.invite_code)}"`)}</div>`);return false;}});return;
  }
  if(name==='member-role'){
    const member=state.members.find(m=>String(m.user_id)===String(id));const roles=[['Viewer','Viewer'],['Manager','Manager'],...(org()?.role==='Owner'?[['Admin','Admin']]:[])];openModal('Đổi vai trò thành viên',`<p class="intro">${esc(member.full_name||member.username)}</p>`+select('Vai trò không gian làm việc','role',roles,member.role),{onSubmit:async f=>{await api.request(`orgs/${state.orgId}/members/${safeNumber(id)}`,{method:'PATCH',body:{role:f.get('role')}});toast('Đã cập nhật vai trò.');await loadWorkspace();}});return;
  }
  if(name==='remove-member'){const member=state.members.find(m=>String(m.user_id)===String(id));confirmAction('Xóa thành viên',`${esc(member?.full_name||member?.username)} sẽ mất quyền truy cập không gian làm việc và các nhóm thiết bị.`,async()=>{await api.request(`orgs/${state.orgId}/members/${safeNumber(id)}`,{method:'DELETE'});});return;}
  if(name==='remove-group-member'){confirmAction('Thu hồi quyền trong nhóm','Thành viên sẽ không còn quyền truy cập nhóm này, trừ khi có vai trò Owner hoặc Admin.',async()=>{await api.request(`groups/${safeNumber(element.dataset.group)}/members/${safeNumber(id)}`,{method:'DELETE'});});return;}
  if(name==='add-rule'){
    const scopes=[...state.devices.map(d=>['device:'+d.device_id,'Thiết bị · '+d.device_name]),...state.groups.map(g=>['group:'+g.group_id,'Nhóm · '+g.group_name])];
    if(!scopes.length){toast('Thêm thiết bị hoặc nhóm trước khi tạo quy tắc.','error');return;}
    openModal('Tạo quy tắc cảnh báo',`<div class="form-grid">${field('Tên quy tắc','rule_name','text','','required maxlength="150"')}${select('Phạm vi áp dụng','scope',scopes)}${select('Chỉ số theo dõi','metric_type',[['CPU','CPU (%)'],['RAM','RAM (%)'],['GPU','GPU / VGA (%)'],['DISK','Ổ đĩa (%)'],['NET_IN','Mạng tải xuống (KB/s)'],['NET_OUT','Mạng tải lên (KB/s)'],['OFFLINE','Ngoại tuyến (phút)'],['SERVICE','Dịch vụ ngừng chạy'],['PROCESS_MISSING','Tiến trình không còn chạy']])}${select('Điều kiện','condition',[['>','Lớn hơn (>)'],['>=','Lớn hơn hoặc bằng (≥)'],['<','Nhỏ hơn (<)'],['<=','Nhỏ hơn hoặc bằng (≤)']])}${thresholdControl()}${select('Mức độ','severity',[['WARNING','Cảnh báo'],['CRITICAL','Nghiêm trọng'],['INFO','Thông tin']])}${field('Thời gian vượt ngưỡng (phút)','duration_minutes','number',1,'required min="1"')}${field('Khoảng cách cảnh báo (phút)','cooldown_minutes','number',30,'required min="1"')}${field('Tên Windows Service (nếu chọn dịch vụ)','service_name')}${field('Tên tiến trình (nếu chọn tiến trình)','process_name')}</div>`,{wide:true,submit:'Tạo quy tắc',onSubmit:async f=>{const [type,scopeId]=f.get('scope').split(':');const kind=f.get('metric_type');const body={rule_name:f.get('rule_name').trim(),metric_type:kind,condition:f.get('condition'),severity:f.get('severity'),duration_minutes:Number(f.get('duration_minutes')),cooldown_minutes:Number(f.get('cooldown_minutes')),[type==='device'?'device_id':'group_id']:safeNumber(scopeId)};if(kind==='SERVICE'){body.service_name=f.get('service_name').trim();if(!body.service_name)throw new Error('Nhập tên dịch vụ.');}else if(kind==='PROCESS_MISSING'){body.process_name=f.get('process_name').trim();if(!body.process_name)throw new Error('Nhập tên tiến trình.');}else{if(f.get('threshold')==='')throw new Error('Nhập ngưỡng cảnh báo.');body.threshold=Number(f.get('threshold'));}await api.request(`orgs/${state.orgId}/alert-rules`,{method:'POST',body});toast('Đã tạo quy tắc cảnh báo.');state.alertTab='rules';await loadWorkspace();}});return;
  }
  if(name==='edit-rule'){
    const r=state.rules.find(r=>String(r.rule_id)===String(id));if(!r)return;
    const numeric=!['SERVICE','PROCESS_MISSING'].includes(r.metric_type);
    openModal('Chỉnh quy tắc cảnh báo', '<p class="intro">'+esc(r.rule_name||r.metric_type)+' · '+esc(r.metric_type)+'</p>'+ (numeric?thresholdControl(r.threshold,r.metric_type):'')+
      field('Thời gian vượt ngưỡng (phút)','duration_minutes','number',r.duration_minutes,'required min="1"')+
      field('Khoảng cách cảnh báo (phút)','cooldown_minutes','number',r.cooldown_minutes,'required min="1"')+
      select('Trạng thái','is_active', [['true','Đang áp dụng'],['false','Tạm tắt']],String(r.is_active)),
      {onSubmit:async f=>{
        const body={duration_minutes:Number(f.get('duration_minutes')),cooldown_minutes:Number(f.get('cooldown_minutes')),is_active:f.get('is_active')==='true'};
        if(numeric)body.threshold=Number(f.get('threshold'));
        await api.request(`orgs/${state.orgId}/alert-rules/${safeNumber(id)}`,{method:'PATCH',body});
        toast('Đã lưu ngưỡng. Web và Android dùng chung quy tắc.');await loadWorkspace();
      }});return;
  }
  if(name==='delete-rule'){confirmAction('Xóa quy tắc cảnh báo','Quy tắc sẽ ngừng áp dụng; lịch sử cảnh báo đã ghi vẫn được giữ lại.',async()=>{await api.request(`alert-rules/${safeNumber(id)}`,{method:'DELETE'});});return;}
  if(name==='ack-alert'||name==='resolve-alert'){
    confirmAction(name==='ack-alert'?'Tiếp nhận cảnh báo':'Đánh dấu đã xử lý',name==='ack-alert'?'Ghi nhận bạn đã tiếp nhận cảnh báo này.':'Chỉ xác nhận khi bạn đã kiểm tra và xử lý vấn đề trên thiết bị.',async()=>{await api.request(`alerts/${safeNumber(id)}/${name==='ack-alert'?'acknowledge':'resolve'}`,{method:'POST'});});return;
  }
}

document.addEventListener('click',async event=>{
  const section=event.target.closest('[data-section]');if(section){event.preventDefault();document.getElementById(section.dataset.section)?.scrollIntoView({behavior:'smooth'});return;}
  const element=event.target.closest('[data-action]');if(!element)return;
  event.preventDefault();if(element.disabled)return;
  try{await action(element.dataset.action,element);}catch(error){toast(error.message,'error');}
});
document.addEventListener('input',event=>{
  const wrap=event.target.closest('.threshold-control');
  if(wrap){
    const input=wrap.querySelector('[name="threshold"]'),slider=wrap.querySelector('[data-threshold-slider]');
    if(event.target===slider)input.value=slider.value;
    else if(event.target===input)slider.value=input.value;
  }

  if(event.target.id==='device-search'){state.search=event.target.value;state.page=1;render();}
});
document.addEventListener('change',async event=>{
  if(event.target.name==='alert_language'){state.alertLanguage=event.target.value;localStorage.setItem('dms.alertLanguage',state.alertLanguage);render();return;}

  if(event.target.name==='metric_type'){
    const wrap=modalRoot.querySelector('.threshold-control');
    if(wrap){
      const kind=event.target.value, input=wrap.querySelector('[name="threshold"]'), slider=wrap.querySelector('[data-threshold-slider]');
      const hidden=['SERVICE','PROCESS_MISSING'].includes(kind);wrap.hidden=hidden;input.required=!hidden;
      const pct=['CPU','RAM','GPU','DISK'].includes(kind),min=kind==='OFFLINE'?1:0,max=pct?100:kind==='OFFLINE'?1440:10000;
      input.min=slider.min=min;input.max=slider.max=max;input.value=slider.value=Math.max(min,Math.min(max,Number(input.value)||0));
      wrap.querySelector('label').firstChild.textContent='Ngưỡng ('+(pct?'%':kind==='OFFLINE'?'phút':'KB/s')+')';
    }
  }

  try{
    if(event.target.id==='org-picker'){const id=safeNumber(event.target.value);resetOrgData();state.orgId=id;await loadWorkspace();}
    if(event.target.id==='status-filter'){state.statusFilter=event.target.value;state.page=1;render();}
    if(event.target.id==='group-filter'){state.groupFilter=event.target.value;state.page=1;render();}
    if(event.target.id==='chart-device'||event.target.id==='activity-device'){
      chartGeneration++;state.selectedDevice=safeNumber(event.target.value);state.metrics=[];state.commands=[];render();await loadDetail(state.selectedDevice);render();
    }
  }catch(error){toast(error.message,'error');}
});
document.addEventListener('submit',async event=>{
  const form=event.target;
  if(form.id==='auth-form'){
    event.preventDefault();const data=new FormData(form);const submit=form.querySelector('[type=submit]');const error=$('#auth-error');error.innerHTML='';submit.disabled=true;
    try{
      state.demo=false;const register=form.dataset.register==='true';const password=data.get('password');
      if(register&&new TextEncoder().encode(password).length>72)throw new Error('Mật khẩu không được vượt quá 72 byte UTF-8.');
      if(register)await api.request('auth/register',{method:'POST',auth:false,body:{username:data.get('username').trim(),email:data.get('email').trim(),password,full_name:data.get('full_name').trim()}});
      const tokens=await api.request('auth/login',{method:'POST',auth:false,body:{identifier:register?data.get('username').trim():data.get('identifier').trim(),password}});
      api.setSession(tokens);resetWorkspace();state.user=await api.request('auth/me');navigate('/overview');await loadWorkspace();
    }catch(e){if(error.isConnected)error.innerHTML=`<div class="form-error">${esc(e.message)}</div>`;}
    finally{submit.disabled=false;}
  }
  if(form.id==='modal-form'){
    event.preventDefault();if(!modalHandler||modalBusy)return;modalBusy=true;const handler=modalHandler;const submit=form.querySelector('[type=submit]');submit.disabled=true;$('#modal-error').innerHTML='';
    try{const keep=await handler(new FormData(form));modalBusy=false;if(keep!==false)closeModal();}
    catch(error){if($('#modal-error'))$('#modal-error').innerHTML=`<div class="form-error">${esc(error.message)}</div>`;}
    finally{modalBusy=false;if(submit?.isConnected)submit.disabled=false;}
  }
});
document.addEventListener('keydown',event=>{
  if(!modalRoot.children.length)return;
  if(event.key==='Escape'){closeModal();return;}
  if(event.key==='Tab'){
    const focusable=[...modalRoot.querySelectorAll('button:not([disabled]),input:not([disabled]),select:not([disabled]),textarea:not([disabled]),a[href],summary')].filter(el=>el.offsetParent!==null);
    const first=focusable[0],last=focusable.at(-1);
    if(event.shiftKey&&document.activeElement===first){event.preventDefault();last?.focus();}else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first?.focus();}
  }
});
window.addEventListener('hashchange',async()=>{
  mobileNavOpen=false;closeModal();window.scrollTo(0,0);
  chartGeneration++;
  if(route().startsWith('/devices/')){state.selectedDevice=Number(route().split('/')[2]);state.detailTab='overview';state.metrics=[];}
  render();if(state.user&&!state.demo&&route().startsWith('/')&&!['/','/login','/register'].includes(route()))await loadWorkspace();
});
window.addEventListener('online',()=>{if(state.user&&!state.demo)loadWorkspace({silent:true});});
render();
if(new URLSearchParams(location.hash.split('?')[1]||'').get('demo')==='1')goDemo();else bootstrap();
pollTimer=setInterval(()=>{if(state.user&&!state.demo&&!state.refreshBusy&&!document.hidden&&!modalRoot.children.length&&!['/','/login','/register'].includes(route()))loadWorkspace({silent:true});},15000);
window.addEventListener('pagehide',()=>clearInterval(pollTimer));
