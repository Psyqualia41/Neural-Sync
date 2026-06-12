package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.HeartRateEntry
import com.example.data.SleepEntry
import com.example.data.SyncState
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(
    viewModel: HealthViewModel,
    modifier: Modifier = Modifier
) {
    val heartRates by viewModel.allHeartRates.collectAsStateWithLifecycle()
    val recentHeartRates by viewModel.recentHeartRates.collectAsStateWithLifecycle()
    val sleepEntries by viewModel.allSleepEntries.collectAsStateWithLifecycle()
    val syncStates by viewModel.allSyncStates.collectAsStateWithLifecycle()
    val weeklyInsight by viewModel.weeklyInsight.collectAsStateWithLifecycle()

    val isSyncingFitbit by viewModel.isSyncingFitbit.collectAsStateWithLifecycle()
    val isSyncingAppleWatch by viewModel.isSyncingAppleWatch.collectAsStateWithLifecycle()
    val isGeneratingInsights by viewModel.isGeneratingInsights.collectAsStateWithLifecycle()

    val restingSpikes by viewModel.restingSpikes.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var previousSpikeCount by remember { mutableStateOf(-1) }

    LaunchedEffect(restingSpikes) {
        if (previousSpikeCount != -1 && restingSpikes.size > previousSpikeCount) {
            val mostRecentSpike = restingSpikes.firstOrNull()
            if (mostRecentSpike != null) {
                android.widget.Toast.makeText(
                    context,
                    "⚠️ TELEMETRY WARNING: Unusual Resting Heart Rate Spike of ${mostRecentSpike.bpm} BPM registered (${mostRecentSpike.deviceSource})!",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
        previousSpikeCount = restingSpikes.size
    }

    var activeTab by remember { mutableStateOf(0) } // 0: Dashboard, 1: Wearables, 2: Insights
    var showAddHrDialog by remember { mutableStateOf(false) }
    var showAddSleepDialog by remember { mutableStateOf(false) }
    var showFitbitAuthDialog by remember { mutableStateOf(false) }
    var showAppleAuthDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(CyberBlack),
        topBar = {
            HeaderArea(
                onReset = { viewModel.resetData() }
            )
        },
        containerColor = CyberBlack
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // High-fidelity Sliding Tech Tab Bar
            TabSelector(
                activeTab = activeTab,
                onTabSelected = { activeTab = it }
            )

            // Animated Tab Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (activeTab) {
                    0 -> DashboardTab(
                        heartRates = heartRates,
                        recentHeartRates = recentHeartRates,
                        sleepEntries = sleepEntries,
                        restingSpikes = restingSpikes,
                        onDismissSpike = { viewModel.dismissSpikeAlert(it) },
                        onAddHrClick = { showAddHrDialog = true },
                        onAddSleepClick = { showAddSleepDialog = true }
                    )
                    1 -> WearablesTab(
                        syncStates = syncStates,
                        isSyncingFitbit = isSyncingFitbit,
                        isSyncingAppleWatch = isSyncingAppleWatch,
                        onSyncFitbit = { showFitbitAuthDialog = true },
                        onSyncAppleWatch = { showAppleAuthDialog = true }
                    )
                    2 -> InsightsTab(
                        weeklyInsight = weeklyInsight?.insightText ?: "",
                        generatedTime = weeklyInsight?.generatedTime ?: 0L,
                        isGenerating = isGeneratingInsights,
                        onRequestGeneration = { viewModel.generateInsights() },
                        hasData = heartRates.isNotEmpty() || sleepEntries.isNotEmpty()
                    )
                }
            }
        }
    }

    // --- Overlay Dialogs ---

    if (showAddHrDialog) {
        AddHrDialog(
            onDismiss = { showAddHrDialog = false },
            onConfirm = { bpm, activity ->
                viewModel.addHeartRate(bpm, activity)
                showAddHrDialog = false
            }
        )
    }

    if (showAddSleepDialog) {
        AddSleepDialog(
            onDismiss = { showAddSleepDialog = false },
            onConfirm = { day, deep, light, rem, awake ->
                viewModel.addSleep(day, deep, light, rem, awake)
                showAddSleepDialog = false
            }
        )
    }

    if (showFitbitAuthDialog) {
        FitbitAuthDialog(
            isSyncing = isSyncingFitbit,
            onDismiss = { showFitbitAuthDialog = false },
            onAuthenticate = { 
                viewModel.syncFitbit()
                showFitbitAuthDialog = false
            }
        )
    }

    if (showAppleAuthDialog) {
        AppleWatchAuthDialog(
            isSyncing = isSyncingAppleWatch,
            onDismiss = { showAppleAuthDialog = false },
            onAuthenticate = { 
                viewModel.syncAppleWatch()
                showAppleAuthDialog = false
            }
        )
    }
}

