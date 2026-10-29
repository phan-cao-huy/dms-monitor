package com.example.myapplication.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.api.RetrofitClient
import com.example.myapplication.data.model.*
import com.example.myapplication.util.MonitoringSupport
import kotlinx.coroutines.launch

@Composable
fun EmailChannelsScreen(org: Organization?) {
    if(org==null){Text(featureText("Chọn không gian làm việc trước.","Select a workspace first."));return}
    if(org.role !in listOf("Owner","Admin")){Text(featureText("Chỉ Owner/Admin được quản lý kênh email.","Only Owner/Admin can manage email channels."));return}
    key(org.id) { EmailChannelsContent(org.id) }
}

@Composable private fun EmailChannelsContent(orgId:String) {
    val api=RetrofitClient.apiService
    val status=remember {FeatureStatus()}
    val scope=rememberCoroutineScope()
    var channels by remember {mutableStateOf<List<EmailChannel>>(emptyList())}
    var editing by remember {mutableStateOf<EmailChannel?>(null)}
    var create by remember {mutableStateOf(false)}
    var deleting by remember {mutableStateOf<EmailChannel?>(null)}
    var history by remember {mutableStateOf<List<EmailDelivery>?>(null)}
    suspend fun reload(){channels=api.emailChannels(orgId).required()}
    RefreshFeature(orgId,!status.busy&&!create&&editing==null&&deleting==null&&history==null){status.refresh{reload()}}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Text(featureText("Kênh email","Email channels"),style=MaterialTheme.typography.headlineSmall)
            Text(featureText("Gửi cảnh báo mới của không gian. Mỗi cảnh báo thử tối đa 3 lần; độc lập với thông báo điện thoại.","Send new workspace alerts. Up to 3 attempts per alert; independent of phone notifications."))
            FeatureMessages(status)
            Button(onClick={status.error=null;create=true},enabled=!status.busy){Text(featureText("Thêm kênh email","Add email channel"))}
        }
        if(channels.isEmpty())item {Text(featureText("Chưa có kênh email.","No email channels yet."))}
        items(channels,key={it.id}) { channel ->
            OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(channel.name,style=MaterialTheme.typography.titleMedium)
                Text(channel.config.host+":"+channel.config.port+" · TLS")
                Text(channel.config.recipients.joinToString("\n"))
                Text((if(channel.enabled)featureText("Đang bật","Enabled") else featureText("Tạm tắt","Disabled"))+" · "+if(channel.config.language=="en")"English" else "Tiếng Việt")
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    TextButton(enabled=!status.busy,onClick={scope.launch{status.perform{api.testEmailChannel(orgId,channel.id).verify();status.message="SMTP đã chấp nhận email thử. Kiểm tra cả thư rác." to "SMTP accepted the test email. Check your spam folder too."}}}){Text(featureText("Gửi thử","Send test"))}
                    TextButton(enabled=!status.busy,onClick={status.error=null;editing=channel}){Text(featureText("Sửa","Edit"))}
                    TextButton(enabled=!status.busy,onClick={scope.launch{status.perform{history=api.emailDeliveries(orgId,channel.id).required()}}}){Text(featureText("Lịch sử","History"))}
                }
                TextButton(enabled=!status.busy,onClick={deleting=channel}){Text(featureText("Xóa kênh","Delete channel"),color=MaterialTheme.colorScheme.error)}
            }}
        }
    }
    if(create||editing!=null)key(editing?.id,create){
        EmailEditor(editing,status,{if(!status.busy){create=false;editing=null}},{body->scope.launch{status.perform{
            if(editing==null)api.createEmailChannel(orgId,body).verify() else api.updateEmailChannel(orgId,editing!!.id,body).verify()
            reload();create=false;editing=null;status.message="Đã lưu kênh email." to "Email channel saved."
        }}})
    }
    deleting?.let{channel->ConfirmFeature(featureText("Xóa kênh: ","Delete channel: ")+channel.name,status,{deleting=null},{scope.launch{status.perform{api.deleteEmailChannel(orgId,channel.id).verify();reload();deleting=null}}})}
    history?.let{rows->FeatureDialog(featureText("Lịch sử gửi email","Email delivery history"),status,{history=null}){
        Text(featureText("Chỉ gồm cảnh báo tự động, không gồm email gửi thử.","Automatic alerts only; test emails are excluded."))
        if(rows.isEmpty())Text(featureText("Chưa có lượt gửi.","No deliveries yet."))
        rows.forEach{row->HorizontalDivider();Text("#${row.alertId} · ${if(row.status=="SENT")featureText("Đã gửi","Sent") else if(row.attempts>=3)featureText("Thất bại","Failed") else featureText("Chờ thử lại","Retry pending")}")
            Text("${row.attempts} / 3 · ${MonitoringSupport.time(row.sentAt)}")
            row.lastError?.let{Text(if(it=="SMTP_AUTH_FAILED")featureText("Sai thông tin đăng nhập SMTP.","SMTP authentication failed.") else featureText("Không gửi được qua SMTP.","SMTP delivery failed."))}
        }
    }}
}

