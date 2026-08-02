package com.example.calllog.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.calllog.CallLogViewModel
import com.example.calllog.UiState
import com.example.calllog.data.LoginRequest

@Composable
fun LoginScreen(
    viewModel: CallLogViewModel,
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "CRM Login", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(32.dp))

        TextField(value = username, onValueChange = { username = it }, label = { Text("Employee ID") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        TextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = { viewModel.login(LoginRequest(username, password)) { success -> if(success) onLoginSuccess() } }, modifier = Modifier.fillMaxWidth(), enabled = uiState !is UiState.Loading) {
            Text("Login")
        }

        if (uiState is UiState.Loading) CircularProgressIndicator()
        else if (uiState is UiState.Error) Text((uiState as UiState.Error).errorMessage, color = MaterialTheme.colorScheme.error)
    }
}
