package com.example.myapplication.util

import com.example.myapplication.data.model.AlertRule
import com.example.myapplication.data.model.Device

object RulePolicy {
    fun key(rule: AlertRule) = rule.metricType to when(rule.metricType) {
        "SERVICE" -> rule.serviceName.orEmpty()
        "PROCESS_MISSING" -> rule.processName.orEmpty()
        else -> ""
    }
    fun effective(rules: List<AlertRule>,device: Device,orgId: String): List<AlertRule> =
        rules.filter { it.isActive }.mapNotNull { rule ->
            val priority=when {
                rule.deviceId!=null -> if(rule.deviceId.toString()==device.id)3 else 0
                rule.groupId!=null -> if(rule.groupId.toString()==device.groupId)2 else 0
                else -> if(rule.workspaceId.toString()==orgId)1 else 0
            }
            if(priority==0)null else priority to rule
        }.groupBy{key(it.second)}.values.map { group ->
            group.maxWith(compareBy<Pair<Int,AlertRule>>{it.first}.thenBy{it.second.ruleId?:0}).second
        }
    fun duplicate(rules: List<AlertRule>,candidate: AlertRule): AlertRule? = rules.find { rule ->
        rule.isActive && rule.ruleId!=candidate.ruleId && key(rule)==key(candidate) &&
            if(candidate.deviceId!=null)rule.deviceId==candidate.deviceId else candidate.groupId!=null&&rule.groupId==candidate.groupId
    }
}
