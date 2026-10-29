package com.example.myapplication

import com.example.myapplication.data.model.Organization
import com.example.myapplication.util.WorkspaceSelection
import org.junit.Assert.*
import org.junit.Test

class WorkspaceSelectionTest {
    private val personal=Organization(id="1",orgName="Personal",role="Owner")
    private val invited=Organization(id="2",orgName="Invited",role="Viewer")
    @Test fun joiningSelectsInvitedWorkspaceEvenWhenPersonalWorkspaceWasSelected() {
        assertEquals(invited,WorkspaceSelection.choose(listOf(personal,invited),"2","1","1"))
    }
    @Test fun staleListCannotUndoExplicitJoinSelection() {
        assertNull(WorkspaceSelection.choose(listOf(personal),"2","1","1"))
    }
    @Test fun normalRefreshKeepsManualSelectionAndLoginUsesSavedWorkspace() {
        assertEquals(invited,WorkspaceSelection.choose(listOf(personal,invited),null,"2","1"))
        assertEquals(invited,WorkspaceSelection.choose(listOf(personal,invited),null,null,"2"))
        assertEquals(personal,WorkspaceSelection.choose(listOf(personal),null,"2","2"))
    }
}
