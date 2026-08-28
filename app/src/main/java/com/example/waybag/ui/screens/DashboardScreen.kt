package com.example.waybag.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.example.waybag.ui.components.GlassBackground
import com.example.waybag.ui.components.ThinGlassPanel
import com.example.waybag.ui.components.WayBagLogo
import com.example.waybag.ui.theme.Premium_Gray
import com.example.waybag.ui.theme.WayBag_Lime
import com.example.waybag.ui.viewmodel.DashboardViewModel
import com.example.waybag.ui.viewmodel.ChartEntry
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.waybag.data.model.UserProfile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import com.example.waybag.data.supabase
import io.github.jan.supabase.auth.auth
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class DashboardTab {
    Home, QRScan, AITracker
}

@Composable
fun DashboardScreen(
    onProfileClick: () -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val scrollState = rememberScrollState()
    val userProfile by viewModel.userProfile.collectAsState()
    val profileName = userProfile?.name ?: "User"
    
    val totalScans by viewModel.totalScans.collectAsState()
    val weeklyData by viewModel.weeklyData.collectAsState()
    val monthlyData by viewModel.monthlyData.collectAsState()
    
    var currentTab by remember { mutableStateOf(DashboardTab.Home) }
    var showHistory by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    if (showHistory) {
        ScanHistoryScreen(onBack = { showHistory = false })
        return
    }

    // Refresh when tab changes OR when returning to this screen from Profile
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(currentTab) {
        if (currentTab == DashboardTab.Home) {
            viewModel.refreshData()
        }
    }
    
    GlassBackground {
        Scaffold(
            topBar = { 
                DashboardHeader(
                    userProfile = userProfile,
                    onHistoryClick = { showHistory = true },
                    onProfileClick = onProfileClick
                ) 
            },
            bottomBar = { 
                DashboardBottomNav(
                    selectedTab = currentTab,
                    onTabSelected = { currentTab = it }
                ) 
            },
            containerColor = Color.Transparent
        ) { innerPadding: PaddingValues ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when (currentTab) {
                    DashboardTab.Home -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp)
                                .verticalScroll(scrollState),
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            Spacer(modifier = Modifier.height(16.dp))
                            ProfileStatsPanel(
                                profileName = profileName,
                                totalScans = totalScans
                            )
                            WeeklyImpactPanel(
                                weeklyData = weeklyData
                            )
                            MonthlyContributionPanel(
                                monthlyData = monthlyData
                            )
                            Spacer(modifier = Modifier.height(100.dp))
                        }
                    }
                    DashboardTab.QRScan -> {
                        QRScanScreen()
                    }
                    DashboardTab.AITracker -> {
                        AITrackerScreen()
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardHeader(
    userProfile: UserProfile?,
    onHistoryClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(90.dp)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // History Button - Replaces Exit App
            TextButton(
                onClick = onHistoryClick,
                modifier = Modifier
                    .height(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(WayBag_Lime.copy(alpha = 0.3f), RoundedCornerShape(25.dp)),
                border = androidx.compose.foundation.BorderStroke(1.dp, WayBag_Lime.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "History",
                    tint = Color(0xFF4B8B00),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "History",
                    color = Color(0xFF4B8B00),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }

            // Centered Logo and Wordmark
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                WayBagLogo(modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "WayBag",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp,
                        color = Premium_Gray
                    )
                )
            }
            
            // Profile Avatar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 1f))
                    .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                    .clickable { onProfileClick() },
                contentAlignment = Alignment.Center
            ) {
                if (userProfile?.avatar_url != null) {
                    AsyncImage(
                        model = userProfile.avatar_url,
                        contentDescription = "Avatar",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Text("👤", fontSize = 20.sp)
                }

                // Small indicator
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(WayBag_Lime)
                        .align(Alignment.BottomEnd)
                        .border(1.dp, Color.White, CircleShape)
                )
            }
        }
    }
}

@Composable
fun ProfileStatsPanel(profileName: String, totalScans: Int) {
    val totalCarbonSaved = totalScans * 1.58f
    ThinGlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = profileName,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(20.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Usage Stat
                Column {
                    Text(
                        text = "Total Bag Usage",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = totalScans.toString(),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                // Vertical Divider
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .width(1.dp)
                        .background(Premium_Gray.copy(alpha = 0.1f))
                )

                // Carbon Stat
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Total Carbon Saved",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.2f", totalCarbonSaved)}kg",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4B8B00)
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Keep it up! Your usage helps the planet.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = Premium_Gray.copy(alpha = 0.7f)
                )
            )
        }
    }
}

