package com.example.myapplication.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.model.*

@Composable fun WorkspaceToolsScreen(org:Organization?,organizations:List<Organization>,devices:List<Device>,onSelectOrg:(String)->Unit) {
    var tab by remember(org?.id){mutableStateOf("analytics")}
    Column(Modifier.fillMaxSize()){
        Column(Modifier.padding(horizontal=16.dp,vertical=8.dp)){
            FeatureChoice(featureText("Không gian làm việc","Workspace"),org?.id?:"",organizations.map{it.id to it.orgName},onSelectOrg)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                FilterChip(selected=tab=="analytics",onClick={tab="analytics"},label={Text(featureText("Phân tích","Analytics"))})
                if(org?.role in listOf("Owner","Admin")){
                    FilterChip(selected=tab=="checks",onClick={tab="checks"},label={Text(featureText("Dịch vụ","Service checks"))})
                    FilterChip(selected=tab=="email",onClick={tab="email"},label={Text("Email")})
                }
            }
        }
        Box(Modifier.weight(1f)){key(org?.id,tab){when(tab){"checks"->ServiceChecksScreen(org);"email"->EmailChannelsScreen(org);else->MetricAnalysisScreen(org,devices)}}}
    }
}