// ==========================================
// Sub-Composables & Custom UI Elements
// ==========================================

@Composable
fun HeaderArea(onReset: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .drawBehind {
                // Subtle high-tech accent line under header
                drawLine(
                    color = BorderGray,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App Identity with glowing breathing pulse
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_alpha"
        )

        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(NeonGreen.copy(alpha = pulseAlpha))
                .border(1.dp, NeonGreen, CircleShape)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "NEURAL SYNC",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = OffWhite,
                    fontFamily = FontFamily.Monospace
                )
            )
            val sdf = SimpleDateFormat("EEEE, MMMM dd", Locale.getDefault())
            Text(
                text = sdf.format(Date()).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MutedText,
                    fontFamily = FontFamily.Monospace
                )
            )
        }

        IconButton(
            onClick = { showMenu = true },
            modifier = Modifier.testTag("app_menu_button")
        ) {
            Icon(Icons.Default.MoreVert, contentDescription = "Options Menu", tint = OffWhite)
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.background(CyberCardBg)
        ) {
            DropdownMenuItem(
                text = { Text("Reset Application Data", color = NeonPink) },
                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = NeonPink) },
                onClick = {
                    onReset()
                    showMenu = false
                }
            )
        }
    }
}

@Composable
fun TabSelector(
    activeTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .background(CyberDarkGray, RoundedCornerShape(10.dp))
            .border(1.dp, BorderGray, RoundedCornerShape(10.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val tabs = listOf("⚡ TELEMETRY", "⌚ WEARABLES", "🧠 AI INSIGHTS")
        tabs.forEachIndexed { index, title ->
            val isSelected = activeTab == index
            val animatedBg by animateColorAsState(
                targetValue = if (isSelected) CyberMediumGray else Color.Transparent,
                animationSpec = tween(250),
                label = "tab_bg"
            )
            val animatedBorder by animateColorAsState(
                targetValue = if (isSelected) NeonTeal.copy(alpha = 0.5f) else Color.Transparent,
                animationSpec = tween(250),
                label = "tab_border"
            )
            val animatedTextColor by animateColorAsState(
                targetValue = if (isSelected) NeonTeal else MutedText,
                animationSpec = tween(250),
                label = "tab_text"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(animatedBg)
                    .border(1.dp, animatedBorder, RoundedCornerShape(8.dp))
                    .clickable { onTabSelected(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = animatedTextColor,
                        fontFamily = FontFamily.Monospace
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ==========================================
// Tab 1: Dashboard Tab Composable
// ==========================================

@Composable
fun DashboardTab(
    heartRates: List<HeartRateEntry>,
    recentHeartRates: List<HeartRateEntry>,
    sleepEntries: List<SleepEntry>,
    restingSpikes: List<HeartRateSpike>,
    onDismissSpike: (Int) -> Unit,
    onAddHrClick: () -> Unit,
    onAddSleepClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // --- HEART RATE ALERTS ---
        if (restingSpikes.isNotEmpty()) {
            item {
                HeartRateSpikeAlertSection(
                    spikes = restingSpikes,
                    onDismiss = onDismissSpike
                )
            }
        }

        // --- HEART RATE CARD ---
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = CyberCardBg),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Favorite, contentDescription = null, tint = NeonPink)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "PULSE LOGS (BPM)",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = OffWhite
                                )
                            )
                        }
                        IconButton(
                            onClick = onAddHrClick,
                            modifier = Modifier
                                .size(32.dp)
                                .background(CyberMediumGray, CircleShape)
                                .testTag("add_hr_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add telemetry", tint = NeonTeal, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (heartRates.isEmpty()) {
                        EmptyStateWidget(
                            icon = Icons.Default.Favorite,
                            message = "No heart rate telemetry synced. Connect a device in Wearables to sync historical readings.",
                            buttonText = "Log Heart Rate",
                            onButtonClick = onAddHrClick
                        )
                    } else {
                        // Display Live Readings
                        val latestHr = heartRates.first()
                        val averageHr = heartRates.map { it.bpm }.average().toInt()
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            MetricsColumn("LATEST", "${latestHr.bpm}", NeonPink)
                            MetricsColumn("AVG BPM", "$averageHr", NeonTeal)
                            MetricsColumn("PEAK", "${heartRates.maxOfOrNull { it.bpm } ?: 0}", NeonOrange)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Custom Glowing Canvas Chart
                        HealthLineChart(heartRates = heartRates)
                    }
                }
            }
        }

        // --- SLEEP TRACKER CARD ---
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CyberCardBg),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bedtime, contentDescription = null, tint = NeonTeal)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SLEEP CYCLES",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = OffWhite
                                )
                            )
                        }
                        IconButton(
                            onClick = onAddSleepClick,
                            modifier = Modifier
                                .size(32.dp)
                                .background(CyberMediumGray, CircleShape)
                                .testTag("add_sleep_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add sleep", tint = NeonGreen, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (sleepEntries.isEmpty()) {
                        EmptyStateWidget(
                            icon = Icons.Default.Bedtime,
                            message = "No sleep logs registered. Use cellular sync with wearable devices or type input parameters.",
                            buttonText = "Log Sleep Session",
                            onButtonClick = onAddSleepClick
                        )
                    } else {
                        val latestSleep = sleepEntries.last() // Chronological ordering
                        val totalMinutes = latestSleep.deepMinutes + latestSleep.lightMinutes + latestSleep.remMinutes + latestSleep.awakeMinutes
                        val hours = totalMinutes / 60
                        val mins = totalMinutes % 60

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "LAST NIGHT (${latestSleep.dateLabel})",
                                    style = MaterialTheme.typography.labelSmall.copy(color = MutedText, letterSpacing = 1.sp)
                                )
                                Text(
                                    text = "${hours}H ${mins}M",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        color = OffWhite
                                    )
                                )
                            }
                            
                            // Live Source Indicator Chip
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CyberMediumGray)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "SRC: ${latestSleep.deviceSource.uppercase()}",
                                    style = MaterialTheme.typography.labelSmall.copy(color = NeonGreen, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Custom Segmented Sleep Cycle Canvas bar
                        HealthSleepBar(sleep = latestSleep)

                        Spacer(modifier = Modifier.height(16.dp))

                        // Sleep Cycle Legend Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            SleepLegendItem("DEEP", "${latestSleep.deepMinutes}m", NeonGreen)
                            SleepLegendItem("LIGHT", "${latestSleep.lightMinutes}m", NeonTeal)
                            SleepLegendItem("REM", "${latestSleep.remMinutes}m", NeonOrange)
                            SleepLegendItem("AWAKE", "${latestSleep.awakeMinutes}m", NeonPink)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricsColumn(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MutedText,
                fontFamily = FontFamily.Monospace
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Black,
                color = color
            )
        )
    }
}

