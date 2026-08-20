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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
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
import com.example.waybag.data.supabase
import io.github.jan.supabase.auth.auth
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
import com.example.waybag.ui.screens.AITrackerScreen

enum class DashboardTab {
    Home, QRScan, AITracker
}

@Composable
fun DashboardScreen(
    onExitClick: () -> Unit,
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
    val lifecycleOwner = LocalLifecycleOwner.current

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
                    onExitClick = onExitClick,
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
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
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
                            ClimateImpactPanel(
                                weeklyData = weeklyData,
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
    onExitClick: () -> Unit,
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
            // Refined Exit Button
            IconButton(
                onClick = onExitClick,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.05f))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Exit",
                    tint = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
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
                    .background(Color.White.copy(alpha = 0.2f))
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
    ThinGlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = profileName,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Total Bag Usage",
                style = MaterialTheme.typography.bodyMedium,
                color = Premium_Gray.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = totalScans.toString(),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "keep it up!",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }
    }
}

@Composable
fun ClimateImpactPanel(
    weeklyData: List<ChartEntry>,
    monthlyData: List<ChartEntry>
) {
    ThinGlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "Steps of Carbon footprint taken",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Vertical Bar Chart
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp), // Increased height for numbers
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                
                // Ensure we always iterate 7 times to keep labels stable
                repeat(7) { index ->
                    val entry = weeklyData.getOrNull(index) ?: ChartEntry(0f, 0)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (entry.quantity > 0) {
                            Text(
                                text = entry.quantity.toString(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = WayBag_Lime
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .fillMaxHeight(if (entry.value > 0.05f) entry.value else 0.05f)
                                .border(1.dp, WayBag_Lime, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(days[index], fontSize = 10.sp, color = Premium_Gray)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
            
            Text(
                text = "Total Contributions on WayBag",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Horizontal Bar Chart
            val months = listOf("Jan", "Feb", "Mar")
            
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) { index ->
                    val entry = monthlyData.getOrNull(index) ?: ChartEntry(0f, 0)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(months[index], modifier = Modifier.width(40.dp), fontSize = 12.sp)
                        Box(
                            modifier = Modifier
                                .weight(1f) // Use weight to fill space
                                .height(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(if (entry.value > 0.02f) entry.value else 0.02f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(WayBag_Lime.copy(alpha = 0.3f))
                                    .border(0.5.dp, WayBag_Lime, RoundedCornerShape(2.dp))
                            )
                        }
                        if (entry.quantity > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = entry.quantity.toString(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = WayBag_Lime
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
                .background(Color.White.copy(alpha = 0.15f)) // Ultra-thin glass
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.2f),
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
        // The Pill Background
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(bgColor)
                .padding(horizontal = 14.dp, vertical = 8.dp)
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
