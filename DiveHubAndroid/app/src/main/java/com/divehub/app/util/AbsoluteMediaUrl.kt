package com.divehub.app.util

import android.net.Uri

private val emulatorDevHosts = setOf("10.0.2.2", "127.0.0.1", "localhost")

/**
 * If the server stored a full URL with emulator/loopback host (e.g. `http://10.0.2.2:3000/...`)
 * but the app is pointed at a LAN IP or HTTPS, rewrite authority so the phone can load the file.
 */
private fun rewriteAbsoluteUrlToCurrentApiHost(apiRoot: String, url: String): String {
    val parsed = Uri.parse(url)
    val host = parsed.host?.lowercase() ?: return url
    if (host !in emulatorDevHosts) return url
    val origin = mediaOriginBaseUrl(apiRoot).trim()
    if (origin.isEmpty()) return url
    val target = Uri.parse(origin)
    val tHost = target.host?.lowercase() ?: return url
    if (tHost == host) return url
    return parsed.buildUpon()
        .scheme(target.scheme)
        .encodedAuthority(target.encodedAuthority)
        .build()
        .toString()
}

/**
 * Strips trailing `/api` segments so a stored path like `/api/media/files/…` joins correctly
 * when users set API base as `https://host` or `https://host/api`.
 */
fun mediaOriginBaseUrl(apiRoot: String): String {
    var b = apiRoot.trim().trimEnd('/')
    while (b.length > 4 && b.endsWith("/api", ignoreCase = true)) {
        b = b.dropLast(4).trimEnd('/')
    }
    return b
}

/** Coil / browser: turn server paths (`/api/media/files/…`) into full URL using current API host (debug/release/override). */
fun absoluteMediaUrl(apiRoot: String, stored: String): String {
    val s = stored.trim()
    if (s.isEmpty()) return ""
    if (s.startsWith("http://", ignoreCase = true) || s.startsWith("https://", ignoreCase = true)) {
        return rewriteAbsoluteUrlToCurrentApiHost(apiRoot, s)
    }
    val base = mediaOriginBaseUrl(apiRoot)
    if (base.isEmpty()) return s
    return if (s.startsWith("/")) "$base$s" else "$base/$s"
}
