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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
    var isProcessing by remember { mutableStateOf(false) }
    val processingLock = remember { AtomicBoolean(false) } // Use AtomicBoolean to prevent race conditions from camera frames
    var validationError by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            if (scannedId == null && validationError == null) {
                CameraPreview(
                    onBarcodeScanned = { barcodeValue ->
                        if (processingLock.compareAndSet(false, true)) {
                            isProcessing = true
                            scope.launch {
                                try {
                                    val success = recordScan(barcodeValue)
                                    if (success) {
                                        scannedId = barcodeValue.trim()
                                    } else {
                                        validationError = "Invalid QR Code: This bag is not registered in our system."
                                        processingLock.set(false) // Allow retrying on error
                                    }
                                } catch (e: Exception) {
                                    Log.e("QRScan", "Failed to record scan", e)
                                    validationError = "Connection Error: ${e.message}"
                                    processingLock.set(false) // Allow retrying on error
                                } finally {
                                    isProcessing = false
                                    // Note: We don't necessarily reset processingLock here if we are in SuccessState
                                    // to prevent "double scanning" the same code before the UI transitions.
                                }
                            }
                        }
                    }
                )
                
                // QR Finder Overlay
                QRScannerOverlay()
                
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
                SuccessState(scannedId!!) {
                    processingLock.set(false) // Reset the lock when user clicks "Scan Another"
                    scannedId = null // Reset for next scan
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
fun SuccessState(uniqueId: String, onReset: () -> Unit) {
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
            Text(
                text = "Plastic Bag #$uniqueId has been recorded!",
                modifier = Modifier.padding(16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        
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

private suspend fun recordScan(barcodeValue: String): Boolean {
    val uniqueId = barcodeValue.trim()
    Log.d("QRScan", "Scanning: '$uniqueId'")
    
    try {
        // 1. 尝试查询该 ID
        val response = supabase.from("valid_bags")
            .select {
                filter {
                    eq("unique_id", uniqueId)
                }
            }
        
        // 打印原始数据，帮我们在 Logcat 中查看结果
        Log.d("QRScan", "Supabase Response: ${response.data}")

        val validBag = response.decodeSingleOrNull<ValidBag>()

        if (validBag == null) {
            Log.e("QRScan", "ID not found in database: $uniqueId")
            return false
        }

        // 2. 记录扫描记录
        val user = supabase.auth.currentUserOrNull()
        if (user != null) {
            val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date())
            val scan = BagScan(
                userId = user.id,
                bagUniqueId = uniqueId,
                timestamp = timestamp
            )
            supabase.from("bag_scans").insert(scan)
            Log.d("QRScan", "Scan recorded successfully for user: ${user.id}")
            return true
        } else {
            Log.e("QRScan", "User not logged in")
        }
    } catch (e: Exception) {
        Log.e("QRScan", "Error during validation: ${e.message}", e)
        throw e // 抛出异常让外部显示 Connection Error
    }
    return false
}