@Composable
fun SleepLegendItem(label: String, duration: String, color: Color) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(color = MutedText, letterSpacing = 0.5.sp)
            )
        }
        Text(
            text = duration,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = OffWhite),
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

// Custom Line-Chart in Jetpack Compose Canvas
@Composable
fun HealthLineChart(heartRates: List<HeartRateEntry>) {
    // Reverse or sort entries chronologically for drawing
    val points = heartRates.sortedBy { it.timestamp }.takeLast(20)
    
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .padding(top = 8.dp)
    ) {
        if (points.isEmpty()) return@Canvas

        val maxBpm = (points.maxOf { it.bpm } + 10).toFloat()
        val minBpm = (points.minOf { it.bpm } - 10).coerceAtLeast(30).toFloat()
        val deltaY = if (maxBpm - minBpm == 0f) 1f else maxBpm - minBpm

        val width = size.width
        val height = size.height

        val stepX = width / (points.size - 1).coerceAtLeast(1)

        val path = Path()
        val fillPath = Path()

        points.forEachIndexed { i, entry ->
            val cx = i * stepX
            val cy = height - ((entry.bpm - minBpm) / deltaY * height)

            if (i == 0) {
                path.moveTo(cx, cy)
                fillPath.moveTo(cx, height)
                fillPath.lineTo(cx, cy)
            } else {
                val prevX = (i - 1) * stepX
                val prevBpm = points[i - 1].bpm
                val prevY = height - ((prevBpm - minBpm) / deltaY * height)
                // Curve calculation for smoothing
                path.cubicTo((prevX + cx) / 2f, prevY, (prevX + cx) / 2f, cy, cx, cy)
                fillPath.cubicTo((prevX + cx) / 2f, prevY, (prevX + cx) / 2f, cy, cx, cy)
            }
            if (i == points.size - 1) {
                fillPath.lineTo(cx, height)
                fillPath.close()
            }
        }

        // Draw background horizontal faint gridlines
        for (g in 1..3) {
            val gy = height * (g / 4f)
            drawLine(
                color = BorderGray.copy(alpha = 0.3f),
                start = Offset(0f, gy),
                end = Offset(width, gy),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Fill area under curves with beautiful gradient
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    NeonTeal.copy(alpha = 0.35f),
                    NeonTeal.copy(alpha = 0.0f)
                )
            )
        )

        // Draw primary line
        drawPath(
            path = path,
            color = NeonTeal,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        // Highlight latest point with a glowing dot
        if (points.isNotEmpty()) {
            val lastX = (points.size - 1) * stepX
            val lastY = height - ((points.last().bpm - minBpm) / deltaY * height)
            
            drawCircle(
                color = NeonPink.copy(alpha = 0.4f),
                radius = 10.dp.toPx(),
                center = Offset(lastX, lastY)
            )
            drawCircle(
                color = NeonPink,
                radius = 4.dp.toPx(),
                center = Offset(lastX, lastY)
            )
        }
    }
}

