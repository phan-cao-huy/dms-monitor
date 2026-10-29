package com.example.myapplication

import com.example.myapplication.util.DevicePermissions
import org.junit.Assert.*
import org.junit.Test

class DevicePermissionsTest {
    @Test fun viewerCannotControlRegardlessOfGroupManagerRole() {
        assertFalse(DevicePermissions.canControl("Viewer","3","Manager"))
        assertFalse(DevicePermissions.canControl("Viewer",null,null))
        assertFalse(DevicePermissions.canControl(null,"3","Manager"))
    }
    @Test fun onlyAuthorizedManagersAndAdminsCanControl() {
        assertTrue(DevicePermissions.canControl("Owner","3","Viewer"))
        assertTrue(DevicePermissions.canControl("Admin","3","Viewer"))
        assertTrue(DevicePermissions.canControl("Manager","3","Manager"))
        assertTrue(DevicePermissions.canControl("Manager",null,null))
        assertFalse(DevicePermissions.canControl("Manager","3","Viewer"))
        assertFalse(DevicePermissions.canControl("Manager","3",null))
    }
}
