package com.example.calllog.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.calllog.CallLogViewModel
import com.example.calllog.UiState
import com.example.calllog.data.RegisterRequest

@Composable
fun RegisterScreen(
    viewModel: CallLogViewModel,
    onRegisterSuccess: () -> Unit,
    onBackToLogin: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var empId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "CRM Registration", style = MaterialTheme.typography.headlineLarge)
        Text(text = "Default Role: Executive", style = MaterialTheme.typography.bodySmall)
        
        Spacer(modifier = Modifier.height(24.dp))

        TextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        TextField(email, {email=it}, label = {Text("Email")}, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        TextField(phone, {phone=it}, label = {Text("Phone")}, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        TextField(empId, {empId=it}, label = {Text("Emp ID (UserID)")}, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        TextField(password, {password=it}, label = {Text("Password")}, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { viewModel.register(RegisterRequest(name, email, phone, empId, password)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState !is UiState.Loading && name.isNotBlank() && empId.isNotBlank() && password.isNotBlank()
        ) {
            Text("Register")
        }

        TextButton(onClick = onBackToLogin) {
            Text("Already have an account? Login")
        }

        if (uiState is UiState.Loading) CircularProgressIndicator()
        else if (uiState is UiState.Error) Text((uiState as UiState.Error).errorMessage, color = MaterialTheme.colorScheme.error)
        else if (uiState is UiState.Success && (uiState as UiState.Success).outputText == "REGISTER_SUCCESS") {
            LaunchedEffect(Unit) { onRegisterSuccess() }
        }
    }
}
