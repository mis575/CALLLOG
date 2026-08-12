package com.example.calllog.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.calllog.AdminViewModel
import com.example.calllog.CallLogViewModel
import com.example.calllog.UiState
import com.example.calllog.data.RegisterRequest
import com.example.calllog.data.TeamMember
import com.example.calllog.data.CallRecord
import com.example.calllog.network.SyncManager
import java.util.Calendar
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState

@Composable
fun ExecutiveDashboard(viewModel: CallLogViewModel) {
    SyncScreen(viewModel, hideAdminButton = true)
}

data class BrowsingContext(
    val empId: String,
    val name: String,
    val role: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagementDashboard(role: String) {
    val adminVM: AdminViewModel = viewModel()
    val context = LocalContext.current
    val callVM: CallLogViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return CallLogViewModel(context.applicationContext) as T
        }
    })

    val members by adminVM.members.collectAsState()
    val reports by adminVM.reports.collectAsState()
    val uiState by adminVM.uiState.collectAsState()
    val allUsers by adminVM.allUsers.collectAsState()

    var roleToAdd by remember { mutableStateOf<String?>(null) }
    var showUserManagement by remember { mutableStateOf(false) }
    var selectedEmpId by remember { mutableStateOf("") }
    var currentTab by remember { mutableStateOf(0) } // 0: Team, 1: My Calls, 2: Leads
    var showExportDialog by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    
    val browsingStack = remember { 
        mutableStateListOf(
            BrowsingContext(
                empId = SyncManager.userEmpId,
                name = SyncManager.userName,
                role = SyncManager.userRole
            )
        ) 
    }
    val currentBrowsing = browsingStack.last()
    val currentBrowsingId = currentBrowsing.empId
    val browsingRole = currentBrowsing.role

    LaunchedEffect(uiState) {
        when (uiState) {
            is UiState.Success -> {
                val msg = (uiState as UiState.Success).outputText
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
            is UiState.Error -> {
                val err = (uiState as UiState.Error).errorMessage
                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            }
            else -> {}
        }
    }
    
    LaunchedEffect(SyncManager.userEmpId) {
        adminVM.loadAllUsers() 
    }

    LaunchedEffect(currentBrowsingId) { 
        adminVM.loadMembers(currentBrowsingId)
        adminVM.loadAllUsers()
        
        // Default to Team/Subordinate report when entering a level
        // (This allows Admin to see all, Manager to see all team, TL to see all team by default)
        adminVM.loadReports(listOf(currentBrowsingId), includeSubs = true)
        selectedEmpId = "" 
    }

    // Real-time polling loop (runs every 10 seconds to auto-refresh data)
    LaunchedEffect(currentBrowsingId, selectedEmpId, currentTab) {
        if (currentTab == 0) {
            while (true) {
                kotlinx.coroutines.delay(10000)
                adminVM.loadMembers(currentBrowsingId)
                if (selectedEmpId.isNotEmpty()) {
                    adminVM.loadReports(listOf(selectedEmpId), includeSubs = false)
                } else {
                    adminVM.loadReports(listOf(currentBrowsingId), includeSubs = true)
                }
            }
        }
    }

    LaunchedEffect(showUserManagement) { if(showUserManagement) adminVM.loadAllUsers() }

    Scaffold(
        topBar = { 
            TopAppBar(
                title = { 
                    Column {
                        Text(text = "JJ CRM", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        val breadcrumb = if (browsingStack.size > 1) " > ${currentBrowsing.name}" else ""
                        Text(text = "${SyncManager.userName} ($role)$breadcrumb", fontSize = 12.sp, color = Color.Gray)
                    }
                },
                navigationIcon = {
                    if(browsingStack.size > 1 || showUserManagement) {
                        IconButton(onClick = { 
                            if(showUserManagement) showUserManagement = false
                            else if(browsingStack.size > 1) browsingStack.removeAt(browsingStack.size - 1)
                        }) { Text("←") }
                    }
                },
                actions = {
                    if (role == "Admin" && !showUserManagement) {
                        Button(onClick = { showUserManagement = true }, modifier = Modifier.padding(end = 8.dp)) { Text("Users") }
                    }
                    TextButton(onClick = { SyncManager.logout(context) }) {
                        Text("Logout", color = Color.Red)
                    }
                }
            ) 
        }
    ) { p ->
        Column(Modifier.padding(p).fillMaxSize().background(Color(0xFFFAFAFA))) {
            if (showUserManagement) {
                UserManagementView(adminVM, allUsers, currentBrowsingId)
            } else {
                TabRow(selectedTabIndex = currentTab, containerColor = Color.White) {
                    Tab(selected = currentTab == 0, onClick = { currentTab = 0 }, text = { Text("Discovery") })
                    Tab(selected = currentTab == 1, onClick = { currentTab = 1 }, text = { Text("My Calls") })
                    Tab(selected = currentTab == 2, onClick = { currentTab = 2 }, text = { Text("Team Leads") })
                }

                Column(Modifier.padding(16.dp)) {
                    if (currentTab == 0 || role == "Admin") {
                        DateFilterRow(onFilterChange = { start, end ->
                            adminVM.setDateRange(start, end)
                            // Refresh reports for selected employee if set, otherwise the current browsing level team reports
                            if (selectedEmpId.isNotEmpty()) {
                                adminVM.loadReports(listOf(selectedEmpId), includeSubs = false)
                            } else {
                                adminVM.loadReports(listOf(currentBrowsingId), includeSubs = true)
                            }
                        })

                        Spacer(Modifier.height(16.dp))

                        DashboardMainView(browsingRole, adminVM, members, reports, uiState, selectedEmpId, context, currentBrowsingId,
                            onAddUser = { roleToAdd = it },
                            onSelectMember = { m ->
                                if (m.role?.equals("Manager", ignoreCase = true) == true || 
                                    m.role?.equals("TL", ignoreCase = true) == true) {
                                    selectedEmpId = "" // Clear previous individual selection
                                    browsingStack.add(BrowsingContext(m.empId, m.name, m.role ?: "Executive"))
                                } else {
                                    selectedEmpId = m.empId
                                    adminVM.loadReports(listOf(m.empId), includeSubs = false)
                                }
                            },
                            onTeamReportClick = { m ->
                                selectedEmpId = m.empId
                                adminVM.loadReports(listOf(m.empId), includeSubs = true)
                            },
                            onExportClick = { showExportDialog = true },
                            onTransferClick = { showTransferDialog = true }
                        )
                    } else if (currentTab == 1) {
                        PersonalCallingView(callVM)
                    } else {
                        TeamLeadsView(adminVM, currentBrowsingId)
                    }
                }
            }
        }
    }

    if (showExportDialog) {
        ExportSelectionDialog(
            role = role,
            members = members,
            onDismiss = { showExportDialog = false },
            onExport = { targetIds, includeSubs, start, end ->
                adminVM.setDateRange(start, end)
                adminVM.fetchAndExport(context, targetIds, includeSubs = includeSubs)
                showExportDialog = false
            }
        )
    }

    if (showTransferDialog) {
        TransferNumbersDialog(
            adminVM = adminVM,
            onDismiss = { showTransferDialog = false }
        )
    }

    roleToAdd?.let { r ->
        AddUserDialog(
            roleToAdd = r,
            browsingRole = browsingRole,
            availableTLs = allUsers.filter { it.role?.equals("TL", ignoreCase = true) == true },
            onDismiss = { roleToAdd = null },
            onConfirm = { 
                val finalReportsTo = if (it.reportsTo.isNotEmpty()) it.reportsTo else currentBrowsingId
                adminVM.createUser(it.copy(reportsTo = finalReportsTo), refreshId = currentBrowsingId)
                roleToAdd = null 
            }
        )
    }
}

@Composable
fun PersonalCallingView(viewModel: CallLogViewModel) {
    val todaysLogs by viewModel.todaysLogs.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
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

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(10000)
            viewModel.loadTodaysLogs()
            viewModel.loadAssignedContacts()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color.LightGray)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(text = "Quick Dialer", fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        label = { Text("Phone Number") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { 
                        if (phoneNumber.isNotBlank()) {
                            com.example.calllog.data.AppCallTracker.recordCall(context, phoneNumber, SyncManager.userEmpId)
                            val intent = android.content.Intent(android.content.Intent.ACTION_CALL).apply { 
                                data = android.net.Uri.parse("tel:$phoneNumber") 
                            }
                            context.startActivity(intent)
                        }
                    }) { Text("Call") }
                }
            }
        }

        AssignedLeadsSection(
            viewModel = viewModel,
            onLeadClick = { phoneNumber = it }
        )

        Spacer(Modifier.height(16.dp))
        DateFilterRow(onFilterChange = { start, end ->
            viewModel.setDateRange(start, end)
        })
        Spacer(Modifier.height(16.dp))
        Text(text = "My Personal Logs", fontWeight = FontWeight.Bold)
        
        Box(Modifier.weight(1f)) {
            if (todaysLogs.isEmpty()) {
                Text("No calls today.", Modifier.align(Alignment.Center), color = Color.Gray)
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(todaysLogs) { log ->
                        CallLogItem(log = log, viewModel = viewModel, uiState = uiState)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
        if (uiState is UiState.Loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
fun DateFilterRow(onFilterChange: (Long?, Long?) -> Unit) {
    var selectedFilter by remember { mutableStateOf("All") }
    val filters = listOf("Today", "Yesterday", "Last 7 Days", "Last Month", "All", "Custom")
    var showCustomDialog by remember { mutableStateOf(false) }
    var customLabel by remember { mutableStateOf("") }
    
    val df = SimpleDateFormat("dd MMM", Locale.getDefault())

    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                        val range = getRangeForFilter(filter)
                        onFilterChange(range.first, range.second)
                    }
                },
                label = { Text(label, fontSize = 10.sp) }
            )
        }
    }
    
    if (showCustomDialog) {
        CustomDateRangeDialog(
            onDismiss = { showCustomDialog = false },
            onApply = { start, end ->
                selectedFilter = "Custom"
                customLabel = "${df.format(Date(start))}-${df.format(Date(end))}"
                onFilterChange(start, end)
            }
        )
    }
}

