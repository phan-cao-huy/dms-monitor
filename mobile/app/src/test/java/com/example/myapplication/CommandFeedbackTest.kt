package com.example.myapplication

import com.example.myapplication.data.model.Command
import com.example.myapplication.util.CommandFeedback
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class CommandFeedbackTest{
    @Test fun tracksRealServerIdAndShowsCompletionOnlyForSuccess(){
        val c=Gson().fromJson("""{"command_id":123,"command_type":"KILL_PROCESS","parameters":{"pid":45},"status":"PENDING"}""",Command::class.java)
        assertEquals(123,c.id)
        assertFalse(CommandFeedback.terminal(c.status))
        assertTrue(CommandFeedback.text(c).contains("Đang thực thi lệnh"))
        assertEquals("Đã dừng tiến trình PID 45 thành công.",CommandFeedback.text(c.copy(status="SUCCESS")))
    }
    @Test fun wholeApplicationResultDoesNotClaimOnePid(){
        val c=Command(12,"KILL_PROCESS",parameters=mapOf("process_name" to "chrome.exe"),status="SUCCESS")
        assertTrue(CommandFeedback.text(c).contains("tất cả tiến trình chrome.exe"))
    }
    @Test fun failuresAndTimeoutsDoNotClaimSuccess(){
        val c=Command(1,"KILL_PROCESS",status="FAILED",errorMessage="Access denied")
        assertTrue(CommandFeedback.text(c).contains("Access denied"))
        assertTrue(CommandFeedback.terminal(c.status))
        assertTrue(CommandFeedback.text(c.copy(status="TIMEOUT")).contains("chưa xác nhận"))
        assertTrue(CommandFeedback.text(c.copy(status="SUCCESS"),true).contains("stopped successfully"))
    }
}
