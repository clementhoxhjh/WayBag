package com.example.waybag.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.waybag.data.model.BagScan
import com.example.waybag.data.model.ValidBag
import com.example.waybag.data.supabase
import com.example.waybag.ui.components.ThinGlassPanel
import com.example.waybag.ui.theme.WayBag_Lime
import com.example.waybag.ui.viewmodel.QRScanViewModel
import com.example.waybag.utils.DateUtils
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun QRScanScreen(
    viewModel: QRScanViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // Lock orientation to portrait when camera is active
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            activity?.requestedOrientation = originalOrientation
        }
    }
    
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
    
    val processingLock = remember { AtomicBoolean(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            if (state.scannedId == null && state.validationError == null) {
                CameraPreview(
                    onBarcodeScanned = { barcodeValue ->
                        if (processingLock.compareAndSet(false, true)) {
                            viewModel.onScanStarted()
                            scope.launch {
                                try {
                                    val timestamp = recordScan(barcodeValue)
                                    if (timestamp != null) {
                                        viewModel.onScanSuccess(barcodeValue.trim(), timestamp)
                                    } else {
                                        viewModel.onScanFailed("Invalid QR Code: This bag is not registered in our system.")
                                        processingLock.set(false)
                                    }
                                } catch (e: Exception) {
                                    Log.e("QRScan", "Failed to record scan", e)
                                    viewModel.onScanFailed("Connection Error: ${e.message}")
                                    processingLock.set(false)
                                }
                            }
                        }
                    }
                )
                
                QRScannerOverlay()

                if (state.isProcessing) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = WayBag_Lime)
                    }
                }
            } else if (state.validationError != null) {
                ErrorState(state.validationError!!) {
                    viewModel.clearError()
                }
            } else {
                SuccessState(
                    uniqueId = state.scannedId!!,
                    timestamp = state.scanTimeTqr ?: ""
                ) {
                    processingLock.set(false)
                    viewModel.reset()
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
    val scrollState = rememberScrollState()
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
            .verticalScroll(scrollState)
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
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(scrollState)
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
            colors = ButtonDefaults.buttonColors(containerColor = WayBag_Lime),
            shape = RoundedCornerShape(50.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Scan Again", color = Color.Black, fontWeight = FontWeight.Bold)
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
