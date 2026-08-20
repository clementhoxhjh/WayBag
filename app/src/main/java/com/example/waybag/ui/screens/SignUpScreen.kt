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
import com.example.waybag.data.model.UserProfile
import com.example.waybag.data.supabase
import com.example.waybag.ui.components.*
import com.example.waybag.ui.theme.Apple_Blue
import com.example.waybag.ui.theme.WayBag_Lime
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.runtime.saveable.rememberSaveable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    onBackClick: () -> Unit,
    onSignUpClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var name by rememberSaveable { mutableStateOf("") }
    var birthday by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }

    var isLoading by rememberSaveable { mutableStateOf(false) }
    var showSuccessDialog by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    
    // Image Selection States
    var selectedImageUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var showImageSourceSheet by rememberSaveable { mutableStateOf(false) }
    var cameraImageUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    // Launcher for Gallery
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
        }
    }

    // Launcher for Camera
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null) {
            selectedImageUri = cameraImageUri
        }
    }

    fun createTempPictureUri(): Uri {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val file = File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { 
                showSuccessDialog = false
                onSignUpClick() 
            },
            confirmButton = {
                WayBagButton(text = "OK", onClick = { 
                    showSuccessDialog = false
                    onSignUpClick() 
                }, modifier = Modifier.fillMaxWidth())
            },
            title = { Text("Account Created", style = MaterialTheme.typography.titleLarge) },
            text = { Text("Your account has been created successfully.", style = MaterialTheme.typography.bodyMedium) },
            shape = RoundedCornerShape(28.dp),
            containerColor = Color.White
        )
    }

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text("OK", color = Color.Red)
                }
            },
            title = { Text("Error", color = Color.Red) },
            text = { Text(errorMessage!!) },
            shape = RoundedCornerShape(28.dp),
            containerColor = Color.White
        )
    }

    if (showImageSourceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showImageSourceSheet = false },
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
                        showImageSourceSheet = false
                        val uri = createTempPictureUri()
                        cameraImageUri = uri
                        cameraLauncher.launch(uri)
                    }
                )
                ListItem(
                    headlineContent = { Text("Choose from Gallery") },
                    leadingContent = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                    modifier = Modifier.clickable {
                        showImageSourceSheet = false
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
                            .clickable { showImageSourceSheet = true },
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
                            if (selectedImageUri != null) {
                                AsyncImage(
                                    model = selectedImageUri,
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
                        WayBagTextField(value = name, onValueChange = { name = it }, label = "Name", placeholder = "Insert full name")
                        WayBagTextField(value = birthday, onValueChange = { birthday = it }, label = "Birthday", placeholder = "DD/MM/YYYY")
                        WayBagTextField(value = email, onValueChange = { email = it }, label = "Email", placeholder = "name@example.com")
                        WayBagTextField(value = password, onValueChange = { password = it }, label = "Password", placeholder = "Minimum 8 characters", isPassword = true)
                        WayBagTextField(value = confirmPassword, onValueChange = { confirmPassword = it }, label = "Confirm Password", placeholder = "Repeat password", isPassword = true)
                    }

                    WayBagButton(
                        text = if (isLoading) "Creating..." else "Sign Up",
                        onClick = {
                            if (password != confirmPassword) {
                                errorMessage = "Passwords do not match"
                                return@WayBagButton
                            }
                            if (email.isBlank() || password.isBlank() || name.isBlank()) {
                                errorMessage = "Please fill all fields"
                                return@WayBagButton
                            }
                            
                            isLoading = true
                            scope.launch {
                                try {
                                    val result = supabase.auth.signUpWith(Email) {
                                        this.email = email
                                        this.password = password
                                    }
                                    
                                    val userId = result?.id
                                    if (userId != null) {
                                        var avatarUrl: String? = null
                                        selectedImageUri?.let { uri ->
                                            val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
                                            if (bytes != null) {
                                                val fileName = "avatar_${userId}_${System.currentTimeMillis()}.jpg"
                                                val bucket = supabase.storage.from("avatars")
                                                bucket.upload(fileName, bytes)
                                                avatarUrl = bucket.publicUrl(fileName)
                                            }
                                        }

                                        val profile = UserProfile(
                                            id = userId,
                                            name = name,
                                            birthday = birthday,
                                            email = email,
                                            avatar_url = avatarUrl
                                        )
                                        supabase.from("profiles").insert(profile)
                                        showSuccessDialog = true
                                    } else {
                                        errorMessage = "Sign up failed: User ID is null"
                                    }
                                } catch (e: Exception) {
                                    val message = e.message ?: ""
                                    if (message.contains("User already registered", ignoreCase = true) || 
                                        message.contains("already exists", ignoreCase = true)) {
                                        errorMessage = "email is already sign in"
                                    } else {
                                        errorMessage = e.message ?: "An unexpected error occurred"
                                    }
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(40.dp))
                }
                
                if (isLoading) {
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
