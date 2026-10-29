package com.example.myapplication

import com.example.myapplication.util.DeviceConnection
import org.junit.Assert.*
import org.junit.Test

class DeviceConnectionTest {
    @Test fun warningDevicesRemainOnline() {
        assertEquals(2,listOf("Warning","Warning","Offline").count{DeviceConnection.isOnline(it)})
        assertTrue(DeviceConnection.isOnline("Online"))
    }
    @Test fun disconnectedPausedArchivedAndUnknownAreNotOnline() {
        listOf("Offline","Paused","Archived","Other",null).forEach{assertFalse(DeviceConnection.isOnline(it))}
    }
}
