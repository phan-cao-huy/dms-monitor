package com.example.myapplication

import com.example.myapplication.data.model.AlertRule
import com.example.myapplication.data.model.Device
import com.example.myapplication.util.RulePolicy
import org.junit.Assert.*
import org.junit.Test

class RulePolicyTest {
    private val device=Device(id="1",groupId="2")
    @Test fun mostSpecificRuleWinsRegardlessOfOrder() {
        val rules=listOf(AlertRule(ruleId=1,workspaceId=3,metricType="RAM"),
            AlertRule(ruleId=2,groupId=2,metricType="RAM"),AlertRule(ruleId=3,deviceId=1,metricType="RAM"),
            AlertRule(ruleId=4,deviceId=1,metricType="RAM"),AlertRule(ruleId=5,deviceId=99,metricType="RAM"))
        assertEquals(listOf(4),RulePolicy.effective(rules,device,"3").map{it.ruleId})
        assertEquals(listOf(4),RulePolicy.effective(rules.reversed(),device,"3").map{it.ruleId})
        assertEquals(listOf(2),RulePolicy.effective(rules.take(2),device,"3").map{it.ruleId})
        assertEquals(listOf(1),RulePolicy.effective(rules.take(1),device,"3").map{it.ruleId})
    }
    @Test fun disabledAndOtherWorkspaceRulesAreExcluded() {
        assertTrue(RulePolicy.effective(listOf(AlertRule(workspaceId=4),AlertRule(deviceId=1,isActive=false)),device,"3").isEmpty())
    }
    @Test fun duplicateThresholdMustBeEditedButOtherDevicesRemainIndependent() {
        val existing=AlertRule(ruleId=1,deviceId=1,metricType="RAM",threshold=93f)
        assertEquals(existing,RulePolicy.duplicate(listOf(existing),AlertRule(deviceId=1,metricType="RAM",threshold=50f)))
        assertNull(RulePolicy.duplicate(listOf(existing),existing))
        assertNull(RulePolicy.duplicate(listOf(existing),AlertRule(deviceId=2,metricType="RAM")))
        assertNull(RulePolicy.duplicate(listOf(existing.copy(isActive=false)),AlertRule(deviceId=1,metricType="RAM")))
    }
    @Test fun namedServicesRemainIndependent() {
        val one=AlertRule(ruleId=1,deviceId=1,metricType="SERVICE",serviceName="Spooler")
        val two=one.copy(ruleId=2,serviceName="DMS-Agent")
        assertNull(RulePolicy.duplicate(listOf(one),two))
        assertEquals(2,RulePolicy.effective(listOf(one,two),device,"3").size)
    }
}
