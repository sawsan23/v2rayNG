package com.v2ray.ang.handler

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig.DEFAULT_SUBSCRIPTION_ID
import com.v2ray.ang.AppConfig.PREF_IS_BOOTED
import com.v2ray.ang.AppConfig.PREF_ROUTING_RULESET
import com.v2ray.ang.dto.entities.AssetUrlCache
import com.v2ray.ang.dto.entities.AssetUrlItem
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.dto.entities.ServerAffiliationInfo
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.dto.entities.WebDavConfig
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

object MmkvManager {
    //region private
    private const val ID_MAIN = "MAIN"
    private const val ID_PROFILE_FULL_CONFIG = "PROFILE_FULL_CONFIG"
    private const val ID_SERVER_RAW = "SERVER_RAW"
    private const val ID_SERVER_AFF = "SERVER_AFF"
    private const val ID_SUB = "SUB"
    private const val ID_ASSET = "ASSET"
    private const val ID_SETTING = "SETTING"

    private const val KEY_SELECTED_SERVER = "SELECTED_SERVER"
    private const val KEY_ANG_CONFIGS = "ANG_CONFIGS"
    private const val KEY_SUB_SERVER_PREFIX = "SUB_SERVERS_"
    private const val KEY_SUB_IDS = "SUB_IDS"
    private const val KEY_WEBDAV_CONFIG = "WEBDAV_CONFIG"

    private val mainStorage by lazy { MMKV.mmkvWithID(ID_MAIN, MMKV.MULTI_PROCESS_MODE) }
    private val profileFullStorage by lazy { MMKV.mmkvWithID(ID_PROFILE_FULL_CONFIG, MMKV.MULTI_PROCESS_MODE) }
    private val serverRawStorage by lazy { MMKV.mmkvWithID(ID_SERVER_RAW, MMKV.MULTI_PROCESS_MODE) }
    private val serverAffStorage by lazy { MMKV.mmkvWithID(ID_SERVER_AFF, MMKV.MULTI_PROCESS_MODE) }
    private val subStorage by lazy { MMKV.mmkvWithID(ID_SUB, MMKV.MULTI_PROCESS_MODE) }
    private val assetStorage by lazy { MMKV.mmkvWithID(ID_ASSET, MMKV.MULTI_PROCESS_MODE) }
    private val settingsStorage by lazy { MMKV.mmkvWithID(ID_SETTING, MMKV.MULTI_PROCESS_MODE) }
    //endregion

    //region Server
    fun readLegacyServerList(): String? {
        return mainStorage.decodeString(KEY_ANG_CONFIGS)
    }

    fun getSelectServer(): String? {
        return mainStorage.decodeString(KEY_SELECTED_SERVER)
    }

    fun setSelectServer(guid: String) {
        mainStorage.encode(KEY_SELECTED_SERVER, guid)
    }

    fun encodeServerList(serverList: MutableList<String>, subscriptionId: String) {
        val subId = getSubscriptionId(subscriptionId)
        val key = "$KEY_SUB_SERVER_PREFIX$subId"
        mainStorage.encode(key, JsonUtil.toJson(serverList))
    }

    fun decodeServerList(subscriptionId: String): MutableList<String> {
        val subId = getSubscriptionId(subscriptionId)
        val key = "$KEY_SUB_SERVER_PREFIX$subId"
        val json = mainStorage.decodeString(key)
        return if (json.isNullOrBlank()) {
            mutableListOf()
        } else {
            JsonUtil.fromJsonSafe(json, Array<String>::class.java)?.toMutableList() ?: mutableListOf()
        }
    }

    fun decodeAllServerList(): MutableList<String> {
        val allServers = mutableListOf<String>()
        val subsList = decodeSubsList()
        if (!subsList.contains(DEFAULT_SUBSCRIPTION_ID)) {
            allServers.addAll(decodeServerList(DEFAULT_SUBSCRIPTION_ID))
        }
        subsList.forEach { guid ->
            allServers.addAll(decodeServerList(guid))
        }
        return allServers
    }

    fun decodeServerConfig(guid: String): ProfileItem? {
        if (guid.isBlank()) return null
        val json = profileFullStorage.decodeString(guid)
        if (json.isNullOrBlank()) return null
        return JsonUtil.fromJsonSafe(json, ProfileItem::class.java)
    }

    fun encodeServerConfig(guid: String, config: ProfileItem): String {
        val key = guid.ifBlank { Utils.getUuid() }
        profileFullStorage.encode(key, JsonUtil.toJson(config))
        val subId = getSubscriptionId(config.subscriptionId)
        val serverList = decodeServerList(subId)
        if (!serverList.contains(key)) {
            serverList.add(0, key)
            encodeServerList(serverList, subId)
            if (getSelectServer().isNullOrBlank()) {
                mainStorage.encode(KEY_SELECTED_SERVER, key)
            }
        }
        return key
    }

