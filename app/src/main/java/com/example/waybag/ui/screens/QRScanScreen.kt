package com.example.waybag.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.waybag.data.model.BagScan
import com.example.waybag.data.model.ValidBag
import com.example.waybag.data.supabase
import com.example.waybag.ui.components.ThinGlassPanel
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import com.example.waybag.ui.theme.WayBag_Lime
import com.example.waybag.utils.DateUtils
import coil.compose.AsyncImage

@Composable
fun QRScanScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )
    
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }
    
    var scannedId by remember { mutableStateOf<String?>(null) }
    var scanTimeTqr by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    val processingLock = remember { AtomicBoolean(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showHistory) {
            ScanHistoryScreen(onBack = { showHistory = false })
        } else if (hasCameraPermission) {
            if (scannedId == null && validationError == null) {
                CameraPreview(
                    onBarcodeScanned = { barcodeValue ->
                        if (processingLock.compareAndSet(false, true)) {
                            isProcessing = true
                            scope.launch {
                                try {
                                    val timestamp = recordScan(barcodeValue)
                                    if (timestamp != null) {
                                        scannedId = barcodeValue.trim()
                                        scanTimeTqr = timestamp
                                    } else {
                                        validationError = "Invalid QR Code: This bag is not registered in our system."
                                        processingLock.set(false)
                                    }
                                } catch (e: Exception) {
                                    Log.e("QRScan", "Failed to record scan", e)
                                    validationError = "Connection Error: ${e.message}"
                                    processingLock.set(false)
                                } finally {
                                    isProcessing = false
                                }
                            }
                        }
                    }
                )
                
                QRScannerOverlay()

                IconButton(
                    onClick = { showHistory = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 48.dp, end = 24.dp)
                        .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(Icons.Default.History, contentDescription = "History", tint = Color.White)
                }
                
                if (isProcessing) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = WayBag_Lime)
                    }
                }
            } else if (validationError != null) {
                ErrorState(validationError!!) {
                    validationError = null
                }
            } else {
                SuccessState(
                    uniqueId = scannedId!!,
                    timestamp = scanTimeTqr ?: ""
                ) {
                    processingLock.set(false)
                    scannedId = null
                    scanTimeTqr = null
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Camera permission is required")
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant Permission")
                }
            }
        }
    }
}

@Composable
fun CameraPreview(onBarcodeScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    AndroidView(
        factory = { context ->
            val previewView = PreviewView(context)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                
                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    processImageProxy(imageProxy, onBarcodeScanned)
                }
                
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e("QRScan", "Use case binding failed", e)
                }
            }, ContextCompat.getMainExecutor(context))
            
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun processImageProxy(
    imageProxy: ImageProxy,
    onBarcodeScanned: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val scanner = BarcodeScanning.getClient()
        
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    barcode.rawValue?.let { value ->
                        onBarcodeScanned(value)
                    }
                }
            }
            .addOnFailureListener {
                Log.e("QRScan", "Barcode scanning failed", it)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}