// Custom segment sleep bar using Canvas
@Composable
fun HealthSleepBar(sleep: SleepEntry) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
    ) {
        val total = (sleep.deepMinutes + sleep.lightMinutes + sleep.remMinutes + sleep.awakeMinutes).toFloat()
        if (total == 0f) return@Canvas

        val w = size.width
        val h = size.height

        val deepPercent = sleep.deepMinutes / total
        val lightPercent = sleep.lightMinutes / total
        val remPercent = sleep.remMinutes / total
        val awakePercent = sleep.awakeMinutes / total

        var curX = 0f

        // Deep segment
        val deepW = w * deepPercent
        drawRect(
            color = NeonGreen,
            topLeft = Offset(curX, 0f),
            size = Size(deepW, h)
        )
        curX += deepW

        // Light segment
        val lightW = w * lightPercent
        drawRect(
            color = NeonTeal,
            topLeft = Offset(curX, 0f),
            size = Size(lightW, h)
        )
        curX += lightW

        // REM segment
        val remW = w * remPercent
        drawRect(
            color = NeonOrange,
            topLeft = Offset(curX, 0f),
            size = Size(remW, h)
        )
        curX += remW

        // Awake segment
        val awakeW = w * awakePercent
        drawRect(
            color = NeonPink,
            topLeft = Offset(curX, 0f),
            size = Size(awakeW, h)
        )
    }
}

@Composable
fun EmptyStateWidget(
    icon: ImageVector,
    message: String,
    buttonText: String,
    onButtonClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MutedText.copy(alpha = 0.5f),
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MutedText,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            ),
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onButtonClick,
            colors = ButtonDefaults.buttonColors(containerColor = CyberMediumGray),
            border = BorderStroke(1.dp, BorderGray),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(text = buttonText, color = NeonTeal, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
        }
    }
}

// ==========================================
// Tab 2: Wearables Connection Composable
// ==========================================