    fun encodeProfileDirect(key: String, configJson: String) {
        profileFullStorage.encode(key, configJson)
    }

    fun removeServer(guid: String) {
        if (guid.isBlank()) return
        val config = decodeServerConfig(guid)
        val subId = getSubscriptionId(config?.subscriptionId)
        val serverList = decodeServerList(subId)
        serverList.remove(guid)
        encodeServerList(serverList, subId)
        if (getSelectServer() == guid) {
            mainStorage.remove(KEY_SELECTED_SERVER)
        }
        profileFullStorage.remove(guid)
        serverAffStorage.remove(guid)
    }

    fun removeServerViaSubid(subscriptionId: String?) {
        val subId = getSubscriptionId(subscriptionId)
        val serverList = decodeServerList(subId)
        serverList.forEach { guid ->
            if (getSelectServer() == guid) {
                mainStorage.remove(KEY_SELECTED_SERVER)
            }
            profileFullStorage.remove(guid)
            serverAffStorage.remove(guid)
        }
        serverList.clear()
        encodeServerList(serverList, subId)
    }

    fun removeServers(guids: List<String>, subscriptionId: String) {
        if (guids.isEmpty()) return
        val subId = getSubscriptionId(subscriptionId)
        val serverList = decodeServerList(subId)
        if (serverList.removeAll(guids)) {
            encodeServerList(serverList, subId)
        }
        val selectedServer = getSelectServer()
        guids.forEach { guid ->
            if (selectedServer == guid) {
                mainStorage.remove(KEY_SELECTED_SERVER)
            }
            profileFullStorage.remove(guid)
            serverAffStorage.remove(guid)
            serverRawStorage.remove(guid)
        }
    }

    fun decodeServerAffiliationInfo(guid: String): ServerAffiliationInfo? {
        if (guid.isBlank()) return null
        val json = serverAffStorage.decodeString(guid)
        if (json.isNullOrBlank()) return null
        return JsonUtil.fromJsonSafe(json, ServerAffiliationInfo::class.java)
    }

    fun encodeServerTestDelayMillis(guid: String, testResult: Long) {
        if (guid.isBlank()) return
        val aff = decodeServerAffiliationInfo(guid) ?: ServerAffiliationInfo()
        aff.testDelayMillis = testResult
        serverAffStorage.encode(guid, JsonUtil.toJson(aff))
    }

    fun clearAllTestDelayResults(keys: List<String>?) {
        keys?.forEach { key ->
            decodeServerAffiliationInfo(key)?.let { aff ->
                aff.testDelayMillis = 0
                serverAffStorage.encode(key, JsonUtil.toJson(aff))
            }
        }
    }

    fun removeAllServer(): Int {
        val count = profileFullStorage.allKeys()?.count() ?: 0
        profileFullStorage.clearAll()
        serverAffStorage.clearAll()
        serverRawStorage.clearAll()
        decodeSubscriptions().forEach { sub ->
            encodeServerList(mutableListOf(), sub.guid)
        }
        return count
    }

    fun removeInvalidServer(guid: String): Int {
        var count = 0
        if (guid.isNotEmpty()) {
            decodeServerAffiliationInfo(guid)?.let { aff ->
                if (aff.testDelayMillis < 0L) {
                    removeServer(guid)
                    count++
                }
            }
        } else {
            serverAffStorage.allKeys()?.forEach { key ->
                decodeServerAffiliationInfo(key)?.let { aff ->
                    if (aff.testDelayMillis < 0L) {
                        removeServer(key)
                        count++
                    }
                }
            }
        }
        return count
    }

    fun encodeServerRaw(guid: String, config: String) {
        serverRawStorage.encode(guid, config)
    }

    fun decodeServerRaw(guid: String): String? {
        return serverRawStorage.decodeString(guid)
    }
    //endregion

    //region Subscriptions
    private fun getSubscriptionId(subscriptionId: String?): String {
        return subscriptionId?.ifEmpty { DEFAULT_SUBSCRIPTION_ID } ?: DEFAULT_SUBSCRIPTION_ID
    }

    private fun initSubsList() {
        val subsList = decodeSubsList()
        if (subsList.isNotEmpty()) return
        subStorage.allKeys()?.forEach { key ->
            subsList.add(key)
        }
        encodeSubsList(subsList)
    }

