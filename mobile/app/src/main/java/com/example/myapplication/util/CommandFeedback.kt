package com.example.myapplication.util

import com.example.myapplication.data.model.Command

object CommandFeedback {
    fun terminal(status:String?)=status in listOf("SUCCESS","FAILED","TIMEOUT")
    fun text(command:Command,en:Boolean=false):String {
        val pid=command.parameters?.get("pid")?.let{(it as? Number)?.toLong()?.toString()?:it.toString()}?:"—"
        return when(command.status){
            "SUCCESS" -> when(command.commandType){
                "KILL_PROCESS"->if(command.parameters?.get("process_name")!=null){
                    val name=command.parameters["process_name"].toString()
                    if(en)"All $name processes on this PC were stopped." else "Đã dừng tất cả tiến trình $name trên máy."
                } else if(en)"Process PID $pid stopped successfully." else "Đã dừng tiến trình PID $pid thành công."
                "REBOOT"->if(en)"Agent confirmed the reboot request." else "Agent đã xác nhận lệnh khởi động lại."
                "RESTART_SERVICE"->if(en)"Service restart completed." else "Đã khởi động lại dịch vụ."
                else->if(en)"Command completed." else "Lệnh đã hoàn tất."
            }
            "FAILED" -> (if(en)"Command failed: " else "Lệnh thất bại: ")+(command.errorMessage?:command.output?:if(en)"Agent reported an error." else "Agent báo lỗi.")
            "TIMEOUT" -> if(en)"Command timed out; no completion was confirmed." else "Lệnh hết thời gian chờ, chưa xác nhận thực hiện thành công."
            "EXECUTING" -> if(en)"Agent is processing the command…" else "Đang thực thi lệnh..."
            else -> if(en)"Command queued; waiting for the agent…" else "Đang thực thi lệnh..."
        }
    }
}
