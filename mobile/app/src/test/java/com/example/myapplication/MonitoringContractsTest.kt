package com.example.myapplication

import com.example.myapplication.data.model.*
import com.example.myapplication.ui.screen.FeatureFailure
import com.example.myapplication.ui.screen.FeatureStatus
import com.example.myapplication.ui.screen.verify
import com.example.myapplication.util.MonitoringSupport
import com.example.myapplication.util.PlotPoint
import com.google.gson.Gson
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException

class MonitoringContractsTest {
    private val gson=Gson()
    @Test fun gmailPasswordDisplaySpacesAreRemovedWithoutChangingOtherProviders() {
        assertEquals("abcdefghijklmnop",MonitoringSupport.smtpPassword("smtp.gmail.com","abcd efgh ijkl mnop"))
        assertEquals(" pass ",MonitoringSupport.smtpPassword("smtp.example.com"," pass "))
        assertNull(MonitoringSupport.smtpPassword("smtp.gmail.com",""))
    }
    @Test fun editingEmailOmitsPasswordAndKeepsDisabledState() {
        val write=EmailChannelWrite("Mail",false,EmailConfig(username="test@example.com",fromEmail="test@example.com",recipients=listOf("target@example.com")))
        val json=gson.toJsonTree(write).asJsonObject
        assertFalse(json.getAsJsonObject("config").has("password"))
        assertFalse(json["enabled"].asBoolean)
        val read=gson.fromJson("""{"channel_id":2,"name":"Mail","enabled":true,"password_set":true,"config":{"host":"smtp.gmail.com","port":587,"username":"test@example.com","from_email":"test@example.com","recipients":["a@example.com"],"language":"en"}}""",EmailChannel::class.java)
        assertNull(read.config.password)
        assertTrue(read.passwordSet)
        assertEquals("en",read.config.language)
    }
    @Test fun unknownDiskHealthAndGpuHistoryNeverBecomeHealthyOrZero() {
        val detail=gson.fromJson("""{"device_id":1,"static_info":{"disks_health":[{"name":"Unknown","healthy":null},{"name":"Bad","healthy":false}],"connections":[],"event_log_status":{"Security":"ACCESS_DENIED"}}}""",DeviceDetail::class.java)
        assertNull(detail.staticInfo!!.disksHealth!![0].healthy)
        assertEquals(false,detail.staticInfo!!.disksHealth!![1].healthy)
        assertTrue(detail.staticInfo!!.connections!!.isEmpty())
        assertEquals("ACCESS_DENIED",detail.staticInfo!!.eventLogStatus!!["Security"])
        val history=gson.fromJson("""{"resolution":"minute","step_seconds":60,"from":"2026-10-29T00:00:00Z","to":"2026-10-29T00:02:00Z","items":[{"created_at":"2026-10-29T00:00:00Z","gpu_usage_pct":0},{"created_at":"2026-10-29T00:01:00Z","gpu_usage_pct":null}]}""",MetricEnvelope::class.java)
        assertEquals(0.0,history.items[0].gpu!!,0.0)
        assertNull(history.items[1].gpu)
        assertNull(history.items[0].cpu)
    }
    @Test fun workspaceDefaultsKeepScopeAndFixedOfflineThresholdIsOmitted() {
        val rule=gson.fromJson("""{"rule_id":5,"workspace_id":8,"default_key":"OFFLINE","device_id":null,"group_id":null,"metric_type":"OFFLINE","is_active":false,"threshold":5}""",AlertRule::class.java)
        assertEquals(8,rule.workspaceId)
        assertEquals("OFFLINE",rule.defaultKey)
        assertNull(rule.deviceId)
        assertFalse(rule.isActive)
        val update=gson.toJsonTree(AlertRuleUpdate(null,1,30,false)).asJsonObject
        assertFalse(update.has("threshold"))
        assertFalse(update["is_active"].asBoolean)
    }
    @Test fun eventsSupportLargeCursorAndScopedConnectionFields() {
        val page=gson.fromJson("""{"next_before":3000000001,"items":[{"event_pk":3000000002,"record_id":99,"event_id":4625,"log_name":"Security","level":"AUDIT_FAILURE","source":"Test","message":"Failed sign-in","occurred_at":"2026-10-29T00:00:00"}]}""",DeviceEventPage::class.java)
        assertEquals(3000000001L,page.nextBefore)
        assertEquals(3000000002L,page.items[0].id)
        assertEquals("AUDIT_FAILURE",page.items[0].level)
        assertNotNull(MonitoringSupport.instant(page.items[0].occurredAt))
    }
    @Test fun labelFiltersPreserveColonAndEnforceLimits() {
        assertTrue(MonitoringSupport.labelMatches(mapOf("env" to "prod:blue"),"env:prod:blue"))
        assertFalse(MonitoringSupport.labelMatches(mapOf("env" to "prod:blue"),"env:prod"))
        assertFalse(MonitoringSupport.validLabel("Bad Key","value"))
        assertFalse(MonitoringSupport.validLabel("env","x".repeat(65)))
        assertFalse(MonitoringSupport.validLabel("env","a\nb"))
        assertTrue(MonitoringSupport.validLabel("env","prod"))
        assertTrue(MonitoringSupport.validLabel("env","😀".repeat(64)))
        assertFalse(MonitoringSupport.validLabel("env","😀".repeat(65)))
    }
    @Test fun timelinePreservesMissingBucketAndLegitimateZero() {
        val points=listOf(PlotPoint("2026-10-29T00:00:00Z",0.0),PlotPoint("2026-10-29T00:02:00Z",50.0))
        val rows=MonitoringSupport.timeline(points,"2026-10-29T00:00:00Z","2026-10-29T00:03:00Z",60)
        assertEquals(3,rows.size)
        assertEquals(0.0,rows[0].value!!,0.0)
        assertNull(rows[1].value)
        assertEquals(50.0,rows[2].value!!,0.0)
        assertEquals(2160L,MonitoringSupport.ranges["90d"])
    }
    @Test fun validationErrorsDoNotEchoSensitiveServerInputs() {
        val response=Response.error<Unit>(422,"""{"detail":[{"input":"private-app-password"}]}""".toResponseBody())
        try {response.verify();fail("Expected failure")}catch(e:FeatureFailure){
            assertFalse(e.vi.contains("private-app-password"))
            assertFalse(e.en.contains("private-app-password"))
        }
    }
    @Test fun backgroundReloadDoesNotEraseUserActionFailure() = runBlocking {
        val status=FeatureStatus()
        status.perform {throw FeatureFailure("Sai thông tin SMTP","SMTP failed")}
        status.refresh { }
        assertEquals("SMTP failed",status.error?.en)
        assertFalse(status.busy)
        assertFalse(status.loading)
    }
    @Test fun cancelledWorkCannotPublishAFalseNetworkError() = runBlocking {
        val status=FeatureStatus()
        try {status.perform {throw CancellationException("scope disposed")}} catch(_:CancellationException){}
        assertNull(status.error)
        assertFalse(status.busy)
    }
    @Test fun checkHistoryAndAnalyticsMatchBackendEnvelopes() {
        val history=gson.fromJson("""{"incidents":[{"detail":"SSL_DAYS:10","opened_at":"2026-10-29T00:00:00","resolved_at":null}],"results":[{"ok":false,"detail":"SSL_DAYS:10","latency_ms":12,"checked_at":"2026-10-29T00:00:00"}]}""",CheckHistory::class.java)
        assertNull(history.incidents[0].resolvedAt)
        assertFalse(history.results[0].ok)
        val query=gson.fromJson("""{"metric":"cpu_usage","agg":"avg","group_by":"label:site","resolution":"hourly","step_seconds":3600,"series":[{"key":null,"name":"Unlabeled","points":[{"timestamp":"2026-10-29T00:00:00Z","value":66.666,"sample_count":3}]}]}""",MetricsQuery::class.java)
        assertNull(query.series[0].key)
        assertEquals(3,query.series[0].points[0].samples)
        assertEquals(66.666,query.series[0].points[0].value!!,0.001)
    }
}
