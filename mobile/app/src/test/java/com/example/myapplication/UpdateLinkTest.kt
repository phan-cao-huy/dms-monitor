package com.example.myapplication

import com.example.myapplication.util.UpdateLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UpdateLinkTest {
    @Test fun allowsConfiguredServerOnly() {
        assertEquals("https://dms.example/static/dms-app.apk",
            UpdateLink.resolve("https://dms.example/api/v1/", "/static/dms-app.apk"))
        assertEquals("http://10.0.2.2:8000/static/dms-app.apk",
            UpdateLink.resolve("http://10.0.2.2:8000/api/v1/", "/static/dms-app.apk"))
    }

    @Test fun rejectsUntrustedLinksAndDowngrades() {
        for (link in listOf("https://other.example/app.apk", "//other.example/app.apk",
            "http://dms.example/app.apk", "https://dms.example:8443/app.apk",
            "intent://install", "file:///sdcard/app.apk", "javascript:alert(1)",
            "https://user@dms.example/app.apk")) {
            assertThrows(IllegalArgumentException::class.java) {
                UpdateLink.resolve("https://dms.example/api/v1/", link)
            }
        }
    }
}
