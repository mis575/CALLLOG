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
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.example.calllog.ui.ExecutiveDashboard
import com.example.calllog.ui.LoginScreen
import com.example.calllog.ui.ManagementDashboard
import com.example.calllog.ui.theme.CallLogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SyncManager.init(applicationContext)
        com.example.calllog.network.SocketManager.init(applicationContext)
        
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
                LaunchedEffect(isLoggedIn) {
                    if (isLoggedIn) {
                        com.example.calllog.network.SocketManager.registerDevice()
                    }
                }
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
                    
                    // Startup version check (retries every 15s until successful to handle sleeping servers)
                    var checkSucceeded = false
                    while (!checkSucceeded) {
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
                                checkSucceeded = true
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Version check failed: ${e.message}", e)
                        }
                        if (!checkSucceeded) {
                            kotlinx.coroutines.delay(15000)
                        }
                    }
                }

                val coroutineScope = rememberCoroutineScope()
                var downloadProgress by remember { mutableFloatStateOf(-1f) }

                if (downloadProgress >= 0f) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { /* Prevent dismiss during download */ },
                        title = { androidx.compose.material3.Text("Downloading Update") },
                        text = {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                            ) {
                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = downloadProgress,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                androidx.compose.material3.Text("${(downloadProgress * 100).toInt()}%")
                            }
                        },
                        confirmButton = {},
                        dismissButton = {}
                    )
                }

                updateInfo?.let { info ->
                    val context = LocalContext.current
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { /* Prevent dismiss on outside touch */ },
                        title = { androidx.compose.material3.Text("Update Available") },
                        text = { androidx.compose.material3.Text("A new version (${info.latestVersionName}) is available. Please update to get the latest features and fixes.") },
                        confirmButton = {
                            androidx.compose.material3.Button(onClick = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    if (!context.packageManager.canRequestPackageInstalls()) {
                                        try {
                                            val settingsIntent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            context.startActivity(settingsIntent)
                                            Toast.makeText(context, "Please enable permission to install updates and try again.", Toast.LENGTH_LONG).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Failed to open settings: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                        }
                                        return@Button
                                    }
                                }

                                // Start download
                                downloadProgress = 0f
                                coroutineScope.launch {
                                    val destinationFile = File(context.getExternalFilesDir(null), "app-update.apk")
                                    val success = downloadApk(info.updateUrl, destinationFile) { progress ->
                                        downloadProgress = progress / 100f
                                    }
                                    downloadProgress = -1f // Hide dialog
                                    if (success) {
                                        installApk(context, destinationFile)
                                    } else {
                                        Toast.makeText(context, "Download failed.", Toast.LENGTH_LONG).show()
                                    }
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

private suspend fun downloadApk(urlString: String, outputFile: File, onProgress: (Int) -> Unit): Boolean {
    return withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext false
            }

            val fileLength = connection.contentLength
            val input = connection.inputStream
            val output = FileOutputStream(outputFile)

            val data = ByteArray(4096)
            var total: Long = 0
            var count: Int
            while (input.read(data).also { count = it } != -1) {
                total += count
                if (fileLength > 0) {
                    onProgress((total * 100 / fileLength).toInt())
                }
                output.write(data, 0, count)
            }
            output.flush()
            output.close()
            input.close()
            true
        } catch (e: Exception) {
            android.util.Log.e("AppUpdater", "Download failed: ${e.message}", e)
            false
        } finally {
            connection?.disconnect()
        }
    }
}

private fun installApk(context: android.content.Context, file: File) {
    try {
        val authority = "${context.packageName}.fileprovider"
        val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Installation failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}
