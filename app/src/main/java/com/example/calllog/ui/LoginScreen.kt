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

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.calllog.R

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
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.jj_logo),
            contentDescription = "JJ Group Logo",
            modifier = Modifier
                .size(90.dp)
                .clip(RoundedCornerShape(16.dp))
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "JJ Call CRM", fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
        Text(text = "Real-Time Call & Lead Sync", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(28.dp))

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
