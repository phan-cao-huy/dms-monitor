package com.example.myapplication.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.myapplication.ui.i18n.LocalAppStrings
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import retrofit2.Response
import java.util.Locale

@Composable internal fun featureText(vi: String,en: String)=if(LocalAppStrings.current.isEnglish)en else vi
internal fun featureDecimal(value: Number?): String = value?.toDouble()?.takeIf { it.isFinite() }?.let { String.format(Locale.getDefault(),"%.1f",it) } ?: "—"

internal class FeatureFailure(val vi: String,val en: String) : Exception()
internal fun Response<*>.verify() {
    if(isSuccessful)return
    val detail=try { JsonParser.parseString(errorBody()?.string()).asJsonObject.get("detail")?.takeIf { it.isJsonPrimitive }?.asString } catch(_:Exception){null}
    throw when {
        code()==401 -> FeatureFailure("Phiên đăng nhập hết hạn. Hãy đăng nhập lại.","Session expired. Sign in again.")
        code()==403 -> FeatureFailure("Bạn không có quyền thực hiện thao tác này.","You do not have permission for this action.")
        code()==429 -> FeatureFailure("Chưa đến lượt kiểm tra tiếp theo. Chờ 30–60 giây rồi thử lại.","Wait 30–60 seconds before testing again.")
        detail=="SMTP_AUTH_FAILED" -> FeatureFailure("Gmail từ chối đăng nhập. Kiểm tra email và mật khẩu ứng dụng.","SMTP authentication failed. Check the address and app password.")
        detail=="SMTP_CONNECTION_FAILED" -> FeatureFailure("Server không kết nối được SMTP.","Server could not connect to SMTP.")
        detail=="SMTP_DELIVERY_FAILED" -> FeatureFailure("SMTP không chấp nhận email. Kiểm tra người gửi/người nhận.","SMTP rejected the email. Check sender/recipients.")
        code()==422 -> FeatureFailure("Dữ liệu chưa hợp lệ hoặc vượt giới hạn. Kiểm tra các trường và phạm vi đã chọn.","Invalid input or limit exceeded. Check fields and selection.")
        code()==503 -> FeatureFailure("Server chưa sẵn sàng hoặc chưa cấu hình khóa mã hóa email.","Server is unavailable or email encryption is not configured.")
        else -> FeatureFailure("Thao tác thất bại (${code()}). Hãy thử lại.","Request failed (${code()}). Try again.")
    }
}
internal fun <T> Response<T>.required(): T { verify(); return body() ?: throw FeatureFailure("Server trả về dữ liệu rỗng.","Server returned an empty response.") }

@Stable internal class FeatureStatus {
    var loading by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<FeatureFailure?>(null)
    var loadError by mutableStateOf<FeatureFailure?>(null)
    var message by mutableStateOf<Pair<String,String>?>(null)
    suspend fun perform(action: suspend ()->Unit) {
        if(busy)return
        busy=true;error=null;message=null
        try { action() } catch(e:CancellationException){throw e}
        catch(e:FeatureFailure){error=e}
        catch(_:Exception){error=FeatureFailure("Không kết nối được server. Hãy thử lại.","Cannot reach server. Try again.")}
        finally {busy=false}
    }
    suspend fun refresh(action: suspend ()->Unit) {
        loading=true
        try {action();loadError=null} catch(e:CancellationException){throw e}
        catch(e:FeatureFailure){loadError=e}
        catch(_:Exception){loadError=FeatureFailure("Không tải được dữ liệu.","Could not load data.")}
        finally {loading=false}
    }
}

@Composable internal fun FeatureMessages(status: FeatureStatus) {
    if(status.busy||status.loading)LinearProgressIndicator(Modifier.fillMaxWidth())
    (status.error?:status.loadError)?.let { Text(featureText(it.vi,it.en),color=MaterialTheme.colorScheme.error) }
    status.message?.let { Text(featureText(it.first,it.second),color=MaterialTheme.colorScheme.primary) }
}

@Composable internal fun RefreshFeature(key: Any?, enabled: Boolean=true, action: suspend ()->Unit) {
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val latest by rememberUpdatedState(action)
    LaunchedEffect(key,enabled,lifecycle) {
        if(enabled)lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { while(true){latest();delay(30000)} }
    }
}

@Composable internal fun FeatureChoice(label: String,value: String,options: List<Pair<String,String>>,onSelect:(String)->Unit,enabled:Boolean=true) {
    var expanded by remember {mutableStateOf(false)}
    Column {
        Text(label,style=MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick={expanded=true},enabled=enabled,modifier=Modifier.fillMaxWidth()) {Text(options.find{it.first==value}?.second ?: value)}
            DropdownMenu(expanded&&enabled,onDismissRequest={expanded=false}) {
                options.forEach{(key,text)->DropdownMenuItem(text={Text(text)},onClick={onSelect(key);expanded=false})}
            }
        }
    }
}

@Composable internal fun FeatureDialog(title:String,status:FeatureStatus,onDismiss:()->Unit,onSave:(()->Unit)?=null,valid:Boolean=true,content:@Composable ColumnScope.()->Unit) {
    AlertDialog(onDismissRequest={if(!status.busy)onDismiss()},title={Text(title)},
        text={Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()).imePadding(),verticalArrangement=Arrangement.spacedBy(12.dp)) {content();FeatureMessages(status)}},
        confirmButton={if(onSave!=null)Button(onClick=onSave,enabled=valid&&!status.busy){Text(featureText("Lưu","Save"))} else TextButton(onClick=onDismiss){Text(featureText("Đóng","Close"))}},
        dismissButton={if(onSave!=null)TextButton(onClick=onDismiss,enabled=!status.busy){Text(featureText("Hủy","Cancel"))}})
}

@Composable internal fun ConfirmFeature(title:String,status:FeatureStatus,onDismiss:()->Unit,onConfirm:()->Unit) {
    AlertDialog(onDismissRequest={if(!status.busy)onDismiss()},title={Text(title)},text={Column{Text(featureText("Thao tác này sẽ xóa dữ liệu đã chọn trên server.","This will delete the selected server data."));FeatureMessages(status)}},
        confirmButton={Button(onClick=onConfirm,enabled=!status.busy){Text(featureText("Xóa","Delete"))}},
        dismissButton={TextButton(onClick=onDismiss,enabled=!status.busy){Text(featureText("Hủy","Cancel"))}})
}
