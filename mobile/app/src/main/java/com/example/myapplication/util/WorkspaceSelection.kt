package com.example.myapplication.util

import com.example.myapplication.data.model.Organization

object WorkspaceSelection {
    fun choose(orgs:List<Organization>,preferred:String?,current:String?,saved:String?):Organization? {
        // Joining is an explicit switch, even when the user already owns another workspace.
        if(preferred!=null)return orgs.find{it.id==preferred}
        return orgs.find{it.id==current} ?: orgs.find{it.id==saved} ?: orgs.firstOrNull()
    }
}
