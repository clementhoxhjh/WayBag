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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

enum class DashboardTab {
    Home, QRScan, AITracker
}

@Composable
fun DashboardScreen(
    onExitClick: () -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val scrollState = rememberScrollState()
    val user = remember { supabase.auth.currentUserOrNull() }
    val profileName = remember(user) {
        user?.email?.substringBefore('@') ?: "Profile Name"
    }
    
    val totalScans by viewModel.totalScans.collectAsState()
    val weeklyData by viewModel.weeklyData.collectAsState()
    val monthlyData by viewModel.monthlyData.collectAsState()
    
    var currentTab by remember { mutableStateOf(DashboardTab.Home) }

    LaunchedEffect(currentTab) {
        if (currentTab == DashboardTab.Home) {
            viewModel.refreshData()
        }
    }
    
    GlassBackground {
        Scaffold(
            topBar = { DashboardHeader(onExitClick = onExitClick) },
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
                        // Placeholder for Module 3
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("AI Tracker Module coming soon")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardHeader(onExitClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(80.dp)
            .background(Color.White.copy(alpha = 0.5f))
            .drawBehindBorder(bottom = true),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Exit Button
            TextButton(onClick = onExitClick) {
                Text(
                    text = "Exit",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = Color.Red.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            
            // Centered Logo and Wordmark
            Row(verticalAlignment = Alignment.CenterVertically) {
                WayBagLogo(modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "WayBag",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                )
            }
            
            // Sophisticated Avatar Icon
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.LightGray.copy(alpha = 0.3f))
                        .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("👤", fontSize = 24.sp)
                }
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(WayBag_Lime),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
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
            .padding(16.dp)
            .navigationBarsPadding()
    ) {
        ThinGlassPanel(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
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
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) WayBag_Lime else Premium_Gray,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (active) WayBag_Lime else Premium_Gray,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
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
