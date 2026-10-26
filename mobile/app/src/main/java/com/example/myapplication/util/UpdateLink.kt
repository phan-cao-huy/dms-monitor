package com.example.myapplication.util

import java.net.URI

/** Metadata may link only to the configured server, never to another origin or scheme. */
object UpdateLink {
    fun resolve(baseUrl: String, downloadUrl: String): String {
        val base = URI(baseUrl)
        require(base.scheme == "https" || base.scheme == "http") { "Invalid server scheme" }
        require(base.host != null && base.rawUserInfo == null) { "Invalid server address" }
        val target = base.resolve("/").resolve(downloadUrl)
        fun effectivePort(uri: URI): Int = if (uri.port >= 0) uri.port else if (uri.scheme == "https") 443 else 80
        require(target.scheme == base.scheme && target.host.equals(base.host, ignoreCase = true) &&
            effectivePort(target) == effectivePort(base) && target.rawUserInfo == null) {
            "Update link must belong to the configured server"
        }
        require(target.fragment == null) { "Invalid update link" }
        return target.toASCIIString()
    }
}
