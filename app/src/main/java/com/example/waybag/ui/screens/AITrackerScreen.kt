package com.example.waybag.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Receipt
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.waybag.data.model.BagScan
import com.example.waybag.ui.components.GlassBackground
import com.example.waybag.ui.components.ThinGlassPanel
import com.example.waybag.ui.theme.WayBag_Lime
import com.example.waybag.ui.theme.Premium_Gray
import com.example.waybag.ui.viewmodel.AITrackerViewModel
import com.example.waybag.ui.viewmodel.AITrackerState
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@Composable
fun AITrackerScreen(
    viewModel: AITrackerViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                if (originalBitmap != null) {
                    val resizedBitmap = resizeBitmap(originalBitmap, 800)
                    viewModel.analyzeReceipt(resizedBitmap)
                }
            } catch (e: Exception) {
                Log.e("AITracker", "Failed to load image from gallery", e)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.selectedScan == null -> {
                PendingScansList(
                    scans = state.pendingScans,
                    onScanSelected = { viewModel.selectScan(it) },
                    onRefresh = { viewModel.fetchPendingScans() }
                )
            }
            state.isAnalyzing -> {
                AnalyzingState()
            }
            state.analysisResult != null || state.error != null -> {
                ResultState(
                    state = state,
                    onDismiss = { viewModel.selectScan(null) }
                )
            }
            else -> {
                ReceiptCameraView(
                    selectedBagId = state.selectedScan?.bagUniqueId ?: "",
                    onImageCaptured = { viewModel.analyzeReceipt(it) },
                    onGalleryClick = { galleryLauncher.launch("image/*") },
                    onCancel = { viewModel.selectScan(null) }
                )
            }
        }
    }
}

@Composable
fun PendingScansList(
    scans: List<BagScan>,
    onScanSelected: (BagScan) -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Select a Scan to Validate",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = Premium_Gray
        )
        Text(
            text = "You need to scan a matching receipt within 20 mins of the QR scan.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray.copy(alpha = 0.8f)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        if (scans.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(), 
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Illustration Placeholder / Branded Icon
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(WayBag_Lime.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Receipt,
                            contentDescription = null,
                            tint = WayBag_Lime,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        text = "No pending scans found.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Scan a QR code to display pending items.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Branded Refresh Button
                    Button(
                        onClick = onRefresh,
                        modifier = Modifier
                            .height(54.dp)
                            .padding(horizontal = 24.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = WayBag_Lime),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Text(
                            "Refresh", 
                            color = Color.Black, 
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(scans) { scan ->
                    ThinGlassPanel(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onScanSelected(scan) }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Receipt, contentDescription = null, tint = WayBag_Lime)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(text = "Bag #${scan.bagUniqueId}", fontWeight = FontWeight.Bold)
                                Text(text = "Scanned at: ${scan.scanQR.take(16).replace('T', ' ')}", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReceiptCameraView(
    selectedBagId: String,
    onImageCaptured: (Bitmap) -> Unit,
    onGalleryClick: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val imageCapture = remember { ImageCapture.Builder().build() }
    
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture
                        )
                    } catch (e: Exception) {
                        Log.e("AITracker", "Camera binding failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // UI Overlay
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                }
                
                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "Validating #$selectedBagId",
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Spacer(modifier = Modifier.width(48.dp))
            }

            // Guide Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .border(2.dp, WayBag_Lime.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            )

            // Bottom Controls
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery Button
                IconButton(
                    onClick = onGalleryClick,
                    modifier = Modifier.size(56.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery", tint = Color.White)
                }

                // Capture Button
                Button(
                    onClick = {
                        val mainExecutor = ContextCompat.getMainExecutor(context)
                        imageCapture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                            val originalBitmap = imageProxyToBitmap(image)
                            image.close()
                            if (originalBitmap != null) {
                                val resizedBitmap = resizeBitmap(originalBitmap, 800)
                                mainExecutor.execute { onImageCaptured(resizedBitmap) }
                            }
                        }
                            override fun onError(exception: ImageCaptureException) {
                                Log.e("AITracker", "Capture failed", exception)
                            }
                        })
                    },
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                ) {
                    Icon(Icons.Default.Camera, contentDescription = "Capture", tint = Color.Black, modifier = Modifier.size(40.dp))
                }
                
                // Placeholder to balance the Gallery button
                Spacer(modifier = Modifier.size(56.dp))
            }
        }
    }
}

@Composable
fun AnalyzingState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = WayBag_Lime)
            Spacer(modifier = Modifier.height(16.dp))
            Text("AI is analyzing your receipt...", color = Color.White)
            Text("Checking for plastic bags & timestamps", fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
fun ResultState(
    state: AITrackerState,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        ThinGlassPanel(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (state.error != null) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.Red, modifier = Modifier.size(64.dp))
                    Text(text = "Error", style = MaterialTheme.typography.headlineSmall, color = Color.Red)
                    Text(text = state.error, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                } else if (state.analysisResult != null) {
                    if (state.isSuccess) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = WayBag_Lime, modifier = Modifier.size(64.dp))
                    } else {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red, modifier = Modifier.size(64.dp))
                    }
                    
                    Text(
                        text = if (state.isSuccess) "Verification Success" else "Verification Failed",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (state.isSuccess) WayBag_Lime else Color.Red
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                    
                    ResultRow("Store", state.analysisResult?.storeName ?: "")
                    ResultRow("Total", "RM ${state.analysisResult?.totalAmount ?: 0.0}")
                    ResultRow("Date", state.analysisResult?.timestamp?.take(16)?.replace('T', ' ') ?: "")
                    ResultRow("Plastic Bag", if (state.analysisResult?.hasPlasticBag == true) "DETECTED" else "None")
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    state.validationMessage?.let {
                        Text(text = it, style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = if (state.isSuccess) WayBag_Lime else Color.Gray)
                ) {
                    Text(if (state.isSuccess) "Awesome!" else "Try Another Receipt", color = Color.Black)
                }
            }
        }
    }
}

@Composable
fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    return try {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        Log.e("AITracker", "ImageProxy to Bitmap conversion failed", e)
        null
    }
}

private fun resizeBitmap(source: Bitmap, maxSize: Int): Bitmap {
    if (source.width <= maxSize && source.height <= maxSize) return source
    
    val width = source.width
    val height = source.height

    val bitmapRatio = width.toFloat() / height.toFloat()
    val finalWidth: Int
    val finalHeight: Int
    
    if (bitmapRatio > 1) {
        finalWidth = maxSize
        finalHeight = (maxSize / bitmapRatio).toInt()
    } else {
        finalHeight = maxSize
        finalWidth = (maxSize * bitmapRatio).toInt()
    }
    val result = Bitmap.createScaledBitmap(source, finalWidth, finalHeight, true)
    if (result != source) {
        // source.recycle() // Be careful with recycling if you need the original elsewhere
    }
    return result
}