    fun decodeSubscriptions(): List<SubscriptionCache> {
        initSubsList()
        val subscriptions = mutableListOf<SubscriptionCache>()
        decodeSubsList().forEach { key ->
            val json = subStorage.decodeString(key)
            if (!json.isNullOrBlank()) {
                val item = JsonUtil.fromJsonSafe(json, SubscriptionItem::class.java) ?: SubscriptionItem()
                subscriptions.add(SubscriptionCache(key, item))
            }
        }
        return subscriptions
    }

    fun removeSubscription(subid: String) {
        subStorage.remove(subid)
        val subsList = decodeSubsList()
        subsList.remove(subid)
        encodeSubsList(subsList)
        removeServerViaSubid(subid)
    }

    fun encodeSubscription(guid: String, subItem: SubscriptionItem) {
        val key = guid.ifBlank { Utils.getUuid() }
        subStorage.encode(key, JsonUtil.toJson(subItem))
        val subsList = decodeSubsList()
        if (!subsList.contains(key)) {
            subsList.add(key)
            encodeSubsList(subsList)
        }
    }

    fun decodeSubscription(subscriptionId: String): SubscriptionItem? {
        val json = subStorage.decodeString(subscriptionId) ?: return null
        return JsonUtil.fromJsonSafe(json, SubscriptionItem::class.java)
    }

    fun encodeSubsList(subsList: MutableList<String>) {
        mainStorage.encode(KEY_SUB_IDS, JsonUtil.toJson(subsList))
    }

    fun decodeSubsList(): MutableList<String> {
        val json = mainStorage.decodeString(KEY_SUB_IDS)
        return if (json.isNullOrBlank()) {
            mutableListOf()
        } else {
            JsonUtil.fromJsonSafe(json, Array<String>::class.java)?.toMutableList() ?: mutableListOf()
        }
    }
    //endregion

    //region Asset
    fun decodeAssetUrls(): List<AssetUrlCache> {
        val assetUrlItems = mutableListOf<AssetUrlCache>()
        assetStorage.allKeys()?.forEach { key ->
            val json = assetStorage.decodeString(key)
            if (!json.isNullOrBlank()) {
                val item = JsonUtil.fromJsonSafe(json, AssetUrlItem::class.java) ?: AssetUrlItem()
                assetUrlItems.add(AssetUrlCache(key, item))
            }
        }
        return assetUrlItems.sortedBy { it.assetUrl.addedTime }
    }

    fun removeAssetUrl(assetid: String) {
        assetStorage.remove(assetid)
    }

    fun encodeAsset(assetid: String, assetItem: AssetUrlItem) {
        val key = assetid.ifBlank { Utils.getUuid() }
        assetStorage.encode(key, JsonUtil.toJson(assetItem))
    }

    fun decodeAsset(assetid: String): AssetUrlItem? {
        val json = assetStorage.decodeString(assetid) ?: return null
        return JsonUtil.fromJsonSafe(json, AssetUrlItem::class.java)
    }
    //endregion

    //region Routing
    fun decodeRoutingRulesets(): MutableList<RulesetItem>? {
        val ruleset = settingsStorage.decodeString(PREF_ROUTING_RULESET)
        if (ruleset.isNullOrEmpty()) return null
        return JsonUtil.fromJsonSafe(ruleset, Array<RulesetItem>::class.java)?.toMutableList() ?: mutableListOf()
    }

    fun encodeRoutingRulesets(rulesetList: MutableList<RulesetItem>?) {
        if (rulesetList.isNullOrEmpty()) encodeSettings(PREF_ROUTING_RULESET, "")
        else encodeSettings(PREF_ROUTING_RULESET, JsonUtil.toJson(rulesetList))
    }
    //endregion

    //region settings
    fun encodeSettings(key: String, value: String?): Boolean = settingsStorage.encode(key, value)
    fun encodeSettings(key: String, value: Int): Boolean = settingsStorage.encode(key, value)
    fun encodeSettings(key: String, value: Long): Boolean = settingsStorage.encode(key, value)
    fun encodeSettings(key: String, value: Float): Boolean = settingsStorage.encode(key, value)
    fun encodeSettings(key: String, value: Boolean): Boolean = settingsStorage.encode(key, value)
    fun encodeSettings(key: String, value: MutableSet<String>): Boolean = settingsStorage.encode(key, value)

    fun decodeSettingsString(key: String): String? {
        return decodeSettingsString(key, null)
    }

