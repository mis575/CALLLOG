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

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { _ -> }

                LaunchedEffect(Unit) {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_CALL_LOG,
                            Manifest.permission.CALL_PHONE,
                            Manifest.permission.READ_PHONE_STATE
                        )
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