@Composable
fun WearablesTab(
    syncStates: List<SyncState>,
    isSyncingFitbit: Boolean,
    isSyncingAppleWatch: Boolean,
    onSyncFitbit: () -> Unit,
    onSyncAppleWatch: () -> Unit
) {
    val showFitbitConnected = syncStates.find { it.deviceName == "Fitbit" }?.isConnected ?: false
    val showAppleConnected = syncStates.find { it.deviceName == "Apple Watch" }?.isConnected ?: false
    val lastFitbitSync = syncStates.find { it.deviceName == "Fitbit" }?.lastSyncedTime ?: 0L
    val lastAppleSync = syncStates.find { it.deviceName == "Apple Watch" }?.lastSyncedTime ?: 0L

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "PAIR SENSOR WEARABLES",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = MutedText,
                    fontFamily = FontFamily.Monospace
                ),
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
        }

        // --- FITBIT CARD ---
        item {
            DeviceSyncCard(
                deviceName = "FITBIT CLOUD DOCK",
                iconRes = Icons.Default.DirectionsRun,
                isConnected = showFitbitConnected,
                lastSynced = lastFitbitSync,
                isSyncing = isSyncingFitbit,
                onConnectClick = onSyncFitbit,
                primaryColor = NeonTeal,
                tagId = "fitbit_card"
            )
        }

        // --- APPLE WATCH CARD ---
        item {
            DeviceSyncCard(
                deviceName = "APPLE WATCH INTERFACE",
                iconRes = Icons.Default.Watch,
                isConnected = showAppleConnected,
                lastSynced = lastAppleSync,
                isSyncing = isSyncingAppleWatch,
                onConnectClick = onSyncAppleWatch,
                primaryColor = NeonGreen,
                tagId = "apple_watch_card"
            )
        }

        item {
            // General pairing info instructions notice block (No Dead-End UI Elements!)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CyberCardBg)
                    .border(1.dp, BorderGray, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = NeonOrange)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "HOW DOES SYNC WORK?",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = OffWhite,
                                letterSpacing = 1.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Because this is an Android sandbox prototype, clicking 'Connect & Mirror' simulates OAuth pairing. It connects to the wearable's Cloud Mirror, registers secure token handshakes, and downloads the last 7 days of raw heart rate (resting, walking, cardio) and deep/REM sleeping cycles directly into your Room Database.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MutedText,
                            lineHeight = 20.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceSyncCard(
    deviceName: String,
    iconRes: ImageVector,
    isConnected: Boolean,
    lastSynced: Long,
    isSyncing: Boolean,
    onConnectClick: () -> Unit,
    primaryColor: Color,
    tagId: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tagId)
            .shadow(4.dp, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CyberCardBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular Glowing Device Icon
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isConnected) primaryColor.copy(alpha = 0.15f) else CyberMediumGray)
                        .border(1.dp, if (isConnected) primaryColor else BorderGray, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = iconRes,
                        contentDescription = null,
                        tint = if (isConnected) primaryColor else MutedText,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = deviceName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = OffWhite,
                            letterSpacing = 0.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    if (isSyncing) {
                        Text(
                            text = "COMMUNICATING CLOUD API...",
                            style = MaterialTheme.typography.labelSmall.copy(color = NeonOrange, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        )
                    } else if (isConnected) {
                        val simpleDateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                        Text(
                            text = "CONNECTED • LAST SECURE SYNC: ${simpleDateFormat.format(Date(lastSynced))}",
                            style = MaterialTheme.typography.labelSmall.copy(color = NeonGreen, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        )
                    } else {
                        Text(
                            text = "STATUS: REGISTER NOT INTEGRATED",
                            style = MaterialTheme.typography.labelSmall.copy(color = MutedText, letterSpacing = 0.5.sp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action button inside Card
            if (isSyncing) {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberMediumGray,
                        disabledContainerColor = CyberMediumGray
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = NeonOrange,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SECURE CLOUD CRYPTO HANDSHAKE...", color = NeonOrange, style = MaterialTheme.typography.labelMedium)
                }
            } else {
                Button(
                    onClick = onConnectClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConnected) CyberMediumGray else primaryColor
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = if (isConnected) BorderStroke(1.dp, BorderGray) else null
                ) {
                    Icon(
                        imageVector = if (isConnected) Icons.Default.Sync else Icons.Default.Link,
                        contentDescription = null,
                        tint = if (isConnected) OffWhite else CyberBlack,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isConnected) "FORCE TELEMETRY SYNC" else "CONNECT & MIRROR ACCOUNT",
                        color = if (isConnected) OffWhite else CyberBlack,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                    )
                }
            }
        }
    }
}

// ==========================================
// Tab 3: Insights Composable (Gemini AI Reports)
// ==========================================

