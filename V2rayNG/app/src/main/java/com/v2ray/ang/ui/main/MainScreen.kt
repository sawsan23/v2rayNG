package com.v2ray.ang.ui.main

import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.QRCodeDialog
import com.v2ray.ang.ui.compose.colorConfigType
import com.v2ray.ang.ui.compose.colorPing
import com.v2ray.ang.ui.compose.colorPingRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val isLoading by mainViewModel.isLoading.collectAsStateWithLifecycle()
    val isRunning = uiState.isRunning
    val displayText = uiState.statusText
    val shareQRCodeBitmap = uiState.shareQRCodeBitmap

    val delayMs by mainViewModel.delayMs.collectAsStateWithLifecycle()
    val cfTraceInfo by mainViewModel.cfTraceInfo.collectAsStateWithLifecycle()

    // Collect Servers for current Group
    val serverFlow = remember(uiState.selectedGroupId) {
        mainViewModel.serversForGroup(uiState.selectedGroupId)
    }
        // --- ဤနေရာတွင် မူလကုဒ်များ ရှိပါသည် ---
        // မူလ: val servers by serverFlow.collectAsStateWithLifecycle()
    val servers by serverFlow.collectAsStateWithLifecycle(initialValue = emptyList())

        val context = androidx.compose.ui.platform.LocalContext.current
    val isTesting = uiState.isTesting
    
    var pendingAutoSelect by remember { mutableStateOf(false) } 
    // State များကို သီးခြားစီ ခွဲထုတ်လိုက်ပါသည်
    var hasRunStartupPing by remember { mutableStateOf(false) }

    // =======================================================
    // [လုပ်ငန်းစဉ်-၁] First Install တွင် Sub ကို (၁) ကြိမ်သာ အလိုအလျောက် Update လုပ်ခြင်း
    // =======================================================
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        val hasDoneFirstUpdate = prefs.getBoolean("has_done_first_update", false)
        if (!hasDoneFirstUpdate) {
            onAction(MainAction.UpdateSubscriptions)
            prefs.edit().putBoolean("has_done_first_update", true).apply()
        }
    }

    // =======================================================
    // [လုပ်ငန်းစဉ်-၂] App ဖွင့်တိုင်း (UI ပေါ်ပြီး ၃ စက္ကန့်အကြာတွင်) 10% Auto Ping စစ်ခြင်း
    // =======================================================
    LaunchedEffect(servers.isNotEmpty(), isRunning) {
        // Data များ ရောက်ရှိနေပြီး၊ VPN မချိတ်ရသေးချိန်၌ တစ်ကြိမ်သာ အလုပ်လုပ်မည်
        if (servers.isNotEmpty() && !isRunning && !hasRunStartupPing) {
            hasRunStartupPing = true // နောက်ထပ် မလုပ်စေရန် ချက်ချင်း ပိတ်ထားမည်
            
            // UI အပြည့်အဝ ပေါ်လာရန်နှင့် Service များ အသင့်ဖြစ်ရန် ၃ စက္ကန့် တိတိ စောင့်ပါမည် 
            // (delay သည် Non-blocking ဖြစ်၍ App လုံးဝ Hang/Lag မည် မဟုတ်ပါ)
            kotlinx.coroutines.delay(5000)
            
            // ၃ စက္ကန့်ပြည့်မှသာ Background မှ 10% Ping ကို ဘေးကင်းစွာ စတင်ပါမည်
            pendingAutoSelect = true 
            mainViewModel.smartPing(10, servers) 
        }
    }

    // =======================================================
    // [လုပ်ငန်းစဉ်-၃] Ping Test ပြီးဆုံးချိန် အကောင်းဆုံး Key အား Auto ချိတ်ပေးမည့် စနစ်
    // =======================================================
        LaunchedEffect(isTesting) {
        if (!isTesting && pendingAutoSelect) {
            kotlinx.coroutines.delay(1000) 
            val bestServer = servers.filter { it.testDelayMillis > 0L }.minByOrNull { it.testDelayMillis }
            if (bestServer != null) {
                if (bestServer.guid != uiState.selectedGuid) {
                    onAction(MainAction.SelectServer(bestServer.guid))
                }
                onAction(MainAction.SortByTestResults)
            }
            // လုပ်ငန်းစဉ်အားလုံး ပြီးဆုံးမှသာ false သို့ ပြောင်းပါမည် (Race Condition ကာကွယ်ရန်)
            pendingAutoSelect = false
        }
    }

    // =======================================================
    // [လုပ်ငန်းစဉ်-၄] တစ်နေ့ ၈ ကြိမ် (၃ နာရီတစ်ခါ) Background မှ Key အားလုံး (100%) ကို Auto စစ်မည်
    // =======================================================
    LaunchedEffect(isRunning) {
        if (isRunning) {
            // Unresolved reference Error မတက်စေရန် while (true) ဖြင့်သာ အသုံးပြုပါမည်
            while (true) {
                kotlinx.coroutines.delay(3 * 60 * 60 * 1000L) 
                if (!isTesting) { 
                    pendingAutoSelect = true
                    onAction(MainAction.TestRealAllServers) 
                }
            }
        }
    }




    
    val isDarkTheme = LocalDarkTheme.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    var showDelAllConfirm by remember { mutableStateOf(false) }
    var showDelDuplicateConfirm by remember { mutableStateOf(false) }
    var showDelInvalidConfirm by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf<String?>(null) }
    var shareTarget by remember { mutableStateOf<Triple<String, ProfileItem, Boolean>?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    MainDialogs(
        showDelAllConfirm = showDelAllConfirm,
        onDismissDelAll = { showDelAllConfirm = false },
        onConfirmDelAll = { showDelAllConfirm = false; onAction(MainAction.RemoveAllServers) },
        showDelDuplicateConfirm = showDelDuplicateConfirm,
        onDismissDelDuplicate = { showDelDuplicateConfirm = false },
        onConfirmDelDuplicate = { showDelDuplicateConfirm = false; onAction(MainAction.RemoveDuplicateServers) },
        showDelInvalidConfirm = showDelInvalidConfirm,
        onDismissDelInvalid = { showDelInvalidConfirm = false },
        onConfirmDelInvalid = { showDelInvalidConfirm = false; onAction(MainAction.RemoveInvalidServers) },
        showRemoveConfirm = showRemoveConfirm,
        onDismissRemove = { showRemoveConfirm = null },
        onConfirmRemove = { guid -> showRemoveConfirm = null; onAction(MainAction.RemoveServer(guid)) }
    )

    if (shareTarget != null) {
        val (guid, profile, more) = shareTarget!!
        ShareMethodDialog(
            guid = guid,
            profile = profile,
            more = more,
            onDismiss = { shareTarget = null },
            onAction = onAction,
            onRemove = { id -> if (uiState.confirmRemove) showRemoveConfirm = id else onAction(MainAction.RemoveServer(id)) },
        )
    }
    if (shareQRCodeBitmap != null) {
        QRCodeDialog(bitmap = shareQRCodeBitmap, onDismiss = { onAction(MainAction.DismissQRCodeDialog) })
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MainDrawerContent(
                drawerState = drawerState,
                onNavigate = { route ->
                    scope.launch { drawerState.close() }
                    onNavigate(route)
                }
            )
        }
    ) {
        Scaffold(
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
            topBar = {
                MainTopBar(
                    isLoading = isLoading,
                    showSearch = showSearch,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { query -> searchQuery = query; onAction(MainAction.Search(query)) },
                    onSearchClose = { searchQuery = ""; onAction(MainAction.Search("")); showSearch = false },
                    onSearchToggle = { show -> showSearch = show },
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onAction = onAction,
                    onMoreMenuAction = { action ->
                        when (action) {
                            MainMoreMenuAction.RestartService -> onAction(MainAction.RestartService)
                            MainMoreMenuAction.DeleteAll -> showDelAllConfirm = true
                            MainMoreMenuAction.DeleteDuplicate -> showDelDuplicateConfirm = true
                            MainMoreMenuAction.DeleteInvalid -> showDelInvalidConfirm = true
                            MainMoreMenuAction.ExportAll -> onAction(MainAction.ExportAll)
                            MainMoreMenuAction.LocateSelected -> onAction(MainAction.LocateSelectedServer)
                            MainMoreMenuAction.SortByTestResults -> onAction(MainAction.SortByTestResults)
                            MainMoreMenuAction.TestAll -> onAction(MainAction.TestAllServers)
                            MainMoreMenuAction.TestAllRealPing -> onAction(MainAction.TestRealAllServers)
                            MainMoreMenuAction.UpdateSubscriptions -> onAction(MainAction.UpdateSubscriptions)
                        }
                    }
                )
            },
            bottomBar = {
               // MainBottomBar(
                  // displayText = displayText,
                  //  isRunning = isRunning,
                //    isDarkTheme = isDarkTheme,
                 //   onAction = onAction
              //  )
            }
                
        ) { innerPadding ->
            HiddifyDashboard(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                isRunning = isRunning,
                displayText = displayText,
                delayMs = delayMs,
                cfTraceInfo = cfTraceInfo,
                servers = servers,
                selectedGuid = uiState.selectedGuid,
                onToggleConnection = { onAction(MainAction.ToggleService) },
                
                                // [စည်းမျဉ်း-၂]: User မှ Manual ခလုတ် (Auto Fast Connect) နှိပ်လျှင် 50% သာ စစ်မည်
                onAutoTestAndSort = {
                    if (!isTesting) {
                        pendingAutoSelect = true
                        mainViewModel.smartPing(50, servers)  // <-- 50% (Round-Robin Random ဖြင့်)
                    }
                },

                
                onTestCurrent = { onAction(MainAction.TestCurrentServer) },
                onSelectServer = { guid -> onAction(MainAction.SelectServer(guid)) }
            )
        }

    }
}

