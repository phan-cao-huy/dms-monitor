package com.example.myapplication

import com.example.myapplication.data.model.DeviceDetail
import com.example.myapplication.data.model.Metric
import com.example.myapplication.data.model.ProcessInfo
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class DevicePayloadTest {
    @Test fun readsEveryVolumeAndActualAgentProcessCpu() {
        val detail=Gson().fromJson("""{"device_id":25,"disks":[{"mountpoint":"C:","total_gb":256,"free_gb":76,"used_gb":180,"used_percent":70.3},{"mountpoint":"D:","total_gb":1000,"free_gb":80,"used_gb":920,"used_percent":92},{"mountpoint":"E:","used_percent":0}],"gpu_usage_pct":0,"top_processes":[{"pid":42,"name":"test.exe","cpu_pct":14.5,"ram_mb":128}]}""",DeviceDetail::class.java)
        assertEquals(3,detail.disks!!.size)
        assertEquals("D:",detail.disks!![1].mountpoint)
        assertEquals(80f,detail.disks!![1].freeGb!!,0f)
        assertEquals(0f,detail.gpuUsagePct!!,0f)
        assertEquals(14.5f,detail.topProcesses!![0].cpuPercent,0f)
        assertNull(detail.cpuUsage)
    }
    @Test fun processGpuUnknownAndZeroAreDifferent(){
        val known=Gson().fromJson("""{"pid":1,"name":"a","cpu_pct":0,"ram_mb":1,"gpu_usage_pct":0}""",ProcessInfo::class.java)
        val unknown=Gson().fromJson("""{"pid":2,"name":"b","cpu_pct":0,"ram_mb":1}""",ProcessInfo::class.java)
        assertEquals(0f,known.gpuUsagePct!!,0f)
        assertNull(unknown.gpuUsagePct)
    }
    @Test fun readsServerTimestampForHistory() {
        val metric=Gson().fromJson("""{"created_at":"2026-10-27T09:00:00Z","cpu_usage":12,"ram_usage":50}""",Metric::class.java)
        assertEquals("2026-10-27T09:00:00Z",metric.timestamp)
    }
}
