package com.example.calllog

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.calllog.network.SyncManager
import com.example.calllog.data.VersionInfo
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.example.calllog.ui.ExecutiveDashboard
import com.example.calllog.ui.LoginScreen
import com.example.calllog.ui.ManagementDashboard
import com.example.calllog.ui.theme.CallLogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SyncManager.init(applicationContext)
        
        setContent {
            CallLogTheme {
                val viewModel: CallLogViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return CallLogViewModel(applicationContext) as T
                        }
                    }
                )

                val isLoggedIn by SyncManager.isLoggedIn.collectAsState()
                var refreshCounter by remember { mutableIntStateOf(0) }
                var updateInfo by remember { mutableStateOf<VersionInfo?>(null) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    if (permissions[Manifest.permission.READ_CALL_LOG] == true) {
                        viewModel.loadTodaysLogs()
                    }
                }

                LaunchedEffect(Unit) {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_CALL_LOG,
                            Manifest.permission.CALL_PHONE,
                            Manifest.permission.READ_PHONE_STATE
                        )
                    )
                    
                    // Startup version check
                    try {
                        val latest = SyncManager.getLatestVersion()
                        if (latest != null) {
                            val packageInfo = applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0)
                            val currentVersion = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                packageInfo.longVersionCode
                            } else {
                                @Suppress("DEPRECATION")
                                packageInfo.versionCode.toLong()
                            }
                            if (latest.latestVersionCode > currentVersion) {
                                updateInfo = latest
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Version check failed: ${e.message}", e)
                    }
                }

                updateInfo?.let { info ->
                    val context = LocalContext.current
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { /* Prevent dismiss on outside touch */ },
                        title = { androidx.compose.material3.Text("Update Available") },
                        text = { androidx.compose.material3.Text("A new version (${info.latestVersionName}) is available. Please update to get the latest features and fixes.") },
                        confirmButton = {
                            androidx.compose.material3.Button(onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.updateUrl)).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Cannot open browser: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            }) {
                                androidx.compose.material3.Text("Update Now")
                            }
                        },
                        dismissButton = {
                            if (!info.forceUpdate) {
                                androidx.compose.material3.TextButton(onClick = { updateInfo = null }) {
                                    androidx.compose.material3.Text("Later")
                                }
                            }
                        }
                    )
                }

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (!isLoggedIn) {
                        LoginScreen(viewModel = viewModel, onLoginSuccess = { refreshCounter++ }, onNavigateToRegister = {})
                    } else {
                        key(refreshCounter) { // Forces recomposition on login success
                            when (SyncManager.userRole) {
                                "Admin" -> ManagementDashboard(role = "Admin")
                                "Manager" -> ManagementDashboard(role = "Manager")
                                "TL" -> ManagementDashboard(role = "TL")
                                else -> ExecutiveDashboard(viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}