@Composable
fun WeeklyImpactPanel(
    weeklyData: List<ChartEntry>
) {
    var dayOffset by remember { mutableIntStateOf(0) }

    val calendar = remember(dayOffset) {
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
        }
    }
    val formattedDate = remember(calendar) {
        SimpleDateFormat("dd MMM", Locale.ENGLISH).format(calendar.time)
    }

    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
    val selectedDayIndex = when (dayOfWeek) {
        Calendar.MONDAY -> 0
        Calendar.TUESDAY -> 1
        Calendar.WEDNESDAY -> 2
        Calendar.THURSDAY -> 3
        Calendar.FRIDAY -> 4
        Calendar.SATURDAY -> 5
        Calendar.SUNDAY -> 6
        else -> 6
    }

    val currentDayEntry = weeklyData.getOrNull(selectedDayIndex) ?: ChartEntry(0f, 0)
    val todayUsage = currentDayEntry.quantity
    val carbonFootprintReduced = todayUsage * 1.58f
    val totalWeeklyUsage = remember(weeklyData) { weeklyData.sumOf { it.quantity } }
    val totalWeeklyCarbonReduced = totalWeeklyUsage * 1.58f

    ThinGlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "Weekly Carbon Footprint Reduced",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${String.format(Locale.US, "%.2f", totalWeeklyCarbonReduced)}kg",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF4B8B00)
                ),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

                repeat(7) { index ->
                    val entry = weeklyData.getOrNull(index) ?: ChartEntry(0f, 0)
                    val isSelected = index == selectedDayIndex

                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (entry.quantity > 0) {
                                    Text(
                                        text = entry.quantity.toString(),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color(0xFF4B8B00) else WayBag_Lime
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                Box(
                                    modifier = Modifier
                                        .width(20.dp)
                                        .fillMaxHeight(if (entry.value > 0.05f) entry.value else 0.05f)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(
                                            color = WayBag_Lime
                                        )
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) Color(0xFF4B8B00) else Color(0xFF4B8B00).copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(2.dp)
                                        )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = days[index],
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                            color = if (isSelected) Color(0xFF4B8B00) else Premium_Gray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { dayOffset -= 1 },
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.White.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous Day",
                        tint = Premium_Gray
                    )
                }

                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Premium_Gray
                    )
                )

                IconButton(
                    onClick = { dayOffset += 1 },
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.White.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next Day",
                        tint = Premium_Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Today's WayBag Usage: $todayUsage times",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Premium_Gray
                )
                Text(
                    text = "Carbon footprint reduced: ${String.format(Locale.US, "%.2f", carbonFootprintReduced)}kg",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Premium_Gray
                )
            }
        }
    }
}

@Composable
fun MonthlyContributionPanel(monthlyData: List<ChartEntry>) {
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val totalMonthlyUsage = monthlyData.sumOf { it.quantity }
    val totalMonthlyCarbonReduced = totalMonthlyUsage * 1.58f

    ThinGlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "Total Contributions on WayBag",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${String.format(Locale.US, "%.2f", totalMonthlyCarbonReduced)}kg Carbon Saved!",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF4B8B00)
                ),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Horizontal Bar Chart
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(monthlyData.size) { index ->
                    val entry = monthlyData[index]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = months[index % 12],
                            modifier = Modifier.width(45.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Premium_Gray
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(16.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.1f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(if (entry.value > 0.02f) entry.value else 0.02f)
                                    .fillMaxHeight()
                                    .background(WayBag_Lime)
                                    .border(1.dp, Color(0xFF4B8B00).copy(alpha = 0.3f))
                            )
                        }
                        if (entry.quantity > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = entry.quantity.toString(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF4B8B00)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardBottomNav(
    selectedTab: DashboardTab,
    onTabSelected: (DashboardTab) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .navigationBarsPadding()
    ) {
        // Pure Floating Glass Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(Color.White.copy(alpha = 0.75f)) // Ultra-thin glass
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(30.dp)
                )
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavItem(
                    label = "Home",
                    icon = Icons.Default.Home,
                    active = selectedTab == DashboardTab.Home,
                    onClick = { onTabSelected(DashboardTab.Home) }
                )
                BottomNavItem(
                    label = "QR Scan",
                    icon = Icons.Default.QrCodeScanner,
                    active = selectedTab == DashboardTab.QRScan,
                    onClick = { onTabSelected(DashboardTab.QRScan) }
                )
                BottomNavItem(
                    label = "AI Tracker",
                    icon = Icons.Default.TrackChanges,
                    active = selectedTab == DashboardTab.AITracker,
                    onClick = { onTabSelected(DashboardTab.AITracker) }
                )
            }
        }
    }
}

@Composable
fun RowScope.BottomNavItem(
    label: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit
) {
    val contentColor = if (active) Color(0xFF4B8B00) else Premium_Gray.copy(alpha = 0.5f)
    val bgColor = if (active) WayBag_Lime.copy(alpha = 0.3f) else Color.Transparent

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, 
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Uniform Pill Background
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
                if (active) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        color = contentColor,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

fun Modifier.drawBehindBorder(bottom: Boolean = false): Modifier = this.drawBehind {
    if (bottom) {
        drawLine(
            color = WayBag_Lime,
            start = Offset(0f, this.size.height),
            end = Offset(this.size.width, this.size.height),
            strokeWidth = 2.dp.toPx()
        )
    }
}