@Composable private fun EmailEditor(channel:EmailChannel?,status:FeatureStatus,onDismiss:()->Unit,onSave:(EmailChannelWrite)->Unit) {
    var name by remember {mutableStateOf(channel?.name?:"")}
    var host by remember {mutableStateOf(channel?.config?.host?:"smtp.gmail.com")}
    var port by remember {mutableStateOf((channel?.config?.port?:587).toString())}
    var username by remember {mutableStateOf(channel?.config?.username?:"")}
    var password by remember {mutableStateOf("")}
    var from by remember {mutableStateOf(channel?.config?.fromEmail?:"")}
    var recipients by remember {mutableStateOf(channel?.config?.recipients?.joinToString("\n")?:"")}
    var language by remember {mutableStateOf(channel?.config?.language?:"vi")}
    var enabled by remember {mutableStateOf(channel?.enabled?:true)}
    val addresses=recipients.split(Regex("[\\n,;]+")).map{it.trim()}.filter{it.isNotEmpty()}.distinct()
    val email=Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
    val valid=name.isNotBlank()&&name.length<=100&&Regex("[A-Za-z0-9.-]{1,253}").matches(host)&&username.isNotBlank()&&username.length<=255&&
        (channel!=null||password.isNotBlank())&&password.length<=1024&&email.matches(from.trim())&&addresses.size in 1..20&&addresses.all{email.matches(it)}
    val uri=LocalUriHandler.current
    FeatureDialog(featureText(if(channel==null)"Thêm kênh email" else "Chỉnh kênh email",if(channel==null)"Add email channel" else "Edit email channel"),status,onDismiss,
        onSave={onSave(EmailChannelWrite(name.trim(),enabled,EmailConfig(host.trim(),port.toInt(),username.trim(),MonitoringSupport.smtpPassword(host,password),from.trim(),addresses,language)))},valid=valid){
        Text(featureText("Gmail: bật xác minh 2 bước và tạo mật khẩu ứng dụng. Không nhập mật khẩu đăng nhập Google.","Gmail: enable 2-Step Verification and create an app password. Do not enter your Google account password."))
        TextButton(onClick={uri.openUri("https://myaccount.google.com/apppasswords")}){Text(featureText("Tạo mật khẩu ứng dụng","Create app password"))}
        OutlinedTextField(name,{name=it},label={Text(featureText("Tên kênh","Channel name"))},singleLine=true)
        OutlinedTextField(host,{host=it},label={Text(featureText("Máy chủ SMTP","SMTP host"))},singleLine=true)
        FeatureChoice(featureText("Cổng / mã hóa","Port / encryption"),port,listOf("587" to "587 · STARTTLS","465" to "465 · SSL/TLS"),{port=it})
        OutlinedTextField(username,{username=it},label={Text(featureText("Tên đăng nhập SMTP","SMTP username"))},singleLine=true)
        OutlinedTextField(password,{password=it},label={Text(featureText(if(channel==null)"Mật khẩu ứng dụng" else "Mật khẩu mới (trống để giữ nguyên)",if(channel==null)"App password" else "New password (blank keeps existing)"))},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),singleLine=true)
        OutlinedTextField(from,{from=it},label={Text(featureText("Email gửi","From email"))},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email))
        OutlinedTextField(recipients,{recipients=it},label={Text(featureText("Email nhận (mỗi dòng một địa chỉ, tối đa 20)","Recipients (one per line, up to 20)"))},minLines=2,maxLines=4)
        FeatureChoice(featureText("Ngôn ngữ email","Email language"),language,listOf("vi" to "Tiếng Việt","en" to "English"),{language=it})
        Row{Checkbox(enabled,{enabled=it});Text(featureText("Bật kênh email","Enable email channel"))}
    }
}
