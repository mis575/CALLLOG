package com.example.calllog.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.calllog.CallLogViewModel
import com.example.calllog.UiState
import com.example.calllog.data.AppCallTracker
import com.example.calllog.data.CallRecord
import com.example.calllog.network.SyncManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun SyncScreen(
    viewModel: CallLogViewModel,
    hideAdminButton: Boolean = false,
    onNavigateToAdmin: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val todaysLogs by viewModel.todaysLogs.collectAsState()
    val context = LocalContext.current
    var phoneNumber by remember { mutableStateOf("") }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadTodaysLogs()
                viewModel.loadAssignedContacts()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- HEADER (JJ CRM | User | Logout) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.border(1.dp, Color.Gray, MaterialTheme.shapes.small),
                shape = MaterialTheme.shapes.small
            ) {
                Text(text = "JJ CRM", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontWeight = FontWeight.Bold)
            }
            
            Surface(
                modifier = Modifier.border(1.dp, Color.Gray, MaterialTheme.shapes.small),
                shape = MaterialTheme.shapes.small
            ) {
                Text(text = SyncManager.userName, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }

            Button(
                onClick = { SyncManager.logout(context) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.error),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text("Logout", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- DIALER ---
        Text(text = "Make a Call", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("Phone Number") },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { 
                if (phoneNumber.isNotBlank()) { 
                    AppCallTracker.recordCall(context, phoneNumber, SyncManager.userEmpId)
                    makeCall(context, phoneNumber) 
                } 
            }) {
                Text("Call")
            }
        }

        AssignedLeadsSection(
            viewModel = viewModel,
            onLeadClick = { phoneNumber = it }
        )

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // --- DATE FILTER ---
        var selectedFilter by remember { mutableStateOf("Today") }
        val filters = listOf("Today", "Yesterday", "Last 7 Days", "Last Month", "All", "Custom")
        var showCustomDialog by remember { mutableStateOf(false) }
        var customLabel by remember { mutableStateOf("") }
        val df = SimpleDateFormat("dd MMM", Locale.getDefault())

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            filters.forEach { filter ->
                val label = if (filter == "Custom" && customLabel.isNotEmpty()) customLabel else filter
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { 
                        if (filter == "Custom") {
                            showCustomDialog = true
                        } else {
                            selectedFilter = filter
                            customLabel = ""
                            val cal = Calendar.getInstance()
                            cal.set(Calendar.HOUR_OF_DAY, 0)
                            cal.set(Calendar.MINUTE, 0)
                            cal.set(Calendar.SECOND, 0)
                            cal.set(Calendar.MILLISECOND, 0)
                            
                            when(filter) {
                                "Today" -> viewModel.setDateRange(cal.timeInMillis, System.currentTimeMillis())
                                "Yesterday" -> {
                                    val end = cal.timeInMillis - 1
                                    cal.add(Calendar.DAY_OF_YEAR, -1)
                                    viewModel.setDateRange(cal.timeInMillis, end)
                                }
                                "Last 7 Days" -> {
                                    cal.add(Calendar.DAY_OF_YEAR, -7)
                                    viewModel.setDateRange(cal.timeInMillis, System.currentTimeMillis())
                                }
                                "Last Month" -> {
                                    cal.add(Calendar.MONTH, -1)
                                    viewModel.setDateRange(cal.timeInMillis, System.currentTimeMillis())
                                }
                                else -> viewModel.setDateRange(null, null)
                            }
                        }
                    },
                    label = { Text(label, fontSize = 9.sp) }
                )
            }
        }

        if (showCustomDialog) {
            CustomDateRangeDialog(
                onDismiss = { showCustomDialog = false },
                onApply = { start, end ->
                    selectedFilter = "Custom"
                    customLabel = "${df.format(Date(start))}-${df.format(Date(end))}"
                    viewModel.setDateRange(start, end)
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (!hideAdminButton && (SyncManager.userRole == "Manager" || SyncManager.userRole == "TL" || SyncManager.userRole == "Admin")) {
            Button(onClick = onNavigateToAdmin, modifier = Modifier.fillMaxWidth()) { Text("Go to Management Dashboard") }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // --- TODAY CALL LOG ---
        Text(text = "Today Call Log", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        
        Box(modifier = Modifier.weight(1f)) {
            if (todaysLogs.isEmpty()) {
                Text("No calls recorded today.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(todaysLogs.size) { index ->
                        CallLogItem(log = todaysLogs[index], viewModel = viewModel, uiState = uiState)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }

        if (uiState is UiState.Loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        else if (uiState is UiState.Error) Text((uiState as UiState.Error).errorMessage, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun CallLogItem(log: CallRecord, viewModel: CallLogViewModel, uiState: UiState) {
    var expanded by remember { mutableStateOf(false) }
    val statuses = listOf("Interested", "Not Interested", "Prospect")

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, Color.LightGray)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Row 1: Duration | User Name | Time
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.border(1.dp, Color.LightGray, MaterialTheme.shapes.small), shape = MaterialTheme.shapes.small) {
                    Text(text = "Duration: ${log.duration}s", modifier = Modifier.padding(4.dp), style = MaterialTheme.typography.bodySmall)
                }
                Surface(modifier = Modifier.border(1.dp, Color.LightGray, MaterialTheme.shapes.small), shape = MaterialTheme.shapes.small) {
                    Text(text = "User: ${log.syncedBy ?: SyncManager.userName}", modifier = Modifier.padding(4.dp), style = MaterialTheme.typography.bodySmall)
                }
                Surface(modifier = Modifier.border(1.dp, Color.LightGray, MaterialTheme.shapes.small), shape = MaterialTheme.shapes.small) {
                    Text(text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(log.date)), modifier = Modifier.padding(4.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            // Row 2: Contact Person | Company Name
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = log.contactName,
                    onValueChange = { viewModel.updateCallDetails(log.id, contactName = it) },
                    label = { Text("Contact Person", fontSize = 10.sp) },
                    modifier = Modifier.weight(1f),
                    enabled = !log.isSaved,
                    textStyle = MaterialTheme.typography.bodySmall
                )
                TextField(
                    value = log.companyName,
                    onValueChange = { viewModel.updateCallDetails(log.id, companyName = it) },
                    label = { Text("Company Name", fontSize = 10.sp) },
                    modifier = Modifier.weight(1f),
                    enabled = !log.isSaved,
                    textStyle = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 3: Address | Status Selection
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = log.address,
                    onValueChange = { viewModel.updateCallDetails(log.id, address = it) },
                    label = { Text("Address", fontSize = 10.sp) },
                    modifier = Modifier.weight(1f),
                    enabled = !log.isSaved,
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                )
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { if (!log.isSaved) expanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !log.isSaved,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(text = log.status, fontSize = 12.sp)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        statuses.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s) },
                                onClick = { 
                                    viewModel.updateCallDetails(log.id, status = s)
                                    expanded = false 
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 3.5: Email | Format Dropdown
            var formatExpanded by remember { mutableStateOf(false) }
            val formats = listOf(
                "General Catalogue Sharing (Format 0005)",
                "Collaboration Opportunity (Format 0003)",
                "Revolutionize Your Printing (Format 0002)",
                "Elevate Your Brand (Format 0004)"
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = log.email,
                    onValueChange = { viewModel.updateCallDetails(log.id, email = it) },
                    label = { Text("Email", fontSize = 10.sp) },
                    modifier = Modifier.weight(1f),
                    enabled = !log.isSaved,
                    textStyle = MaterialTheme.typography.bodySmall
                )
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { if (!log.isSaved) formatExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !log.isSaved,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = log.format, 
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    DropdownMenu(expanded = formatExpanded, onDismissRequest = { formatExpanded = false }) {
                        formats.forEach { f ->
                            DropdownMenuItem(
                                text = { Text(f) },
                                onClick = { 
                                    viewModel.updateCallDetails(log.id, format = f)
                                    formatExpanded = false 
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 4: Description
            TextField(
                value = log.description,
                onValueChange = { viewModel.updateCallDetails(log.id, description = it) },
                label = { Text("Description", fontSize = 10.sp) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !log.isSaved,
                minLines = 1,
                textStyle = MaterialTheme.typography.bodySmall
            )

            if (!log.isSaved) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.saveCallLog(log) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    enabled = uiState !is UiState.Loading
                ) {
                    if (uiState is UiState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Save & Sync")
                }
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                Text("✓ Saved", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

private fun makeCall(context: Context, number: String) {
    val intent = Intent(Intent.ACTION_CALL).apply { data = Uri.parse("tel:$number") }
    context.startActivity(intent)
}
