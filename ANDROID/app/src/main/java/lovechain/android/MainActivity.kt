package lovechain.android

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import lovechain.core.ActivityType
import lovechain.core.ConfirmationStatus
import lovechain.core.CoupleProfile
import lovechain.core.LoveBlock
import lovechain.core.LoveBlockDraft
import lovechain.core.LoveBlockSignatureVerifier
import lovechain.core.LoveBlockType
import lovechain.core.LoveChain
import lovechain.core.LoveMapSnapshot
import lovechain.core.PartnerPresence
import lovechain.core.VisibilityMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LoveChainApp()
        }
    }
}

@Composable
fun LoveChainApp() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Cream
        ) {
            LoveChainScreen()
        }
    }
}

@Composable
private fun LoveChainScreen() {
    val context = LocalContext.current
    val sqliteStore = remember { LoveBlockSQLiteStore(context) }
    val loveMapStore = remember { LoveMapSQLiteStore(context) }
    val legacyJsonStore = remember { LoveBlockJsonStore(context) }
    val deviceKeyStore = remember { DeviceKeyStore() }
    val loveChain = remember { LoveChain() }
    val coupleProfile = remember { CoupleProfile("Maxim", "Dasha") }
    val partnerPresence = remember {
        PartnerPresence(
            partnerName = "Dasha",
            visibilityMode = VisibilityMode.ALWAYS_OPEN,
            movementStatus = "Together soon",
            batteryPercent = 74,
            distanceMeters = 420,
            lastUpdateText = "2 minutes ago",
            bluetoothConfirmed = false,
            gpsConfirmed = false
        )
    }

    var selectedTab by rememberSaveable { mutableStateOf(ScreenTab.CHAIN) }
    var blocks by remember { mutableStateOf(emptyList<LoveBlock>()) }
    var statusText by remember { mutableStateOf("") }
    var loveMapSnapshot by remember { mutableStateOf<LoveMapSnapshot?>(null) }
    var syncEndpointText by remember {
        mutableStateOf(
            context.getSharedPreferences(LoveMapForegroundService.PreferencesName, Context.MODE_PRIVATE)
                .getString(LoveMapForegroundService.SyncEndpointKey, "")
                .orEmpty()
        )
    }
    val localFingerprint = remember { deviceKeyStore.fingerprint() }
    val loveMapPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (hasLocationPermission) {
            startLoveMapService(context)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }

        val exportSucceeded = runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(LoveChainJsonCodec.formatExport(blocks, coupleProfile))
            } != null
        }.getOrDefault(false)

        if (!exportSucceeded) {
            statusText = context.getString(R.string.export_failed)
            return@rememberLauncherForActivityResult
        }

        statusText = context.getString(R.string.export_complete)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }

        val importedText = runCatching {
            context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { reader -> reader.readText() }
        }.getOrNull()

        if (importedText.isNullOrBlank()) {
            statusText = context.getString(R.string.import_failed)
            return@rememberLauncherForActivityResult
        }

        val importedBlocks = runCatching { LoveChainJsonCodec.parseExport(importedText) }.getOrNull()
        if (importedBlocks == null || !loveChain.isValid(importedBlocks) || !signaturesAreValid(importedBlocks)) {
            statusText = context.getString(R.string.import_failed)
            return@rememberLauncherForActivityResult
        }

        val importSucceeded = runCatching {
            sqliteStore.replaceAll(importedBlocks)
        }.isSuccess

        if (!importSucceeded) {
            statusText = context.getString(R.string.import_failed)
            return@rememberLauncherForActivityResult
        }

        blocks = importedBlocks
        statusText = context.getString(R.string.import_complete)
    }

    LaunchedEffect(Unit) {
        loveMapSnapshot = loveMapStore.latestSnapshot()
        val sqliteBlocks = sqliteStore.loadBlocks()
        val legacyBlocks = if (!legacyJsonStore.wasMigratedToSQLite()) {
            legacyJsonStore.loadBlocks()
        } else {
            emptyList()
        }
        val storedBlocks = if (sqliteBlocks.isNotEmpty()) sqliteBlocks else legacyBlocks
        val readyBlocks = if (storedBlocks.isEmpty()) {
            val genesisBlock = loveChain.createBlock(
                existingBlocks = emptyList(),
                draft = LoveBlockDraft(
                    type = LoveBlockType.GENESIS,
                    title = context.getString(R.string.genesis_title),
                    message = context.getString(R.string.genesis_message),
                    placeLabel = "LoveChain"
                )
            )
            val signature = deviceKeyStore.signBlockHash(genesisBlock.hash)
            listOf(loveChain.updateSignatures(genesisBlock, listOf(signature), localFingerprint))
        } else {
            signUnsignedBlocks(
                blocks = storedBlocks,
                loveChain = loveChain,
                deviceKeyStore = deviceKeyStore,
                localFingerprint = localFingerprint
            )
        }
        blocks = readyBlocks
        sqliteStore.replaceAll(readyBlocks)
        legacyJsonStore.markMigratedToSQLite()
    }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                loveMapSnapshot = loveMapStore.latestSnapshot()
            }
        }
        val intentFilter = IntentFilter(LoveMapForegroundService.ActionSnapshotUpdated)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, intentFilter)
        }

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Cream, RoseMist, Cream)
                )
            )
    ) {
        Header(coupleProfile = coupleProfile)
        TabSelector(
            selectedTab = selectedTab,
            onSelectedTabChange = { nextTab -> selectedTab = nextTab }
        )

        when (selectedTab) {
            ScreenTab.CHAIN -> ChainTab(
                blocks = blocks,
                totalCoins = loveChain.totalLoveCoins(blocks),
                chainIsValid = loveChain.isValid(blocks),
                signaturesAreValid = signaturesAreValid(blocks),
                statusText = statusText,
                onExport = { exportLauncher.launch("lovechain.json") },
                onImport = { importLauncher.launch(arrayOf("application/json", "text/*")) },
                onCreateDraft = { draft ->
                    val unsignedBlock = loveChain.createBlock(blocks, draft)
                    val signature = deviceKeyStore.signBlockHash(unsignedBlock.hash)
                    val nextBlock = loveChain.updateSignatures(
                        block = unsignedBlock,
                        signatures = listOf(signature),
                        localFingerprint = localFingerprint
                    )
                    val nextBlocks = blocks + nextBlock
                    blocks = nextBlocks
                    sqliteStore.appendBlock(nextBlock)
                    statusText = context.getString(R.string.block_signed)
                }
            )

            ScreenTab.MAP -> LoveMapTab(
                partnerPresence = partnerPresence,
                loveMapSnapshot = loveMapSnapshot,
                syncEndpointText = syncEndpointText,
                onSyncEndpointChange = { nextEndpoint -> syncEndpointText = nextEndpoint },
                onStartLoveMap = {
                    saveLoveMapSyncEndpoint(context, syncEndpointText)
                    val missingPermissions = missingLoveMapPermissions(context)
                    if (missingPermissions.isEmpty()) {
                        startLoveMapService(context)
                    } else {
                        loveMapPermissionLauncher.launch(missingPermissions.toTypedArray())
                    }
                },
                onStopLoveMap = { stopLoveMapService(context) }
            )
        }
    }
}

