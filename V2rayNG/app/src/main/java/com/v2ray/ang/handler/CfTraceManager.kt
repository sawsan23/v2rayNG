package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object CfTraceManager {

    /**
     * Cloudflare CDN Endpoint မှ Network Trace ကို လှမ်းဆွဲပါမည်။
     */
    suspend fun getTraceInfo(): String = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://1.1.1.1/cdn-cgi/trace")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000 // 5 seconds timeout
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                parseTraceResponse(response)
            } else {
                "Trace Failed: HTTP ${connection.responseCode}"
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to fetch CF Trace", e)
            "Trace Error: Timeout or No Connection"
        }
    }

    /**
     * Response မှ IP, WARP နှင့် COLO ကို Parse (ခွဲထုတ်) ပါမည်။
     */
    private fun parseTraceResponse(response: String): String {
        var ip = "Unknown"
        var warp = "Unknown"
        var colo = "Unknown"

        response.lines().forEach { line ->
            when {
                line.startsWith("ip=") -> ip = line.substringAfter("ip=")
                line.startsWith("warp=") -> warp = line.substringAfter("warp=")
                line.startsWith("colo=") -> colo = line.substringAfter("colo=")
            }
        }

        return "IP: $ip\nWARP: $warp\nColo (Location): $colo"
    }
}