@Composable
fun InsightsTab(
    weeklyInsight: String,
    generatedTime: Long,
    isGenerating: Boolean,
    onRequestGeneration: () -> Unit,
    hasData: Boolean
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text(
                text = "NEURAL AI COMPILER",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = MutedText,
                    fontFamily = FontFamily.Monospace
                ),
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
        }

        // Action Trigger Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CyberMediumGray),
                border = BorderStroke(1.dp, BorderGray),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "🔬 COGNITIVE COACH DIRECTIVE",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = NeonTeal,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Trigger Gemini Large Language models to scan local SQLite database repositories. Gemini maps resting telemetry trends and sleep architecture to write actionable biocuration routines.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = OffWhite, lineHeight = 20.sp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!hasData) {
                        Text(
                            text = "⚠️ Empty logs: Please pair a device or log manuals to generate reports.",
                            color = NeonOrange,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (isGenerating) {
                        Button(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(disabledContainerColor = CyberMediumGray),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = NeonTeal, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("SCANNING ARCHIVES & SOLVING LABS...", color = NeonTeal, style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        Button(
                            onClick = onRequestGeneration,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("generate_insights_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonTeal),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "COMPILE PERSONALIZED REPORT",
                                color = CyberBlack,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                            )
                        }
                    }
                }
            }
        }

        // Render Generated Report block
        if (isGenerating) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CyberCardBg)
                        .border(1.dp, BorderGray, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = NeonGreen)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "GEMINI IS AUDITING YOUR TELEMETRY...",
                            style = MaterialTheme.typography.labelMedium.copy(color = MutedText, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
                        )
                    }
                }
            }
        } else if (weeklyInsight.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = CyberCardBg),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(NeonGreen, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "SECURE INSIGHT DOSSIER",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = NeonGreen,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                            }
                            
                            val sdfLocal = SimpleDateFormat("HH:mm", Locale.getDefault())
                            Text(
                                text = "GEN: ${sdfLocal.format(Date(generatedTime))}",
                                style = MaterialTheme.typography.labelSmall.copy(color = MutedText, fontSize = 9.sp)
                            )
                        }

                        Divider(color = BorderGray, modifier = Modifier.padding(vertical = 14.dp))

                        // Custom formatted basic Markdown parser
                        MarkdownRenderer(text = weeklyInsight)
                    }
                }
            }
        }
    }
}

// Exceptionally clean simple Custom Markdown Engine to avoid messy pure raw tags
@Composable
fun MarkdownRenderer(text: String) {
    val lines = text.split("\n")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        lines.forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("###") -> {
                    val rawText = trimmed.replace("###", "").trim()
                    Text(
                        text = rawText.uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = NeonTeal,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                trimmed.startsWith("##") -> {
                    val rawText = trimmed.replace("##", "").trim()
                    Text(
                        text = rawText.uppercase(),
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = NeonOrange,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
                trimmed.startsWith("-") -> {
                    val rawText = trimmed.replace("-", "").trim()
                    // Detect custom bold highlights in markdown e.g. **Text**: Body
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(text = " •  ", color = NeonTeal, fontWeight = FontWeight.Bold)
                        Box(modifier = Modifier.weight(1f)) {
                            MarkdownSpanText(rawText)
                        }
                    }
                }
                trimmed.startsWith(">") -> {
                    val rawText = trimmed.replace(">", "").trim()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(CyberMediumGray)
                            .drawBehind {
                                drawRect(
                                    color = NeonTeal,
                                    size = Size(4.dp.toPx(), size.height)
                                )
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = rawText,
                            style = MaterialTheme.typography.bodyMedium.copy(color = OffWhite, fontFamily = FontFamily.Monospace)
                        )
                    }
                }
                trimmed.isNotEmpty() -> {
                    MarkdownSpanText(trimmed)
                }
            }
        }
    }
}

@Composable
fun MarkdownSpanText(text: String) {
    // Detect custom bold highlights: **HighlightText** Remaining text
    if (text.contains("**")) {
        val parts = text.split("**")
        // typically: parts[0] is text before **, parts[1] is bold text, parts[2] is after etc.
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = text.replace("**", ""), // Clean fallback or formatted nested text
                style = MaterialTheme.typography.bodyMedium.copy(color = OffWhite, lineHeight = 20.sp)
            )
        }
    } else {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(color = OffWhite, lineHeight = 20.sp)
        )
    }
}