@Composable
private fun Header(coupleProfile: CoupleProfile) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.lovechain),
            contentDescription = stringResource(R.string.app_name),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "LoveChain",
                color = Wine,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = coupleProfile.displayName(),
                color = Cocoa,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = stringResource(R.string.app_tagline),
                color = MutedCocoa,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun TabSelector(
    selectedTab: ScreenTab,
    onSelectedTabChange: (ScreenTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TabButton(
            text = stringResource(R.string.tab_chain),
            selected = selectedTab == ScreenTab.CHAIN,
            onClick = { onSelectedTabChange(ScreenTab.CHAIN) },
            modifier = Modifier.weight(1f)
        )
        TabButton(
            text = stringResource(R.string.tab_map),
            selected = selectedTab == ScreenTab.MAP,
            onClick = { onSelectedTabChange(ScreenTab.MAP) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TabButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonColors = if (selected) {
        ButtonDefaults.buttonColors(containerColor = Wine, contentColor = Color.White)
    } else {
        ButtonDefaults.outlinedButtonColors(contentColor = Wine)
    }

    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = buttonColors,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ChainTab(
    blocks: List<LoveBlock>,
    totalCoins: Int,
    chainIsValid: Boolean,
    signaturesAreValid: Boolean,
    statusText: String,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onCreateDraft: (LoveBlockDraft) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ChainStats(
                totalCoins = totalCoins,
                blockCount = blocks.size,
                chainIsValid = chainIsValid,
                signaturesAreValid = signaturesAreValid
            )
        }

        item {
            QuickBlockActions(onCreateDraft = onCreateDraft)
        }

        item {
            TransferActions(
                statusText = statusText,
                onExport = onExport,
                onImport = onImport
            )
        }

        items(blocks.asReversed()) { block ->
            LoveBlockCard(block = block)
        }
    }
}

@Composable
private fun ChainStats(
    totalCoins: Int,
    blockCount: Int,
    chainIsValid: Boolean,
    signaturesAreValid: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatText(label = "LoveCoins", value = totalCoins.toString(), modifier = Modifier.weight(1f))
            StatText(label = stringResource(R.string.blocks_label), value = blockCount.toString(), modifier = Modifier.weight(1f))
            StatText(
                label = stringResource(R.string.chain_label),
                value = if (chainIsValid && signaturesAreValid) {
                    stringResource(R.string.chain_valid)
                } else {
                    stringResource(R.string.chain_broken)
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatText(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = value, color = Wine, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = MutedCocoa, fontSize = 12.sp)
    }
}

@Composable
private fun QuickBlockActions(onCreateDraft: (LoveBlockDraft) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Blush),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.create_block),
                color = Wine,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton(
                    text = "Together",
                    onClick = {
                        onCreateDraft(
                            LoveBlockDraft(
                                type = LoveBlockType.TOGETHERNESS,
                                title = "Together Block",
                                message = "Both partners signed a shared moment.",
                                proximityMinutes = 45,
                                placeLabel = "Together"
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
                ActionButton(
                    text = "Walk",
                    onClick = {
                        onCreateDraft(
                            LoveBlockDraft(
                                type = LoveBlockType.WALK,
                                title = "Walk Block",
                                message = "A simple walk became part of the chain.",
                                proximityMinutes = 72,
                                movingMinutes = 68,
                                distanceMeters = 4800,
                                activityType = ActivityType.WALKING,
                                placeLabel = "Daily route"
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton(
                    text = "Travel",
                    onClick = {
                        onCreateDraft(
                            LoveBlockDraft(
                                type = LoveBlockType.TRAVEL,
                                title = "Travel Block",
                                message = "A shared trip was added to LoveChain.",
                                movingMinutes = 210,
                                activityType = ActivityType.TRAVELING,
                                placeLabel = "New place"
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
                ActionButton(
                    text = "Gratitude",
                    onClick = {
                        onCreateDraft(
                            LoveBlockDraft(
                                type = LoveBlockType.GRATITUDE,
                                title = "Gratitude Block",
                                message = "Thank you for today.",
                                placeLabel = "Home"
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TransferActions(
    statusText: String,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.transfer_chain),
                color = Wine,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton(
                    text = stringResource(R.string.export_chain),
                    onClick = onExport,
                    modifier = Modifier.weight(1f)
                )
                ActionButton(
                    text = stringResource(R.string.import_chain),
                    onClick = onImport,
                    modifier = Modifier.weight(1f)
                )
            }
            if (statusText.isNotBlank()) {
                Text(text = statusText, color = MutedCocoa, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Wine),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 10.dp)
    ) {
        Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LoveBlockCard(block: LoveBlock) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Rose),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = block.index.toString(),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(text = block.title, color = Wine, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(text = formatTimestamp(block.timestamp), color = MutedCocoa, fontSize = 12.sp)
                }
            }

            if (!block.message.isNullOrBlank()) {
                Text(text = block.message, color = Cocoa, fontSize = 14.sp)
            }

            BlockDetails(block = block)

            Text(
                text = "Hash ${block.hash.take(12)}...",
                color = MutedCocoa,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BlockDetails(block: LoveBlock) {
    val detailTexts = listOfNotNull(
        "+${block.rewardCoins} LoveCoins",
        block.confirmationStatus.label(),
        block.placeLabel,
        block.proximityMinutes?.let { minutes -> "$minutes min near" },
        block.movingMinutes?.let { minutes -> "$minutes min moving" },
        block.distanceMeters?.let { meters -> "${meters / 1000.0} km" },
        block.activityType?.name?.lowercase()
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        for (detailText in detailTexts.take(3)) {
            DetailPill(text = detailText)
        }
    }
}

@Composable
private fun DetailPill(text: String) {
    Text(
        text = text,
        color = Wine,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .background(RoseMist, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp)
    )
}

@Composable
private fun LoveMapTab(
    partnerPresence: PartnerPresence,
    loveMapSnapshot: LoveMapSnapshot?,
    syncEndpointText: String,
    onSyncEndpointChange: (String) -> Unit,
    onStartLoveMap: () -> Unit,
    onStopLoveMap: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            LoveMapControls(
                loveMapSnapshot = loveMapSnapshot,
                syncEndpointText = syncEndpointText,
                onSyncEndpointChange = onSyncEndpointChange,
                onStartLoveMap = onStartLoveMap,
                onStopLoveMap = onStopLoveMap
            )
        }

        item {
            LoveMapPreview(
                partnerPresence = partnerPresence,
                loveMapSnapshot = loveMapSnapshot
            )
        }

        item {
            TransparencyCard()
        }

        item {
            FutureServicesCard(loveMapSnapshot = loveMapSnapshot)
        }
    }
}

@Composable
private fun LoveMapControls(
    loveMapSnapshot: LoveMapSnapshot?,
    syncEndpointText: String,
    onSyncEndpointChange: (String) -> Unit,
    onStartLoveMap: () -> Unit,
    onStopLoveMap: () -> Unit
) {
    val running = loveMapSnapshot?.serviceRunning == true

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.lovemap_service_title),
                color = Wine,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (running) {
                    stringResource(R.string.lovemap_running)
                } else {
                    stringResource(R.string.lovemap_paused)
                },
                color = Cocoa,
                fontSize = 14.sp
            )
            OutlinedTextField(
                value = syncEndpointText,
                onValueChange = onSyncEndpointChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(text = stringResource(R.string.lovemap_sync_endpoint)) }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton(
                    text = stringResource(R.string.lovemap_start),
                    onClick = onStartLoveMap,
                    modifier = Modifier.weight(1f)
                )
                ActionButton(
                    text = stringResource(R.string.lovemap_pause),
                    onClick = onStopLoveMap,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = stringResource(R.string.lovemap_consent),
                color = MutedCocoa,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun LoveMapPreview(
    partnerPresence: PartnerPresence,
    loveMapSnapshot: LoveMapSnapshot?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        colors = CardDefaults.cardColors(containerColor = MapGreen),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            MapRouteLine(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(120.dp)
                    .padding(horizontal = 40.dp)
            )
            PartnerMarker(
                name = "Maxim",
                label = "You",
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 44.dp, bottom = 58.dp)
            )
            PartnerMarker(
                name = partnerPresence.partnerName,
                label = partnerPresence.movementStatus,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 42.dp, top = 50.dp)
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.92f))
                    .padding(14.dp)
            ) {
                Text(text = stringResource(R.string.lovemap_title), color = Wine, fontWeight = FontWeight.Bold)
                Text(
                    text = loveMapStatusText(partnerPresence, loveMapSnapshot),
                    color = Cocoa,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun MapRouteLine(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(RoseMist.copy(alpha = 0.72f))
    )
}

@Composable
private fun PartnerMarker(
    name: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Wine),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.take(1),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(text = label, color = Wine, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TransparencyCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = stringResource(R.string.mutual_openness), color = Wine, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                text = stringResource(R.string.lovemap_placeholder),
                color = Cocoa,
                fontSize = 14.sp
            )
            Text(text = stringResource(R.string.lovemap_default_mode), color = MutedCocoa, fontSize = 13.sp)
        }
    }
}

@Composable
private fun FutureServicesCard(loveMapSnapshot: LoveMapSnapshot?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = stringResource(R.string.next_services), color = Wine, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            ServiceRow(name = "LocationService", status = locationStatus(loveMapSnapshot))
            ServiceRow(name = "BluetoothPresenceService", status = bluetoothStatus(loveMapSnapshot))
            ServiceRow(name = "LoveMapSyncClient", status = loveMapSnapshot?.lastSyncStatus ?: "sync disabled")
            ServiceRow(name = "MotionDetector", status = "standing placeholder")
        }
    }
}

@Composable
private fun ServiceRow(name: String, status: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = name, color = Cocoa, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text(text = status, color = MutedCocoa, fontSize = 13.sp)
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val formatter = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

private fun loveMapStatusText(
    partnerPresence: PartnerPresence,
    loveMapSnapshot: LoveMapSnapshot?
): String {
    val locationSnapshot = loveMapSnapshot?.locationSnapshot
    val bluetoothSnapshot = loveMapSnapshot?.bluetoothPresenceSnapshot
    val batteryText = locationSnapshot?.batteryPercent?.let { percent -> "battery $percent%" }
        ?: "battery ${partnerPresence.batteryPercent ?: 0}%"
    val distanceText = "${partnerPresence.distanceMeters ?: 0} m apart"
    val bluetoothText = if (bluetoothSnapshot?.partnerDeviceSeen == true) {
        "BLE near ${bluetoothSnapshot.rssi ?: 0} dBm"
    } else {
        "BLE far"
    }
    val locationText = if (locationSnapshot == null) {
        "GPS waiting"
    } else {
        "GPS ${"%.5f".format(locationSnapshot.latitude)}, ${"%.5f".format(locationSnapshot.longitude)}"
    }

    return listOf(distanceText, batteryText, bluetoothText, locationText).joinToString(separator = " · ")
}

private fun locationStatus(loveMapSnapshot: LoveMapSnapshot?): String {
    val locationSnapshot = loveMapSnapshot?.locationSnapshot ?: return "waiting"
    return "${"%.5f".format(locationSnapshot.latitude)}, ${"%.5f".format(locationSnapshot.longitude)}"
}

private fun bluetoothStatus(loveMapSnapshot: LoveMapSnapshot?): String {
    val bluetoothSnapshot = loveMapSnapshot?.bluetoothPresenceSnapshot ?: return "waiting"
    if (!bluetoothSnapshot.partnerDeviceSeen) {
        return "far"
    }

    return "near ${bluetoothSnapshot.rssi ?: 0} dBm"
}

private fun startLoveMapService(context: Context) {
    val intent = Intent(context, LoveMapForegroundService::class.java)
        .setAction(LoveMapForegroundService.ActionStart)
    ContextCompat.startForegroundService(context, intent)
}

private fun stopLoveMapService(context: Context) {
    val intent = Intent(context, LoveMapForegroundService::class.java)
        .setAction(LoveMapForegroundService.ActionStop)
    context.startService(intent)
}

private fun saveLoveMapSyncEndpoint(context: Context, syncEndpoint: String) {
    context.getSharedPreferences(LoveMapForegroundService.PreferencesName, Context.MODE_PRIVATE)
        .edit()
        .putString(LoveMapForegroundService.SyncEndpointKey, syncEndpoint.trim())
        .apply()
}

private fun missingLoveMapPermissions(context: Context): List<String> {
    val permissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
    }

    return permissions.filter { permission -> !hasPermission(context, permission) }
}

private fun hasPermission(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

private fun signaturesAreValid(blocks: List<LoveBlock>): Boolean {
    for (block in blocks) {
        if (block.signatures.isEmpty()) {
            return false
        }

        for (signature in block.signatures) {
            if (!LoveBlockSignatureVerifier.verify(block.hash, signature)) {
                return false
            }
        }
    }

    return true
}

private fun signUnsignedBlocks(
    blocks: List<LoveBlock>,
    loveChain: LoveChain,
    deviceKeyStore: DeviceKeyStore,
    localFingerprint: String
): List<LoveBlock> {
    val signedBlocks = mutableListOf<LoveBlock>()

    for (block in blocks) {
        if (block.signatures.isNotEmpty()) {
            signedBlocks.add(block)
            continue
        }

        val signature = deviceKeyStore.signBlockHash(block.hash)
        signedBlocks.add(
            loveChain.updateSignatures(
                block = block,
                signatures = listOf(signature),
                localFingerprint = localFingerprint
            )
        )
    }

    return signedBlocks
}

private fun ConfirmationStatus.label(): String {
    return when (this) {
        ConfirmationStatus.DRAFT -> "draft"
        ConfirmationStatus.SIGNED_BY_ME -> "signed"
        ConfirmationStatus.SIGNED_BY_PARTNER -> "partner signed"
        ConfirmationStatus.CONFIRMED_BY_BOTH -> "confirmed"
    }
}

private enum class ScreenTab {
    CHAIN,
    MAP
}

private val Cream = Color(0xFFFFF8F5)
private val Blush = Color(0xFFFFE7E1)
private val RoseMist = Color(0xFFF8D5D8)
private val Rose = Color(0xFFD85B78)
private val Wine = Color(0xFF8F2447)
private val Cocoa = Color(0xFF4B2E2A)
private val MutedCocoa = Color(0xFF806A64)
private val MapGreen = Color(0xFFE7F0E7)
