package com.example.waybag.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.waybag.data.model.BagScan
import com.example.waybag.ui.components.ThinGlassPanel
import com.example.waybag.ui.theme.WayBag_Lime
import com.example.waybag.ui.viewmodel.HistoryViewModel
import com.example.waybag.utils.DateUtils
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WayBag_Lime.copy(alpha = 0.04f))
            .statusBarsPadding()
            .padding(horizontal = if (isLandscape) 80.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Scan History",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = WayBag_Lime)
            }
        } else if (state.scans.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No scan history found.", color = Color.Gray)
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp)
                        .align(Alignment.TopCenter),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.scans) { scan ->
                        HistoryItem(
                            scan = scan,
                            onClick = {
                                if (scan.status.lowercase() != "pending") {
                                    viewModel.selectScan(scan)
                                }
                            }
                        )
                    }
                }
            }
        }

        // Scan Detail Modal/Overlay
        if (state.selectedScanDetail != null) {
            ScanDetailView(
                scan = state.selectedScanDetail!!,
                onDismiss = { viewModel.selectScan(null) }
            )
        }
    }
}

@Composable
fun ScanDetailView(scan: BagScan, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = WayBag_Lime)
            ) {
                Text("Close", color = Color.Black)
            }
        },
        title = { 
            Text(
                "Scan Details", 
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold 
            ) 
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DetailRow(
                    label = "Status", 
                    value = scan.status.uppercase(), 
                    valueColor = if (scan.status.lowercase() == "completed") Color(0xFF4B8B00) else Color.Red
                )
                
                if (scan.status.lowercase() == "failed" && scan.failureReason != null) {
                    DetailRow("Failure Reason", scan.failureReason, Color.Red)
                }

                val scanTime = remember(scan.scanQR) {
                    DateUtils.parseIsoTimestamp(scan.scanQR)?.let {
                        SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault()).format(it)
                    } ?: scan.scanQR
                }
                DetailRow("Scan QR Time", scanTime)

                val receiptTime = remember(scan.receiptTimestamp) {
                    if (scan.receiptTimestamp == null || scan.receiptTimestamp == "MISSING") {
                        "MISSING"
                    } else {
                        // Attempt to format if it's ISO
                        try {
                            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                            val date = sdf.parse(scan.receiptTimestamp)
                            SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault()).format(date!!)
                        } catch (e: Exception) {
                            scan.receiptTimestamp
                        }
                    }
                }
                DetailRow("Receipt Time", receiptTime)
                
                DetailRow("Reg No", scan.regNo ?: "N/A")
                DetailRow("Store", scan.storeName ?: "N/A")

                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Receipt Photo",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray
                )
                
                if (scan.receiptImageUrl != null) {
                    AsyncImage(
                        model = scan.receiptImageUrl,
                        contentDescription = "Receipt Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(Color.LightGray.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No photo available", color = Color.Gray)
                    }
                }
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun DetailRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(
            text = value, 
            style = MaterialTheme.typography.bodyLarge, 
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

@Composable
fun HistoryItem(scan: BagScan, onClick: () -> Unit) {
    val displayTime = remember(scan.scanQR) {
        val date = DateUtils.parseIsoTimestamp(scan.scanQR)
        if (date != null) {
            SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(date)
        } else {
            scan.scanQR.take(16).replace('T', ' ')
        }
    }

    val isExpired = remember(scan.scanQR, scan.status) {
        if (scan.status.lowercase() == "pending") {
            val scanDate = DateUtils.parseIsoTimestamp(scan.scanQR)
            scanDate != null && (System.currentTimeMillis() - scanDate.time) > 20 * 60 * 1000
        } else false
    }

    val displayStatus = if (isExpired) "FAILED" else scan.status.uppercase()
    val statusColor = when {
        isExpired || scan.status.lowercase() == "failed" -> Color.Red
        scan.status.lowercase() == "completed" -> Color(0xFF4B8B00)
        else -> Color(0xFFFFB300) // Pending
    }

    ThinGlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = scan.status.lowercase() != "pending") { onClick() }
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Bag #${scan.bagUniqueId}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = displayTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                if (isExpired) {
                    Text(
                        text = "EXPIRED (Over 20 mins)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Red.copy(alpha = 0.7f)
                    )
                }
            }
            
            Surface(
                color = statusColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
            ) {
                Text(
                    text = displayStatus,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
