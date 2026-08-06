package com.v2ray.ang.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.QRCodeDialog
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

    val isDarkTheme = LocalDarkTheme.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    // UI State for Dialogs
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
                MainBottomBar(
                    displayText = displayText,
                    isRunning = isRunning,
                    isDarkTheme = isDarkTheme,
                    onAction = onAction
                )
            }
        ) { innerPadding ->
            // --- HIDDIFY STYLE DASHBOARD UI ---
            HiddifyDashboard(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                isRunning = isRunning,
                displayText = displayText,
                // Ping ms ကို လက်ရှိ delay အဖြစ် ယူဆသည် (နောက်ပိုင်း ViewModel မှ အတိအကျ ယူမည်)
                delayMs = uiState.selectedGuid?.let { "145 ms" } ?: "0 ms", 
                onToggleConnection = { onAction(MainAction.ToggleService) },
                onAutoTestAndSort = {
                    // Update Sub -> Test -> Sort အား ဆင့်ကဲခေါ်မည်
                    onAction(MainAction.UpdateSubscriptions)
                    scope.launch {
                        kotlinx.coroutines.delay(2000)
                        onAction(MainAction.TestRealAllServers)
                        kotlinx.coroutines.delay(3000)
                        onAction(MainAction.SortByTestResults)
                    }
                }
            )
        }
    }
}

@Composable
fun HiddifyDashboard(
    modifier: Modifier = Modifier,
    isRunning: Boolean,
    displayText: String,
    delayMs: String,
    onToggleConnection: () -> Unit,
    onAutoTestAndSort: () -> Unit
) {
    // Animations for the Connect Button
    val buttonColor by animateColorAsState(
        targetValue = if (isRunning) Color(0xFF00BFA5) else Color(0xFFE0E0E0),
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

    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        
        // --- Status Text (Connected / Not Connected) ---
        Text(
            text = if (isRunning) "Connected" else "Not Connected",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        // --- Delay (Ping) Indicator ---
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
                    text = delayMs,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        } else {
            Spacer(modifier = Modifier.height(24.dp)) // Placeholder
        }

        Spacer(modifier = Modifier.height(48.dp))

        // --- BIG CENTER CONNECT BUTTON ---
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(160.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(buttonColor)
                .clickable { onToggleConnection() }
        ) {
            Icon(
                imageVector = Icons.Default.PowerSettingsNew,
                contentDescription = "Toggle VPN",
                tint = iconColor,
                modifier = Modifier.size(72.dp)
            )
        }

        Spacer(modifier = Modifier.height(64.dp))

        // --- Info Card (Cloudflare Trace & Routing Info) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Connection Details",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                // ဒီနေရာမှာ Cloudflare Trace Data (IP, Colo, Warp) ကို လာပြပါမည် (Phase 5)
                Text(
                    text = if (isRunning) displayText else "Ready to connect...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Left
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- Fast Connect / Auto Sort Button ---
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
    }
}