@Composable
fun QRScannerOverlay() {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .size(250.dp)
                .align(Alignment.Center)
                .border(2.dp, WayBag_Lime, RoundedCornerShape(12.dp))
        )
        
        Text(
            text = "Scan the QR code on your Way Bag",
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun SuccessState(uniqueId: String, timestamp: String, onReset: () -> Unit) {
    val displayTime = remember(timestamp) {
        val date = DateUtils.parseIsoTimestamp(timestamp)
        if (date != null) {
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date)
        } else {
            timestamp.take(16).replace('T', ' ')
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White.copy(alpha = 0.9f))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = WayBag_Lime,
            modifier = Modifier.size(100.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Success!",
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        ThinGlassPanel(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Plastic Bag #$uniqueId recorded!",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Scan Time (T_QR): $displayTime",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Tip: You have 20 minutes to scan your receipt for validation.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.DarkGray,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = onReset,
            colors = ButtonDefaults.buttonColors(containerColor = WayBag_Lime),
            shape = RoundedCornerShape(50.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Scan Another", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ErrorState(message: String, onReset: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = Color.Red,
            modifier = Modifier.size(100.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Validation Failed",
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
            color = Color.Red
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = message,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = onReset,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
            shape = RoundedCornerShape(50.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Try Again", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ScanHistoryScreen(onBack: () -> Unit) {
    var scans by remember { mutableStateOf<List<BagScan>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedScanDetail by remember { mutableStateOf<BagScan?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val user = supabase.auth.currentUserOrNull()
                if (user != null) {
                    // 1. Fetch current pending scans to check for expiration
                    val response = supabase.from("bag_scans")
                        .select {
                            filter { eq("userId", user.id) }
                            order("scanQR", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                        }
                    val allScans = response.decodeList<BagScan>()
                    
                    // 2. Identify expired scans (Pending > 20 mins)
                    val now = Date()
                    val expiredIds = allScans.filter { scan ->
                        if (scan.status.lowercase() == "pending") {
                            val scanDate = DateUtils.parseIsoTimestamp(scan.scanQR)
                            val isExpired = scanDate != null && (now.time - scanDate.time) > 20 * 60 * 1000
                            isExpired
                        } else false
                    }.mapNotNull { it.id }

                    // 3. Batch update expired scans in database
                    if (expiredIds.isNotEmpty()) {
                        try {
                            supabase.from("bag_scans").update({
                                set("status", "failed")
                                set("failureReason", "EXPIRED (Over 20 mins)")
                            }) {
                                filter {
                                    isIn("id", expiredIds)
                                }
                            }
                            // Re-fetch to get updated statuses for the UI
                            val refreshedResponse = supabase.from("bag_scans")
                                .select {
                                    filter { eq("userId", user.id) }
                                    order("scanQR", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                                }
                            scans = refreshedResponse.decodeList<BagScan>()
                        } catch (e: Exception) {
                            Log.e("ExpirationCheck", "Update failed", e)
                            scans = allScans
                        }
                    } else {
                        scans = allScans
                    }
                }
            } catch (e: Exception) {
                Log.e("ScanHistory", "Failed to fetch or update history", e)
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
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

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = WayBag_Lime)
            }
        } else if (scans.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No scan history found.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(scans) { scan ->
                    HistoryItem(
                        scan = scan,
                        onClick = {
                            if (scan.status.lowercase() != "pending") {
                                selectedScanDetail = scan
                            }
                        }
                    )
                }
            }
        }

        // Scan Detail Modal/Overlay
        if (selectedScanDetail != null) {
            ScanDetailView(
                scan = selectedScanDetail!!,
                onDismiss = { selectedScanDetail = null }
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
                    valueColor = if (scan.status.lowercase() == "completed") WayBag_Lime else Color.Red
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
        scan.status.lowercase() == "completed" -> WayBag_Lime
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

private suspend fun recordScan(barcodeValue: String): String? {
    val uniqueId = barcodeValue.trim()
    
    try {
        val response = supabase.from("valid_bags")
            .select {
                filter { eq("unique_id", uniqueId) }
            }
        
        val validBag = response.decodeSingleOrNull<ValidBag>()
        val user = supabase.auth.currentUserOrNull()
        
        // Use local timezone format (e.g. +0800) for easier reading in DB
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
        val timestamp = sdf.format(Date())
        Log.d("QRScan", "Recording with local timestamp: $timestamp")

        if (validBag == null) {
            if (user != null) {
                val failedScan = BagScan(
                    userId = user.id,
                    bagUniqueId = uniqueId,
                    scanQR = timestamp,
                    status = "failed",
                    failureReason = "Invalid QR Code: Bag not registered"
                )
                supabase.from("bag_scans").insert(failedScan)
            }
            return null
        }

        if (user != null) {
            val scan = BagScan(
                userId = user.id,
                bagUniqueId = uniqueId,
                scanQR = timestamp,
                status = "pending"
            )
            supabase.from("bag_scans").insert(scan)
            return timestamp
        }
    } catch (e: Exception) {
        Log.e("QRScan", "Error during validation: ${e.message}", e)
        throw e
    }
    return null
}