// (MainScreen.kt ၏ အောက်ပိုင်း)
@Composable
fun HiddifyDashboard(
    modifier: Modifier = Modifier,
    isRunning: Boolean,
    displayText: String,
    delayMs: String,
    cfTraceInfo: String,
    servers: List<ServersCache>,
    selectedGuid: String?,
    onToggleConnection: () -> Unit,
    onAutoTestAndSort: () -> Unit, // <--- Type ပြန်ထည့်ပေးရပါမည်
    onTestCurrent: () -> Unit, // <--- ပျောက်သွားသော Parameter ပြန်ထည့်ပါ
    onSelectServer: (String) -> Unit // <--- ပျောက်သွားသော Parameter ပြန်ထည့်ပါ
) {
    val lifecycleOwner = LocalLifecycleOwner.current
 
    var isExpanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 250)
    )

    // Lifecycle-Aware Polling (3s -> 60s Cycle)
    LaunchedEffect(isRunning, lifecycleOwner) {
        if (isRunning) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    delay(1000)
                    onTestCurrent()
                    // မူလ: while (isActive) {
                    while (true) {
                        delay(3000)
                        onTestCurrent()
                        delay(60000)
                        onTestCurrent()
                    }
                }

        }
    }

    val buttonColor by animateColorAsState(
        targetValue = if (isRunning) Color(0xFF37474F) else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(durationMillis = 500)
    )

    val iconColor by animateColorAsState(
        targetValue = if (isRunning) Color.White else Color.Gray,
        animationSpec = tween(durationMillis = 500)
    )
    val scale by animateFloatAsState(
        targetValue = if (isRunning) 1.05f else 1f,
        animationSpec = tween(durationMillis = 300)
    )

        val realPing = remember(displayText, delayMs) {
        val match = Regex("(\\d+)\\s*ms").find(displayText)
        match?.value ?: delayMs
    }

    // ၁။ currentSelectedServer ကို အရင်ကြေညာရပါမည်
    val currentSelectedServer = remember(servers, selectedGuid) {
        servers.find { it.guid == selectedGuid } ?: servers.firstOrNull()
    }

    // ၂။ ထို့နောက်မှ ၎င်းကို ယူသုံးသော displayPingForCard ကို ထားရပါမည်
    val displayPingForCard = if (isRunning) realPing else currentSelectedServer?.testDelayString ?: ""
    val pingColor = if (displayPingForCard.contains("-") || displayPingForCard.isBlank()) colorPingRed else colorPing

    Column(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Status Text
        Text(
            text = when {
                displayText == "Connecting..." -> "Connecting..."
                isRunning -> "Connected"
                else -> "Not Connected"
            },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (isRunning || displayText == "Connecting...") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Real-time Ping Indicator
        if (isRunning) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = "Ping",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = realPing,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        } else {
            Spacer(modifier = Modifier.height(24.dp))
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Main Power Button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(150.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(buttonColor)
                .clickable { onToggleConnection() }
        ) {
            Icon(
                imageVector = Icons.Default.PowerSettingsNew,
                contentDescription = "Toggle VPN",
                tint = iconColor,
                modifier = Modifier.size(68.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Auto Fast Connect Button
        Button(
            onClick = onAutoTestAndSort,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Icon(imageVector = Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Auto Fast Connect (Test & Sort)")
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- HIDDIFY STYLE PROXY SELECTOR CARD WITH ARROW ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { isExpanded = !isExpanded },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Dns,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentSelectedServer?.profile?.remarks ?: "No Configuration",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentSelectedServer?.profile?.configType?.name ?: "Balancer (sticky-sessions)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                                        // --- အစားထိုးရမည့် အပိုင်း ---
                    // Ping ms of Selected Server
                    if (displayPingForCard.isNotBlank()) {
                        Text(
                            text = displayPingForCard,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = pingColor,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }


                    // Dropdown Arrow
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand Proxies",
                        modifier = Modifier
                            .size(26.dp)
                            .rotate(arrowRotation)
                    )
                }

                                // Expandable Proxy List
        AnimatedVisibility(
            visible = isExpanded,
            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            ) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                ) {
                    items(items = servers) { serverItem ->
                        val isSelected = serverItem.guid == selectedGuid
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable {
                                    onSelectServer(serverItem.guid)
                                    isExpanded = false
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Column {
                                    Text(
                                        text = serverItem.profile.remarks,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = serverItem.profile.configType.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorConfigType
                                    )
                                }
                            }
                            // Latency ms
                            Text(
                                text = serverItem.testDelayString,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = if (serverItem.testDelayMillis < 0L) colorPingRed else colorPing
                            )
                        }
                    }
                }
            }
        }
    } // End Column (inside Card)
    } // End Card
    } // End Main Column
} // End fun HiddifyDashboard (ဖိုင်၏ အဆုံးသတ်)
