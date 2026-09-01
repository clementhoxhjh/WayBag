package com.example.waybag.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.waybag.ui.components.*
import com.example.waybag.ui.viewmodel.ProfileViewModel
import com.example.waybag.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ProfileScreen(
    onBackClick: () -> Unit,
    onLogoutClick: () -> Unit,
    viewModel: ProfileViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var showLogoutDialog by remember { mutableStateOf(false) }

    if (state.error != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("OK", color = Color.Red)
                }
            },
            title = { Text("Error", color = Color.Red) },
            text = { Text(state.error!!) },
            shape = RoundedCornerShape(28.dp),
            containerColor = Color.White
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log Out?") },
            text = { Text("Are you sure you want to log out from WayBag?") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    scope.launch {
                        com.example.waybag.data.clearStorage(context)
                        onLogoutClick()
                    }
                }) {
                    Text("Log Out", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = Premium_Gray)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(28.dp)
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val inputStream = context.contentResolver.openInputStream(it)
            val bytes = inputStream?.readBytes()
            bytes?.let { b -> viewModel.uploadAvatar(b) }
        }
    }

    GlassBackground {
        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = if (isLandscape) 80.dp else 24.dp)
                        .padding(top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Premium_Gray
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Profile Settings",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Premium_Gray
                        )
                    )
                }
            },
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = if (isLandscape) 80.dp else 24.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.widthIn(max = 500.dp)) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))

                        // Profile Card
                        ThinGlassPanel(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(24.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Avatar
                                Box(
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(CircleShape)
                                        .clickable { launcher.launch("image/*") }
                                ) {
                                    if (state.userProfile?.avatar_url != null) {
                                        AsyncImage(
                                            model = state.userProfile?.avatar_url,
                                            contentDescription = "Profile Avatar",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Surface(
                                            modifier = Modifier.fillMaxSize(),
                                            color = WayBag_Lime.copy(alpha = 0.2f)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = if (state.name.isNotEmpty()) state.name.take(1).uppercase() else "U",
                                                    style = MaterialTheme.typography.displaySmall,
                                                    color = WayBag_Lime
                                                )
                                            }
                                        }
                                    }
                                }

                                Text(
                                    text = "Change Avatar",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = WayBag_Lime,
                                    modifier = Modifier.padding(top = 8.dp)
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                // Fields
                                WayBagTextField(
                                    value = state.name,
                                    onValueChange = { viewModel.onNameChange(it) },
                                    label = "Full Name",
                                    placeholder = "Enter your name",
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                WayBagTextField(
                                    value = state.email,
                                    onValueChange = { viewModel.onEmailChange(it) },
                                    label = "Email Address",
                                    placeholder = "Enter your email",
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Birthday with Date Picker
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

                                if (state.showDatePicker) {
                                    val datePickerState = rememberDatePickerState(
                                        selectableDates = object : SelectableDates {
                                            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                                                return utcTimeMillis <= System.currentTimeMillis()
                                            }
                                        }
                                    )
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

                                Spacer(modifier = Modifier.height(16.dp))

                                WayBagTextField(
                                    value = state.currentPassword,
                                    onValueChange = { viewModel.onCurrentPasswordChange(it) },
                                    label = "Current Password",
                                    placeholder = "Required to change password/email",
                                    isPassword = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                WayBagTextField(
                                    value = state.password,
                                    onValueChange = { viewModel.onPasswordChange(it) },
                                    label = "New Password",
                                    placeholder = "Leave blank to keep current",
                                    isPassword = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        // Save Button
                        WayBagButton(
                            text = if (state.isLoading) "Saving..." else "Save Changes",
                            onClick = { viewModel.saveProfile() },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Logout Button
                        OutlinedButton(
                            onClick = { showLogoutDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .background(Color.Red.copy(alpha = 0.05f), RoundedCornerShape(25.dp))
                                .clip(RoundedCornerShape(12.dp)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red.copy(alpha = 0.3f))
                        ) {
                            Text("Log Out", fontWeight = FontWeight.Bold, color = Color.Red.copy(alpha = 0.8f),)
                        }

                        if (state.updateSuccess) {
                            Text(
                                text = "Profile updated successfully!",
                                color = Color.Black,
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                            LaunchedEffect(Unit) {
                                kotlinx.coroutines.delay(3000)
                                viewModel.resetUpdateSuccess()
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}
