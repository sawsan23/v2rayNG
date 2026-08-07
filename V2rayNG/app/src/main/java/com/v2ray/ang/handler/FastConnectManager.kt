package com.v2ray.ang.handler

import kotlinx.coroutines.delay
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager.decodeAllServerList
import com.v2ray.ang.handler.MmkvManager.decodeServerConfig
import com.v2ray.ang.handler.MmkvManager.setSelectServer
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FastConnectManager {

    /**
     * အမြန်ဆုံး Node (ms အနည်းဆုံး) ကို ရှာဖွေပြီး အလိုအလျောက် ရွေးချယ်ပေးသည် (Fast Connect)
     */
    suspend fun performFastConnect(): Pair<String, Long> = withContext(Dispatchers.IO) {
        val serverList = decodeAllServerList()
        if (serverList.isEmpty()) {
            LogUtil.w(AppConfig.TAG, "FastConnectManager: No servers available for fast connect.")
            return@withContext Pair("No Server", -1L)
        }

        var bestGuid: String? = null
        var minDelay = Long.MAX_VALUE
        var bestRemarks = "Default"

        for (guid in serverList) {
            val profile = decodeServerConfig(guid) ?: continue
            val delay = profile.delay

            // အကယ်၍ Delay စစ်ထားပြီးသားဖြစ်ပြီး 0 ထက်ကြီးကာ အနည်းဆုံးဖြစ်နေလျှင်
            if (delay in 1 until minDelay) {
                minDelay = delay
                bestGuid = guid
                bestRemarks = profile.remarks
            }
        }

        // Delay စစ်ထားတာ မရှိသေးလျှင် (သို့မဟုတ် အကုန် 0 ဖြစ်နေလျှင်) ပထမဆုံး Server ကို ယူမည်
        if (bestGuid == null && serverList.isNotEmpty()) {
            bestGuid = serverList.first()
            val profile = decodeServerConfig(bestGuid)
            bestRemarks = profile?.remarks ?: "Server 1"
            minDelay = profile?.delay ?: 0L
        }

        // အမြန်ဆုံး Server ကို App တွင် Active အဖြစ် သတ်မှတ်ပေးခြင်း
        if (bestGuid != null) {
            setSelectServer(bestGuid)
            LogUtil.i(AppConfig.TAG, "FastConnectManager: Selected fastest server -> $bestRemarks ($minDelay ms)")
        }

        return@withContext Pair(bestRemarks, minDelay)
    }
}
