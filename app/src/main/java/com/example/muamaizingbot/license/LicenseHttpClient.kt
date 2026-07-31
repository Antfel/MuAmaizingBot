package com.example.muamaizingbot.license

import android.util.Log
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.Inet4Address
import java.util.concurrent.TimeUnit

/**
 * Shared HTTP transport for license and content APIs.
 *
 * Some BlueStacks builds advertise IPv6 but have no usable IPv6 route. Returning
 * IPv4 addresses first lets OkHttp connect immediately while retaining IPv6 as
 * a fallback for IPv6-only networks.
 */
internal object LicenseHttpClient {

    private const val TAG = "LicenseHttp"

    private val ipv4FirstDns = Dns { hostname ->
        val addresses = Dns.SYSTEM.lookup(hostname)
        val sorted = addresses.sortedBy { address ->
            if (address is Inet4Address) 0 else 1
        }
        Log.d(
            TAG,
            "[HTTP] dns host=$hostname order=" +
                sorted.joinToString { if (it is Inet4Address) "v4" else "v6" },
        )
        sorted
    }

    val licenseClient: OkHttpClient = OkHttpClient.Builder()
        .dns(ipv4FirstDns)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    val contentClient: OkHttpClient = licenseClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun addTunnelHeaders(url: String, builder: okhttp3.Request.Builder) {
        if (url.contains(".ngrok-free.dev/")) {
            builder.header("ngrok-skip-browser-warning", "true")
        }
    }
}
