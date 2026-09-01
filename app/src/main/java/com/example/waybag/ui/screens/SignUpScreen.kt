package com.example.waybag.ui.screens

import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.waybag.data.supabase
import com.example.waybag.ui.components.*
import com.example.waybag.ui.theme.Apple_Blue
import com.example.waybag.ui.theme.WayBag_Lime
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.waybag.ui.viewmodel.SignUpViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    onBackClick: () -> Unit,
    onSignUpClick: () -> Unit,
    viewModel: SignUpViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()

    // Launcher for Gallery
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onImageSelected(uri)
        }
    }

    // Launcher for Camera
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && state.cameraImageUri != null) {
            viewModel.onImageSelected(state.cameraImageUri)
        }
    }

    fun createTempPictureUri(): Uri {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val file = File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    if (state.showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { 
                viewModel.setShowSuccessDialog(false)
                onSignUpClick() 
            },
            confirmButton = {
                WayBagButton(text = "OK", onClick = { 
                    viewModel.setShowSuccessDialog(false)
                    onSignUpClick() 
                }, modifier = Modifier.fillMaxWidth())
            },
            title = { Text("Account Created", style = MaterialTheme.typography.titleLarge) },
            text = { Text("Your account has been created successfully.", style = MaterialTheme.typography.bodyMedium) },
            shape = RoundedCornerShape(28.dp),
            containerColor = Color.White
        )
    }

    if (state.errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("OK", color = Color.Red)
                }
            },
            title = { Text("Error", color = Color.Red) },
            text = { Text(state.errorMessage!!) },
            shape = RoundedCornerShape(28.dp),
            containerColor = Color.White
        )
    }

    if (state.showImageSourceSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.setShowImageSourceSheet(false) },
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                ListItem(
                    headlineContent = { Text("Take Photo") },
                    leadingContent = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                    modifier = Modifier.clickable {
                        viewModel.setShowImageSourceSheet(false)
                        val uri = createTempPictureUri()
                        viewModel.onCameraImageUriCreated(uri)
                        cameraLauncher.launch(uri)
                    }
                )
                ListItem(
                    headlineContent = { Text("Choose from Gallery") },
                    leadingContent = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                    modifier = Modifier.clickable {
                        viewModel.setShowImageSourceSheet(false)
                        galleryLauncher.launch("image/*")
                    }
                )
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    GlassBackground {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Create Account", style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.Black)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.Black
                    )
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 32.dp)
                        .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Profile Pic Selector (Floating)
                    Box(
                        modifier = Modifier
                            .clickable { viewModel.setShowImageSourceSheet(true) },
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.05f))
                                .border(1.dp, Color.Black.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (state.selectedImageUri != null) {
                                AsyncImage(
                                    model = state.selectedImageUri,
                                    contentDescription = "Profile Picture",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(70.dp), tint = Color.Black.copy(alpha = 0.15f))
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(WayBag_Lime),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(22.dp), tint = Color.Black)
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        WayBagTextField(value = state.name, onValueChange = { viewModel.onNameChange(it) }, label = "Name", placeholder = "Insert full name")
                        
                        Box(modifier = Modifier.fillMaxWidth().clickable { viewModel.setShowDatePicker(true) }) {
                            WayBagTextField(
                                value = state.birthday,
                                onValueChange = { },
                                label = "Birthday",
                                placeholder = "Select birthday",
                                readOnly = true,
                                enabled = false,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        WayBagTextField(value = state.email, onValueChange = { viewModel.onEmailChange(it) }, label = "Email", placeholder = "name@example.com")
                        WayBagTextField(value = state.password, onValueChange = { viewModel.onPasswordChange(it) }, label = "Password", placeholder = "Minimum 8 characters", isPassword = true)
                        WayBagTextField(value = state.confirmPassword, onValueChange = { viewModel.onConfirmPasswordChange(it) }, label = "Confirm Password", placeholder = "Repeat password", isPassword = true)
                    }

                    if (state.showDatePicker) {
                        val datePickerState = rememberDatePickerState()
                        DatePickerDialog(
                            onDismissRequest = { viewModel.setShowDatePicker(false) },
                            confirmButton = {
                                TextButton(onClick = {
                                    datePickerState.selectedDateMillis?.let {
                                        val date = Date(it)
                                        val formattedDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date)
                                        viewModel.onBirthdayChange(formattedDate)
                                    }
                                    viewModel.setShowDatePicker(false)
                                }) {
                                    Text("OK")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { viewModel.setShowDatePicker(false) }) {
                                    Text("Cancel")
                                }
                            }
                        ) {
                            DatePicker(state = datePickerState)
                        }
                    }

                    WayBagButton(
                        text = if (state.isLoading) "Creating..." else "Sign Up",
                        onClick = {
                            viewModel.signUp(context)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(40.dp))
                }
                
                if (state.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.5f)).clickable(enabled = false) {},
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Apple_Blue)
                    }
                }
            }
        }
    }
}