private fun getRangeForFilter(filter: String): Pair<Long?, Long?> {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    
    return when(filter) {
        "Today" -> Pair(cal.timeInMillis, System.currentTimeMillis())
        "Yesterday" -> {
            val end = cal.timeInMillis - 1
            cal.add(Calendar.DAY_OF_YEAR, -1)
            Pair(cal.timeInMillis, end)
        }
        "Last 7 Days" -> {
            val end = System.currentTimeMillis()
            cal.add(Calendar.DAY_OF_YEAR, -7)
            Pair(cal.timeInMillis, end)
        }
        "Last Month" -> {
            val end = System.currentTimeMillis()
            cal.add(Calendar.MONTH, -1)
        Pair(cal.timeInMillis, end)
        }
        else -> Pair(null, null)
    }
}

@Composable
fun DashboardMainView(
    role: String, adminVM: AdminViewModel, members: List<TeamMember>, reports: List<com.example.calllog.data.CallRecord>,
    uiState: UiState, selectedEmpId: String, context: android.content.Context, currentBrowsingId: String,
    onAddUser: (String) -> Unit, onSelectMember: (TeamMember) -> Unit,
    onTeamReportClick: (TeamMember) -> Unit,
    onExportClick: () -> Unit,
    onTransferClick: () -> Unit
) {
    val cleanRole = (role ?: "Executive").uppercase()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (cleanRole == "ADMIN") {
                Button(onClick = { onAddUser("Manager") }, modifier = Modifier.weight(1f)) { Text("Add Manager", fontSize = 12.sp) }
            } else if (cleanRole == "MANAGER") {
                Button(onClick = { onAddUser("TL") }, modifier = Modifier.weight(0.7f)) { Text("Add TL", fontSize = 12.sp) }
                Button(onClick = { onAddUser("Executive") }, modifier = Modifier.weight(0.7f)) { Text("Add Exec", fontSize = 12.sp) }
                Button(onClick = onTransferClick, modifier = Modifier.weight(0.8f)) { Text("Assign Leads", fontSize = 12.sp) }
            } else if (cleanRole == "TL") {
                Button(onClick = { onAddUser("Executive") }, modifier = Modifier.weight(0.8f)) { Text("Add Executive", fontSize = 12.sp) }
                Button(onClick = onTransferClick, modifier = Modifier.weight(0.8f)) { Text("Assign Leads", fontSize = 12.sp) }
            }
            Button(onClick = onExportClick, modifier = if(cleanRole=="EXECUTIVE") Modifier.fillMaxWidth() else Modifier.weight(0.6f)) { 
                Text("Export", fontSize = 12.sp) 
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Team Discovery", fontWeight = FontWeight.Bold)
        
        Box(Modifier.weight(0.4f)) {
            if (members.isEmpty()) Text("No subordinates found.", Modifier.align(Alignment.Center), color = Color.Gray)
            else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(members) { m ->
                        val displayRole = if(m.role.isNullOrEmpty()) "Executive" else m.role
                        val roleColor = when (displayRole) {
                            "Admin" -> Color(0xFFE53935)      // Red
                            "Manager" -> Color(0xFF1E88E5)    // Blue
                            "TL" -> Color(0xFF8E24AA)         // Purple
                            else -> Color(0xFF43A047)         // Green (Executive)
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onSelectMember(m) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedEmpId == m.empId) Color(0xFFE3F2FD) else Color.White
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (selectedEmpId == m.empId) Color(0xFF90CAF9) else Color(0xFFE0E0E0)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Avatar circle with initial letter
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(roleColor.copy(alpha = 0.1f), shape = androidx.compose.foundation.shape.CircleShape)
                                        .border(1.dp, roleColor.copy(alpha = 0.3f), shape = androidx.compose.foundation.shape.CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = m.name.take(1).uppercase(),
                                        color = roleColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }

                                Spacer(Modifier.width(12.dp))

                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = m.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF212121)
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "ID: ${m.empId}",
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        // Badge
                                        Surface(
                                            shape = MaterialTheme.shapes.extraSmall,
                                            color = roleColor.copy(alpha = 0.12f),
                                            border = BorderStroke(1.dp, roleColor.copy(alpha = 0.4f))
                                        ) {
                                            Text(
                                                text = displayRole,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                                color = roleColor,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                }

                                if (displayRole == "Manager" || displayRole == "TL") {
                                    IconButton(onClick = { onTeamReportClick(m) }) {
                                        Text("📊", fontSize = 16.sp)
                                    }
                                }

                                if (SyncManager.userRole == "Admin" || SyncManager.userRole == "Manager") {
                                    IconButton(onClick = { adminVM.deleteUser(m.empId, currentBrowsingId) }) {
                                        Text("🗑", color = Color.Red, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        if (true) { // Always show reports/summary section
            if (uiState is UiState.Loading && reports.isEmpty()) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                val reportTitle = if (selectedEmpId.isEmpty()) "Team/Overall Report" else "Individual Report"
                Text(reportTitle, fontWeight = FontWeight.Bold)

                Spacer(Modifier.height(8.dp))

                var selectedStatusFilter by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(reports) {
                    selectedStatusFilter = null
                }

                val filteredReports = remember(reports, selectedStatusFilter) {
                    when (selectedStatusFilter) {
                        "Interested" -> reports.filter { it.status.equals("Interested", ignoreCase = true) }
                        "Not Interested" -> reports.filter { it.status.equals("Not Interested", ignoreCase = true) }
                        "Prospect" -> reports.filter { it.status.equals("Prospect", ignoreCase = true) }
                        "Missed Call" -> reports.filter { it.duration == 0L }
                        "Follow Up" -> reports.filter { it.status.equals("Follow Up", ignoreCase = true) }
                        else -> reports
                    }
                }

                val plannedValues = remember {
                    mutableStateMapOf<Int, Int>().apply {
                        for (h in 9..18) {
                            this[h] = 10
                        }
                    }
                }

                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        // Summary Statistics Card
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                            border = BorderStroke(1.dp, Color.LightGray)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Report Summary (Click to Filter Logs)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.DarkGray)
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { selectedStatusFilter = null }
                                            .border(
                                                width = if (selectedStatusFilter == null) 1.5.dp else 0.dp,
                                                color = if (selectedStatusFilter == null) Color.Black else Color.Transparent,
                                                shape = MaterialTheme.shapes.extraSmall
                                            )
                                            .padding(4.dp)
                                    ) {
                                        Text("Total Calls", fontSize = 10.sp, color = Color.Gray)
                                        Text("${reports.size}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Black)
                                    }
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { selectedStatusFilter = "Interested" }
                                            .border(
                                                width = if (selectedStatusFilter == "Interested") 1.5.dp else 0.dp,
                                                color = if (selectedStatusFilter == "Interested") Color(0xFF43A047) else Color.Transparent,
                                                shape = MaterialTheme.shapes.extraSmall
                                            )
                                            .padding(4.dp)
                                    ) {
                                        Text("Interested", fontSize = 10.sp, color = Color.Gray)
                                        Text("${reports.count { it.status.equals("Interested", ignoreCase = true) }}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF43A047))
                                    }
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { selectedStatusFilter = "Not Interested" }
                                            .border(
                                                width = if (selectedStatusFilter == "Not Interested") 1.5.dp else 0.dp,
                                                color = if (selectedStatusFilter == "Not Interested") Color(0xFFE53935) else Color.Transparent,
                                                shape = MaterialTheme.shapes.extraSmall
                                            )
                                            .padding(4.dp)
                                    ) {
                                        Text("Not Interested", fontSize = 10.sp, color = Color.Gray)
                                        Text("${reports.count { it.status.equals("Not Interested", ignoreCase = true) }}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFE53935))
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { selectedStatusFilter = "Prospect" }
                                            .border(
                                                width = if (selectedStatusFilter == "Prospect") 1.5.dp else 0.dp,
                                                color = if (selectedStatusFilter == "Prospect") Color(0xFF1E88E5) else Color.Transparent,
                                                shape = MaterialTheme.shapes.extraSmall
                                            )
                                            .padding(4.dp)
                                    ) {
                                        Text("Prospect", fontSize = 10.sp, color = Color.Gray)
                                        Text("${reports.count { it.status.equals("Prospect", ignoreCase = true) }}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E88E5))
                                    }
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { selectedStatusFilter = "Missed Call" }
                                            .border(
                                                width = if (selectedStatusFilter == "Missed Call") 1.5.dp else 0.dp,
                                                color = if (selectedStatusFilter == "Missed Call") Color.DarkGray else Color.Transparent,
                                                shape = MaterialTheme.shapes.extraSmall
                                            )
                                            .padding(4.dp)
                                    ) {
                                        Text("Missed Call", fontSize = 10.sp, color = Color.Gray)
                                        Text("${reports.count { it.duration == 0L }}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.DarkGray)
                                    }
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { selectedStatusFilter = "Follow Up" }
                                            .border(
                                                width = if (selectedStatusFilter == "Follow Up") 1.5.dp else 0.dp,
                                                color = if (selectedStatusFilter == "Follow Up") Color(0xFFFF9800) else Color.Transparent,
                                                shape = MaterialTheme.shapes.extraSmall
                                            )
                                            .padding(4.dp)
                                    ) {
                                        Text("Follow Up", fontSize = 10.sp, color = Color.Gray)
                                        Text("${reports.count { it.status.equals("Follow Up", ignoreCase = true) }}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFFF9800))
                                    }
                                }
                            }
                        }
                    }

                    item {
                        // Hourly Performance Table
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            border = BorderStroke(1.dp, Color.LightGray),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Hourly Performance Table", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.DarkGray)
                                Spacer(Modifier.height(8.dp))
                                
                                // Table Header
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(Color(0xFFEEEEEE)).padding(vertical = 6.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Hour Slot", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text("Planned", modifier = Modifier.weight(1.2f), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    Text("Actual", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    Text("Status", modifier = Modifier.weight(1.3f), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                                }
                                
                                // Table Rows
                                var totalPlanned = 0
                                var totalActual = 0
                                
                                val slots = listOf(
                                    9 to "09:00 - 10:00",
                                    10 to "10:00 - 11:00",
                                    11 to "11:00 - 12:00",
                                    12 to "12:00 - 13:00",
                                    13 to "13:00 - 14:00",
                                    14 to "14:00 - 15:00",
                                    15 to "15:00 - 16:00",
                                    16 to "16:00 - 17:00",
                                    17 to "17:00 - 18:00",
                                    18 to "18:00 - 19:00"
                                )
                                
                                slots.forEach { (hourStart, label) ->
                                    val actual = reports.count { log ->
                                        val cal = Calendar.getInstance().apply { timeInMillis = log.date }
                                        val hour = cal.get(Calendar.HOUR_OF_DAY)
                                        hour == hourStart
                                    }
                                    val planned = plannedValues[hourStart] ?: 0
                                    
                                    totalActual += actual
                                    totalPlanned += planned
                                    
                                    val ratio = if (planned > 0) (actual.toFloat() / planned.toFloat()) else 0f
                                    val pct = ratio * 100
                                    val status = when {
                                        planned == 0 && actual == 0 -> "-"
                                        planned == 0 && actual > 0 -> "Excellent"
                                        pct >= 90f -> "Excellent"
                                        pct >= 80f -> "Better"
                                        pct >= 70f -> "Good"
                                        pct >= 50f -> "Average"
                                        else -> "Poor"
                                    }
                                    
                                    val statusColor = when (status) {
                                        "Excellent" -> Color(0xFF2E7D32)
                                        "Better" -> Color(0xFF4CAF50)
                                        "Good" -> Color(0xFF2196F3)
                                        "Average" -> Color(0xFFFF9800)
                                        "Poor" -> Color(0xFFD32F2F)
                                        else -> Color.Gray
                                    }
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(label, modifier = Modifier.weight(1.5f), fontSize = 11.sp)
                                        
                                        Box(modifier = Modifier.weight(1.2f), contentAlignment = Alignment.Center) {
                                            var textValue by remember(planned) { mutableStateOf(planned.toString()) }
                                            androidx.compose.foundation.text.BasicTextField(
                                                value = textValue,
                                                onValueChange = { newValue ->
                                                    textValue = newValue.filter { it.isDigit() }
                                                    val newInt = textValue.toIntOrNull() ?: 0
                                                    plannedValues[hourStart] = newInt
                                                },
                                                textStyle = LocalTextStyle.current.copy(
                                                    fontSize = 11.sp,
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                    fontWeight = FontWeight.Medium
                                                ),
                                                modifier = Modifier
                                                    .width(50.dp)
                                                    .border(1.dp, Color.LightGray, MaterialTheme.shapes.extraSmall)
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                        
                                        Text("$actual", modifier = Modifier.weight(1f), fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                        
                                        Text(
                                            text = status, 
                                            modifier = Modifier.weight(1.3f), 
                                            fontSize = 11.sp, 
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                                        )
                                    }
                                    HorizontalDivider(color = Color(0xFFF5F5F5))
                                }
                                
                                // Total row
                                val totalRatio = if (totalPlanned > 0) (totalActual.toFloat() / totalPlanned.toFloat()) else 0f
                                val totalPct = totalRatio * 100
                                val totalStatus = when {
                                    totalPlanned == 0 && totalActual == 0 -> "-"
                                    totalPlanned == 0 && totalActual > 0 -> "Excellent"
                                    totalPct >= 90f -> "Excellent"
                                    totalPct >= 80f -> "Better"
                                    totalPct >= 70f -> "Good"
                                    totalPct >= 50f -> "Average"
                                    else -> "Poor"
                                }
                                val totalStatusColor = when (totalStatus) {
                                    "Excellent" -> Color(0xFF2E7D32)
                                    "Better" -> Color(0xFF4CAF50)
                                    "Good" -> Color(0xFF2196F3)
                                    "Average" -> Color(0xFFFF9800)
                                    "Poor" -> Color(0xFFD32F2F)
                                    else -> Color.Gray
                                }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(Color(0xFFFAFAFA)).padding(vertical = 6.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Total (Day)", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text("$totalPlanned", modifier = Modifier.weight(1.2f), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    Text("$totalActual", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    Text(
                                        text = totalStatus, 
                                        modifier = Modifier.weight(1.3f), 
                                        fontWeight = FontWeight.Bold, 
                                        fontSize = 11.sp, 
                                        color = totalStatusColor,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (selectedStatusFilter == null) "All Calls Logged" else "Calls filtered by: $selectedStatusFilter",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.DarkGray,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    if (filteredReports.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("No matching records found.", color = Color.Gray)
                            }
                        }
                    } else {
                        items(filteredReports) { r ->
                            ReadOnlyCallLogItem(log = r)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DuplicateDateFilterRow(onFilterChange: (Long?, Long?) -> Unit) {
    var selectedFilter by remember { mutableStateOf("All") }
    val filters = listOf("Today", "Yesterday", "Last 7 Days", "Last Month", "All", "Custom")
    var showCustomDialog by remember { mutableStateOf(false) }
    var customLabel by remember { mutableStateOf("") }
    
    val df = SimpleDateFormat("dd MMM", Locale.getDefault())

    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                        val range = getRangeForFilter(filter)
                        onFilterChange(range.first, range.second)
                    }
                },
                label = { Text(label, fontSize = 10.sp) }
            )
        }
    }
    
    if (showCustomDialog) {
        CustomDateRangeDialog(
            onDismiss = { showCustomDialog = false },
            onApply = { start, end ->
                selectedFilter = "Custom"
                customLabel = "${df.format(Date(start))}-${df.format(Date(end))}"
                onFilterChange(start, end)
            }
        )
    }
}

@Composable
fun ExportSelectionDialog(
    role: String,
    members: List<TeamMember>,
    onDismiss: () -> Unit,
    onExport: (List<String>, Boolean, Long?, Long?) -> Unit
) {
    var selectedId by remember { mutableStateOf("") }
    var includeSubordinates by remember { mutableStateOf(role == "Admin" || role == "Manager") }
    var expanded by remember { mutableStateOf(false) }
    
    var selectedDateFilter by remember { mutableStateOf("Today") }
    val dateFilters = listOf("Today", "Yesterday", "Last 7 Days", "Last Month", "All", "Custom")
    
    var showCustomDialog by remember { mutableStateOf(false) }
    var customRange by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    val df = SimpleDateFormat("dd MMM", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Configuration") },
        text = {
            Column {
                Text("1. Select Team/Member:", style = MaterialTheme.typography.labelSmall)
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { expanded = true }, Modifier.fillMaxWidth()) {
                        Text(if(selectedId.isEmpty()) "Entire Visible Team" else selectedId)
                    }
                    DropdownMenu(expanded, { expanded = false }) {
                        DropdownMenuItem(text = { Text("Entire Visible Team") }, onClick = { selectedId = ""; expanded = false })
                        members.forEach { m ->
                            DropdownMenuItem(text = { Text("${m.name} (${m.role ?: "Exec"})") }, onClick = { selectedId = m.empId; expanded = false })
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                Text("2. Select Date Range:", style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    dateFilters.forEach { filter ->
                        val label = if (filter == "Custom" && customRange != null) {
                            "${df.format(Date(customRange!!.first))}-${df.format(Date(customRange!!.second))}"
                        } else filter
                        FilterChip(
                            selected = selectedDateFilter == filter,
                            onClick = { 
                                if (filter == "Custom") {
                                    showCustomDialog = true
                                } else {
                                    selectedDateFilter = filter
                                    customRange = null
                                }
                            },
                            label = { Text(label, fontSize = 9.sp) }
                        )
                    }
                }

                if (role == "Admin" || role == "Manager") {
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = includeSubordinates, onCheckedChange = { includeSubordinates = it })
                        Text("Include all sub-teams (Cumulative)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val targets = if(selectedId.isEmpty()) members.map { it.empId } else listOf(selectedId)
                val range = if (selectedDateFilter == "Custom" && customRange != null) {
                    customRange
                } else {
                    getRangeForFilter(selectedDateFilter)
                }
                onExport(targets, includeSubordinates, range?.first, range?.second)
            }) { Text("Export CSV") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showCustomDialog) {
        CustomDateRangeDialog(
            onDismiss = { showCustomDialog = false },
            onApply = { start, end ->
                selectedDateFilter = "Custom"
                customRange = Pair(start, end)
            }
        )
    }
}

@Composable
fun TeamLeadsView(adminVM: AdminViewModel, currentBrowsingId: String) {
    val leads by adminVM.teamLeadsData.collectAsState()
    val transferMembers by adminVM.transferMembers.collectAsState()
    val selectedLeads = remember { mutableStateListOf<String>() }
    var showTransferDialog by remember { mutableStateOf(false) }

    LaunchedEffect(currentBrowsingId) {
        adminVM.loadTeamLeads(currentBrowsingId)
        adminVM.loadTransferMembers(SyncManager.userEmpId)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Leads Management", fontWeight = FontWeight.Bold)
            if (selectedLeads.isNotEmpty()) {
                Button(onClick = { showTransferDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57C00))) {
                    Text("Transfer (${selectedLeads.size})", fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (leads.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No leads found in this branch.", color = Color.Gray)
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(leads) { lead ->
                    val isSelected = selectedLeads.contains(lead.number)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                if (isSelected) selectedLeads.remove(lead.number)
                                else selectedLeads.add(lead.number)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFFFFF3E0) else Color.White
                        ),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFFF57C00) else Color(0xFFE0E0E0))
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = isSelected, onCheckedChange = { checked ->
                                if (checked == true) selectedLeads.add(lead.number)
                                else selectedLeads.remove(lead.number)
                            })
                            Column(Modifier.weight(1f)) {
                                Text(lead.number, fontWeight = FontWeight.Bold)
                                Text("With: ${lead.assignedToName} (${lead.assignedTo})", fontSize = 11.sp, color = Color.Gray)
                                Text("By: ${lead.assignedByName}", fontSize = 9.sp, color = Color.LightGray)
                            }
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = if (lead.status == "Pending") Color(0xFFFFF3E0) else Color(0xFFE8F5E9)
                            ) {
                                Text(lead.status, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTransferDialog) {
        var expanded by remember { mutableStateOf(false) }
        var selectedMember by remember { mutableStateOf<TeamMember?>(null) }
        
        AlertDialog(
            onDismissRequest = { showTransferDialog = false },
            title = { Text("Transfer Selected Leads") },
            text = {
                Column {
                    Text("Transfer ${selectedLeads.size} leads to:", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { expanded = true }, Modifier.fillMaxWidth()) {
                            Text(selectedMember?.let { "${it.name} (${it.role})" } ?: "Select Assignee")
                        }
                        DropdownMenu(expanded, { expanded = false }) {
                            transferMembers.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text("${m.name} (${m.role ?: "Exec"})") }, 
                                    onClick = { selectedMember = m; expanded = false }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedMember?.let { target ->
                            val leadsToTransfer = leads
                                .filter { selectedLeads.contains(it.number) }
                                .map { com.example.calllog.data.LeadInfo(number = it.number, name = it.name ?: "") }
                            adminVM.assignContacts(
                                leads = leadsToTransfer,
                                assignedTo = target.empId,
                                assignedBy = SyncManager.userEmpId
                            ) { success ->
                                if (success) {
                                    selectedLeads.clear()
                                    adminVM.loadTeamLeads(currentBrowsingId)
                                    showTransferDialog = false
                                }
                            }
                        }
                    },
                    enabled = selectedMember != null
                ) { Text("Confirm Transfer") }
            },
            dismissButton = {
                TextButton(onClick = { showTransferDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun UserManagementView(adminVM: AdminViewModel, allUsers: List<TeamMember>, currentBrowsingId: String) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Global User Management", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(16.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(allUsers) { user ->
                var expandedRole by remember { mutableStateOf(false) }
                var expandedSup by remember { mutableStateOf(false) }
                var selectedRole by remember { mutableStateOf(user.role ?: "Executive") }
                var selectedSup by remember { mutableStateOf(user.reportsTo) }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color.LightGray)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${user.name} (${user.empId})", fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) {
                                OutlinedButton(onClick = { expandedRole = true }, Modifier.fillMaxWidth()) { Text(selectedRole, fontSize = 12.sp) }
                                DropdownMenu(expandedRole, { expandedRole = false }) {
                                    listOf("Executive", "TL", "Manager", "Admin").forEach { r ->
                                        DropdownMenuItem(text = { Text(r) }, onClick = { selectedRole = r; expandedRole = false })
                                    }
                                }
                            }
                            Box(Modifier.weight(1f)) {
                                OutlinedButton(onClick = { expandedSup = true }, Modifier.fillMaxWidth()) { Text(if(selectedSup.isEmpty()) "Superior" else selectedSup, fontSize = 12.sp) }
                                DropdownMenu(expandedSup, { expandedSup = false }) {
                                    DropdownMenuItem(text = { Text("None") }, onClick = { selectedSup = ""; expandedSup = false })
                                    allUsers.filter { it.empId != user.empId }.forEach { s ->
                                        DropdownMenuItem(text = { Text("${s.name} (${s.role})") }, onClick = { selectedSup = s.empId; expandedSup = false })
                                    }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { adminVM.deleteUser(user.empId, currentBrowsingId) }) { Text("Delete", color = Color.Red) }
                            Button(onClick = { adminVM.updateHierarchy(user.empId, selectedRole, selectedSup, currentBrowsingId) }) { Text("Save") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddUserDialog(
    roleToAdd: String,
    browsingRole: String,
    availableTLs: List<TeamMember>,
    onDismiss: () -> Unit,
    onConfirm: (RegisterRequest) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var selectedTL by remember { mutableStateOf("") }
    var tlExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add $roleToAdd") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TextField(name, {name=it}, label = {Text("Full Name")}, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                TextField(email, {email=it}, label = {Text("Email")}, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                TextField(phone, {phone=it}, label = {Text("Phone")}, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                TextField(pass, {password -> pass=password}, label = {Text("Password")}, modifier = Modifier.fillMaxWidth())
                
                // Only show TL selection if we are at Manager level and adding an Executive
                if (roleToAdd == "Executive" && browsingRole == "Manager") {
                    Spacer(Modifier.height(16.dp))
                    Text("Assign to Team Leader:", fontWeight = FontWeight.SemiBold)
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { tlExpanded = true }, Modifier.fillMaxWidth()) { 
                            Text(if(selectedTL.isEmpty()) "Choose TL" else selectedTL) 
                        }
                        DropdownMenu(tlExpanded, { tlExpanded = false }) {
                            availableTLs.forEach { tl ->
                                DropdownMenuItem(text = { Text("${tl.name} (${tl.empId})") }, onClick = { selectedTL = tl.empId; tlExpanded = false })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { 
            Button(onClick = { 
                onConfirm(RegisterRequest(name, email, phone, "", pass, roleToAdd, selectedTL))
                onDismiss()
            }, enabled = name.isNotBlank() && pass.isNotBlank() && (roleToAdd != "Executive" || selectedTL.isNotEmpty() || browsingRole != "Manager")) { 
                Text("Create") 
            } 
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleDatePickerDialog(
    initialSelectedDateMillis: Long? = null,
    onDateSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { onDateSelected(it) }
                    onDismiss()
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@Composable
fun CustomDateRangeDialog(
    onDismiss: () -> Unit,
    onApply: (Long, Long) -> Unit
) {
    var startDate by remember { mutableStateOf<Long?>(null) }
    var endDate by remember { mutableStateOf<Long?>(null) }
    
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    
    val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Custom Date Range") },
        text = {
            Column(Modifier.fillMaxWidth().padding(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Start Date", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text(
                            text = startDate?.let { df.format(Date(it)) } ?: "Not Selected",
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Button(onClick = { showStartPicker = true }) {
                        Text("Select")
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("End Date", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text(
                            text = endDate?.let { df.format(Date(it)) } ?: "Not Selected",
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Button(onClick = { showEndPicker = true }) {
                        Text("Select")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val start = startDate ?: System.currentTimeMillis()
                    val end = endDate ?: System.currentTimeMillis()
                    val cal = Calendar.getInstance()
                    cal.timeInMillis = end
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59)
                    cal.set(Calendar.MILLISECOND, 999)
                    onApply(start, cal.timeInMillis)
                    onDismiss()
                },
                enabled = startDate != null
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showStartPicker) {
        SimpleDatePickerDialog(
            initialSelectedDateMillis = startDate,
            onDateSelected = { startDate = it },
            onDismiss = { showStartPicker = false }
        )
    }

    if (showEndPicker) {
        SimpleDatePickerDialog(
            initialSelectedDateMillis = endDate,
            onDateSelected = { endDate = it },
            onDismiss = { showEndPicker = false }
        )
    }
}

@Composable
fun ReadOnlyCallLogItem(log: CallRecord) {
    val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val callTypeLabel = when (log.type) {
        1 -> "Incoming"
        2 -> "Outgoing"
        3 -> "Missed"
        else -> "Unknown"
    }
    
    val statusColor = when (log.status) {
        "Interested" -> Color(0xFF4CAF50)
        "Prospect" -> Color(0xFF2196F3)
        "Not Interested" -> Color(0xFF757575)
        "Follow Up" -> Color(0xFFFF9800)
        else -> Color(0xFF9E9E9E)
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Caller: ${log.syncedBy ?: "Unknown"}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF424242)
                )
                Text(
                    text = df.format(Date(log.date)),
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Phone: ${log.number}",
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = Color(0xFF333333)
                )
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = Color(0xFFF5F5F5),
                    border = BorderStroke(1.dp, Color.LightGray)
                ) {
                    Text(
                        text = "$callTypeLabel (${log.duration}s)",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFFF5F5F5))
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!log.contactName.isNullOrBlank() || !log.companyName.isNullOrBlank()) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Contact: ",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.width(70.dp)
                        )
                        Text(
                            text = listOfNotNull(
                                log.contactName.takeIf { it.isNotBlank() },
                                log.companyName.takeIf { it.isNotBlank() }
                            ).joinToString(" @ "),
                            fontSize = 12.sp,
                            color = Color(0xFF212121),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                if (!log.address.isNullOrBlank()) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Address: ",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.width(70.dp)
                        )
                        Text(
                            text = log.address,
                            fontSize = 12.sp,
                            color = Color(0xFF212121),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Status: ",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.width(70.dp)
                    )
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = statusColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = log.status,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                if (log.status == "Interested" && log.enquiryReceived.isNotBlank()) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Enquiry: ",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.width(70.dp)
                        )
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (log.enquiryReceived == "Yes") Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                            border = BorderStroke(1.dp, if (log.enquiryReceived == "Yes") Color(0xFF81C784) else Color(0xFFE57373))
                        ) {
                            Text(
                                text = log.enquiryReceived,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                color = if (log.enquiryReceived == "Yes") Color(0xFF2E7D32) else Color(0xFFC62828),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                if (!log.email.isNullOrBlank()) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Email: ",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.width(70.dp)
                        )
                        Text(
                            text = log.email,
                            fontSize = 12.sp,
                            color = Color(0xFF212121),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (!log.format.isNullOrBlank() && log.format != "Select Format") {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Format: ",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.width(70.dp)
                        )
                        Text(
                            text = log.format,
                            fontSize = 12.sp,
                            color = Color(0xFF212121),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (!log.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Notes:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = log.description,
                        fontSize = 12.sp,
                        color = Color(0xFF424242),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFAFAFA), shape = MaterialTheme.shapes.extraSmall)
                            .padding(8.dp)
                            .border(BorderStroke(1.dp, Color(0xFFEEEEEE)), shape = MaterialTheme.shapes.extraSmall)
                    )
                }
            }
        }
    }
}

@Composable
fun AssignedLeadsSection(
    viewModel: CallLogViewModel,
    onLeadClick: (String) -> Unit
) {
    val assignedContacts by viewModel.assignedContacts.collectAsState()
    val context = LocalContext.current
    
    LaunchedEffect(SyncManager.userEmpId) {
        viewModel.loadAssignedContacts()
    }
    
    if (assignedContacts.isNotEmpty()) {
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFF57C00)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Assigned Leads (${assignedContacts.size})",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE65100),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(assignedContacts) { lead ->
                        Card(
                            modifier = Modifier
                                .width(180.dp)
                                .clickable {
                                    onLeadClick(lead.number)
                                    Toast.makeText(context, "Loaded ${lead.number} to dialer", Toast.LENGTH_SHORT).show()
                                },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                            border = BorderStroke(1.dp, Color(0xFFFFB74D))
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = lead.number,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFFE65100)
                                )
                                if (!lead.name.isNullOrBlank()) {
                                    Text(
                                        text = lead.name,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp,
                                        color = Color.Black
                                    )
                                }
                                Text(
                                    text = "By: ${lead.assignedByName}",
                                    fontSize = 11.sp,
                                    color = Color.DarkGray
                                )
                                Text(
                                    text = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(lead.assignedAt)),
                                    fontSize = 9.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TransferNumbersDialog(
    adminVM: AdminViewModel,
    onDismiss: () -> Unit
) {
    var numbersStr by remember { mutableStateOf("") }
    var selectedMember by remember { mutableStateOf<TeamMember?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    val transferMembers by adminVM.transferMembers.collectAsState()
    val uiState by adminVM.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(SyncManager.userEmpId) {
        adminVM.loadTransferMembers(SyncManager.userEmpId)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign / Transfer Numbers") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Assign contact numbers to team members. Executives will see these numbers in their dialer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(Modifier.height(12.dp))
                
                TextField(
                    value = numbersStr,
                    onValueChange = { numbersStr = it },
                    label = { Text("Leads (format Number:Name, comma separated)") },
                    placeholder = { Text("e.g. 9876543210: John Doe, 8765432109") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5
                )
                
                Spacer(Modifier.height(16.dp))
                Text("Select Team Member:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { dropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(selectedMember?.let { "${it.name} (${it.role ?: "Exec"})" } ?: "Choose Assignee")
                    }
                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        if (transferMembers.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No team members found") },
                                onClick = { dropdownExpanded = false }
                            )
                        } else {
                            transferMembers.forEach { member ->
                                DropdownMenuItem(
                                    text = { Text("${member.name} (${member.role ?: "Exec"})") },
                                    onClick = {
                                        selectedMember = member
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedLeads = numbersStr.split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .map { entry ->
                            if (entry.contains(":")) {
                                val parts = entry.split(":", limit = 2)
                                com.example.calllog.data.LeadInfo(number = parts[0].trim(), name = parts[1].trim())
                            } else if (entry.contains("-")) {
                                val parts = entry.split("-", limit = 2)
                                com.example.calllog.data.LeadInfo(number = parts[0].trim(), name = parts[1].trim())
                            } else {
                                com.example.calllog.data.LeadInfo(number = entry, name = "")
                            }
                        }
                    
                    val target = selectedMember
                    if (parsedLeads.isNotEmpty() && target != null) {
                        adminVM.assignContacts(
                            leads = parsedLeads,
                            assignedTo = target.empId,
                            assignedBy = SyncManager.userEmpId
                        ) { success ->
                            if (success) {
                                onDismiss()
                            }
                        }
                    } else {
                        Toast.makeText(context, "Please enter numbers and select an assignee", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = uiState !is UiState.Loading
            ) {
                Text("Assign")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