// ==========================================
// Overlay Action Input Dialogs
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddHrDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int, String) -> Unit
) {
    var bpmString by remember { mutableStateOf("") }
    var activityType by remember { mutableStateOf("Resting") }
    var errorMsg by remember { mutableStateOf("") }

    val activities = listOf("Resting", "Walking", "Cardio", "Sleep")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = CyberDarkGray),
            border = BorderStroke(1.dp, BorderGray),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "LOG CARDIO TELEMETRY",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        color = NeonTeal,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                TextField(
                    value = bpmString,
                    onValueChange = { 
                        bpmString = it
                        errorMsg = ""
                    },
                    label = { Text("BPM VALUE", color = MutedText) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = OffWhite,
                        unfocusedTextColor = OffWhite,
                        focusedContainerColor = CyberMediumGray,
                        unfocusedContainerColor = CyberMediumGray,
                        cursorColor = NeonTeal,
                        focusedIndicatorColor = NeonTeal
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("bpm_input_field"),
                    singleLine = true
                )

                if (errorMsg.isNotEmpty()) {
                    Text(text = errorMsg, color = NeonPink, fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "SELECT CURRENT SENSOR CYCLE",
                    style = MaterialTheme.typography.labelSmall.copy(color = MutedText, letterSpacing = 0.5.sp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    activities.forEach { act ->
                        val isSelected = activityType == act
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) NeonTeal else CyberMediumGray)
                                .clickable { activityType = act }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = act.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isSelected) CyberBlack else OffWhite,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCEL", color = MutedText, style = MaterialTheme.typography.labelLarge)
                    }
                    Button(
                        onClick = {
                            val bpm = bpmString.toIntOrNull()
                            if (bpm == null || bpm !in 30..220) {
                                errorMsg = "BPM must register between 30 and 220"
                            } else {
                                onConfirm(bpm, activityType)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonTeal),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("confirm_hr_btn")
                    ) {
                        Text("SAVE ENTRY", color = CyberBlack, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSleepDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Int, Int, Int, Int) -> Unit
) {
    var dayLabel by remember { mutableStateOf("Mon") }
    var deepStr by remember { mutableStateOf("") }
    var lightStr by remember { mutableStateOf("") }
    var remStr by remember { mutableStateOf("") }
    var awakeStr by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }

    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = CyberDarkGray),
            border = BorderStroke(1.dp, BorderGray),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "LOG SLEEP ENVELOPE",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        color = NeonGreen,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    days.forEach { day ->
                        val isSelected = dayLabel == day
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) NeonGreen else CyberMediumGray)
                                .clickable { dayLabel = day }
                                .padding(horizontal = 6.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = day.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isSelected) CyberBlack else OffWhite,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextField(
                    value = deepStr,
                    onValueChange = { deepStr = it; errorMsg = "" },
                    label = { Text("DEEP SLEEP MINUTES", color = MutedText, fontSize = 11.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = OffWhite, unfocusedTextColor = OffWhite,
                        focusedContainerColor = CyberMediumGray, unfocusedContainerColor = CyberMediumGray,
                        cursorColor = NeonGreen, focusedIndicatorColor = NeonGreen
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("deep_sleep_input"),
                    singleLine = true
                )

                TextField(
                    value = lightStr,
                    onValueChange = { lightStr = it; errorMsg = "" },
                    label = { Text("LIGHT SLEEP MINUTES", color = MutedText, fontSize = 11.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = OffWhite, unfocusedTextColor = OffWhite,
                        focusedContainerColor = CyberMediumGray, unfocusedContainerColor = CyberMediumGray,
                        cursorColor = NeonGreen, focusedIndicatorColor = NeonGreen
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("light_sleep_input"),
                    singleLine = true
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = remStr,
                        onValueChange = { remStr = it; errorMsg = "" },
                        label = { Text("REM MINS", color = MutedText, fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = OffWhite, unfocusedTextColor = OffWhite,
                            focusedContainerColor = CyberMediumGray, unfocusedContainerColor = CyberMediumGray,
                            cursorColor = NeonGreen, focusedIndicatorColor = NeonGreen
                        ),
                        modifier = Modifier.weight(1f).testTag("rem_sleep_input"),
                        singleLine = true
                    )

                    TextField(
                        value = awakeStr,
                        onValueChange = { awakeStr = it; errorMsg = "" },
                        label = { Text("AWAKE MINS", color = MutedText, fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = OffWhite, unfocusedTextColor = OffWhite,
                            focusedContainerColor = CyberMediumGray, unfocusedContainerColor = CyberMediumGray,
                            cursorColor = NeonGreen, focusedIndicatorColor = NeonGreen
                        ),
                        modifier = Modifier.weight(1f).testTag("awake_sleep_input"),
                        singleLine = true
                    )
                }

                if (errorMsg.isNotEmpty()) {
                    Text(text = errorMsg, color = NeonPink, fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCEL", color = MutedText, style = MaterialTheme.typography.labelLarge)
                    }
                    Button(
                        onClick = {
                            val deep = deepStr.toIntOrNull() ?: 0
                            val light = lightStr.toIntOrNull() ?: 0
                            val rem = remStr.toIntOrNull() ?: 0
                            val awake = awakeStr.toIntOrNull() ?: 0
                            
                            if (deep == 0 && light == 0 && rem == 0 && awake == 0) {
                                errorMsg = "Please input at least one parameter minute value"
                            } else {
                                onConfirm(dayLabel, deep, light, rem, awake)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("confirm_sleep_btn")
                    ) {
                        Text("SAVE ENVELOPE", color = CyberBlack, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
fun FitbitAuthDialog(
    isSyncing: Boolean,
    onDismiss: () -> Unit,
    onAuthenticate: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = CyberDarkGray),
            border = BorderStroke(1.dp, BorderGray),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.DirectionsRun, contentDescription = null, tint = NeonTeal, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "FITBIT CLOUD DIRECTIVE",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        color = OffWhite,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Are you sure you want to mirror your Fitbit account data? Health Sync Tracker will query heart rate intervals isometrically over secure Web OAuth tokens mapped cleanly into internal registers.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MutedText,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCEL", color = MutedText)
                    }
                    Button(
                        onClick = onAuthenticate,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonTeal),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("fitbit_authorize_button")
                    ) {
                        Text("APPROVE & LINK", color = CyberBlack, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

@Composable
fun AppleWatchAuthDialog(
    isSyncing: Boolean,
    onDismiss: () -> Unit,
    onAuthenticate: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = CyberDarkGray),
            border = BorderStroke(1.dp, BorderGray),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Watch, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "APPLE WATCH PROTOCOL",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        color = OffWhite,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Syncing with Apple Watch mirrors HealthKit aggregates via secure iCloud CloudKit proxy bindings. It downloads sleep parameters, active peak values, and resting indexes seamlessly.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MutedText,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCEL", color = MutedText)
                    }
                    Button(
                        onClick = onAuthenticate,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("apple_authorize_button")
                    ) {
                        Text("APPROVE & LINK", color = CyberBlack, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

@Composable
fun HeartRateSpikeAlertSection(
    spikes: List<HeartRateSpike>,
    onDismiss: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        spikes.forEach { spike ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .shadow(4.dp, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = CyberMediumGray),
                border = BorderStroke(1.dp, NeonPink),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "alert_pulse")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 0.85f,
                        targetValue = 1.15f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "alert_scale"
                    )

                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .drawBehind {
                                drawCircle(
                                    color = NeonPink.copy(alpha = 0.15f * pulseScale),
                                    radius = (size.minDimension / 1.5f) * pulseScale
                                )
                            }
                            .clip(CircleShape)
                            .background(NeonPink.copy(alpha = 0.2f))
                            .border(1.dp, NeonPink, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Resting HR warning anomaly",
                            tint = NeonPink,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ANOMALOUS RESTING HEART RATE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = NeonPink,
                                letterSpacing = 1.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Unusually high resting rate of ${spike.bpm} BPM registered.",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = OffWhite
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${spike.dateLabel} • Sync: ${spike.deviceSource.uppercase()}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MutedText,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { onDismiss(spike.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCardBg),
                        border = BorderStroke(1.dp, BorderGray),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("dismiss_spike_${spike.id}")
                    ) {
                        Text(
                            text = "DISMISS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = OffWhite,
                                fontSize = 9.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