    // 💡 SS VPN Custom Default Strings
    fun decodeSettingsString(key: String, defaultValue: String?): String? {
        val actualDefault = when (key) {
            "pref_ui_mode_night" -> "2" // Dark Mode Force
            "pref_remote_dns" -> "https://cloudflare-dns.com/dns-query"
            "pref_domestic_dns" -> "223.5.5.5"
            "pref_vpn_dns" -> "1.1.1.1"
            "pref_loglevel" -> "warning"
            "pref_delay_test_url" -> "https://www.gstatic.com/generate_204"
            "pref_connection_info_test_url" -> "https://api.ip.sb/geoip"
            "pref_vpn_mtu" -> "1500"
            "pref_local_proxy_port" -> "10808"
            "pref_hev_socks5_tunnel_log_level" -> "error"
            "pref_hev_socks5_tunnel_timeout" -> "300,60"
            else -> defaultValue
        }
        return settingsStorage.decodeString(key, actualDefault) ?: actualDefault
    }

    // 💡 SS VPN Custom Default Integers
    fun decodeSettingsInt(key: String, defaultValue: Int): Int {
        val actualDefault = when (key) {
            "pref_delay_test_concurrency" -> 16
            else -> defaultValue
        }
        return settingsStorage.decodeInt(key, actualDefault)
    }

    fun decodeSettingsLong(key: String, defaultValue: Long): Long {
        return settingsStorage.decodeLong(key, defaultValue)
    }

    fun decodeSettingsFloat(key: String, defaultValue: Float): Float {
        return settingsStorage.decodeFloat(key, defaultValue)
    }

    fun decodeSettingsBool(key: String): Boolean {
        return decodeSettingsBool(key, false)
    }

    // 💡 SS VPN Custom Default Booleans
    fun decodeSettingsBool(key: String, defaultValue: Boolean): Boolean {
        // 🚨 Hev TUN Feature အား လုံးဝ အလုပ်မလုပ်အောင် အမြစ်ပြတ် ပိတ်ပစ်ခြင်း
        if (key == "pref_hev_socks5_tunnel_enable") return false

        val actualDefault = when (key) {
            // ON ဖြစ်ရမည့် Setting များ
            "pref_speed_enabled",
            "pref_auto_connect",
            "pref_socks_udp",
            "pref_routing_custom_dns",
            "pref_fake_dns",
            "pref_sniffing_enabled",
            "pref_start_bgn_enable",
            "pref_local_proxy_enabled" -> true

            // OFF ဖြစ်ရမည့် Setting များ
            "pref_confirm_remove",
            "pref_double_column",
            "pref_vpn_ipv6",
            "pref_prefer_ipv6",
            "pref_tethering_sharing_enabled",
            "pref_fragment_enabled",
            "pref_mux_enabled",
            "pref_allow_lan_conn",
            "pref_dynamic_local_proxy_port",
            "pref_append_http_proxy",
            "pref_route_only_enabled" -> false
            
            else -> defaultValue
        }
        return settingsStorage.decodeBool(key, actualDefault)
    }

    fun decodeSettingsStringSet(key: String): MutableSet<String>? {
        return settingsStorage.decodeStringSet(key)
    }

    fun encodeStartOnBoot(startOnBoot: Boolean) {
        encodeSettings(PREF_IS_BOOTED, startOnBoot)
    }

    fun decodeStartOnBoot(): Boolean {
        return decodeSettingsBool(PREF_IS_BOOTED, false)
    }
    //endregion

    //region WebDAV
    fun encodeWebDavConfig(config: WebDavConfig): Boolean {
        return mainStorage.encode(KEY_WEBDAV_CONFIG, JsonUtil.toJson(config))
    }

    fun decodeWebDavConfig(): WebDavConfig? {
        val json = mainStorage.decodeString(KEY_WEBDAV_CONFIG) ?: return null
        return JsonUtil.fromJsonSafe(json, WebDavConfig::class.java)
    }
    //endregion

    //region Compose helpers for Settings
    @Composable
    fun rememberMmkvString(
        key: String,
        default: String = ""
    ): MutableState<String> {
        val state = remember(key) { mutableStateOf(decodeSettingsString(key, default) ?: default) }
        LaunchedEffect(key) {
            snapshotFlow { state.value }
                .drop(1)
                .distinctUntilChanged()
                .collectLatest { value ->
                    encodeSettings(key, value)
                    SettingsChangeManager.notifySettingChanged(key)
                }
        }
        return state
    }

    @Composable
    fun rememberMmkvBool(
        key: String,
        default: Boolean = false
    ): MutableState<Boolean> {
        val state = remember(key) { mutableStateOf(decodeSettingsBool(key, default)) }
        LaunchedEffect(key) {
            snapshotFlow { state.value }
                .drop(1)
                .distinctUntilChanged()
                .collectLatest { value ->
                    encodeSettings(key, value)
                    SettingsChangeManager.notifySettingChanged(key)
                }
        }
        return state
    }
    //endregion
}
