package com.example.calllog.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.calllog.CallLogViewModel
import com.example.calllog.UiState
import com.example.calllog.data.AppCallTracker
import com.example.calllog.data.AssignedContact
import com.example.calllog.data.CallRecord
import com.example.calllog.network.SyncManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val PrimaryBlue  = Color(0xFF1565C0)
private val AccentBlue   = Color(0xFF42A5F5)
private val GreenOk      = Color(0xFF2E7D32)
private val GreenLight   = Color(0xFFE8F5E9)
private val OrangeOk     = Color(0xFFE65100)
private val OrangeLight  = Color(0xFFFFF3E0)
private val RedOk        = Color(0xFFC62828)
private val RedLight     = Color(0xFFFFEBEE)
private val BlueLight    = Color(0xFFE3F2FD)
private val DividerColor = Color(0xFFE0E0E0)
private val PurpleOk     = Color(0xFF6A1B9A)
private val PurpleLight  = Color(0xFFF3E5F5)

@Composable
fun SyncScreen(
    viewModel: CallLogViewModel,
    hideAdminButton: Boolean = false,
    onNavigateToAdmin: () -> Unit = {}
) {
    val uiState    by viewModel.uiState.collectAsState()
    val todaysLogs by viewModel.todaysLogs.collectAsState()
    val context    = LocalContext.current
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

    // Auto-refresh: runs silently in the background; CallLogItem uses local state
    // so this refresh will NOT interrupt any card the user is currently editing.
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30000)
            val hasUnsaved = todaysLogs.any { !it.isSaved }
            if (!hasUnsaved) {
                viewModel.loadTodaysLogs()
                viewModel.loadAssignedContacts()
            }
        }
    }

    var selectedFilter by remember { mutableStateOf("Today") }
    var selectedStatusFilter by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val filters = listOf("Today", "Yesterday", "Last 7 Days", "Last Month", "All", "Custom")
    var showCustomDialog by remember { mutableStateOf(false) }
    var customLabel by remember { mutableStateOf("") }
    val df = SimpleDateFormat("dd MMM", Locale.getDefault())

    val filteredLogs = remember(todaysLogs, selectedStatusFilter, searchQuery) {
        var list = todaysLogs
        if (selectedStatusFilter != null) {
            list = when (selectedStatusFilter) {
                "Interested" -> list.filter { it.status.equals("Interested", ignoreCase = true) }
                "Follow Up" -> list.filter { it.status.equals("Follow Up", ignoreCase = true) }
                "Prospect" -> list.filter { it.status.equals("Prospect", ignoreCase = true) }
                "Enquiry" -> list.filter { it.enquiryReceived.equals("Yes", ignoreCase = true) }
                "No Enquiry" -> list.filter { it.enquiryReceived.equals("No", ignoreCase = true) }
                else -> list
            }
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim()
            list = list.filter {
                it.number.contains(q, ignoreCase = true) ||
                (it.companyName ?: "").contains(q, ignoreCase = true) ||
                (it.contactName ?: "").contains(q, ignoreCase = true)
            }
        }
        list
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F7FA))) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(Color(0xFF0D47A1), Color(0xFF1976D2))))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("JJ CRM", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color.White)
                    Text(SyncManager.userName, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                }
                OutlinedButton(
                    onClick = { SyncManager.logout(context) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Logout", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (uiState is UiState.Loading)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = AccentBlue, trackColor = Color.Transparent)
        else if (uiState is UiState.Error)
            Text((uiState as UiState.Error).errorMessage, color = RedOk,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp), fontSize = 11.sp)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 0.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Make a Call", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = PrimaryBlue)
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = phoneNumber,
                                onValueChange = { phoneNumber = it },
                                label = { Text("Phone Number", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                            Spacer(Modifier.width(10.dp))
                            Button(
                                onClick = {
                                    if (phoneNumber.isNotBlank()) {
                                        AppCallTracker.recordCall(context, phoneNumber, SyncManager.userEmpId)
                                        makeCall(context, phoneNumber)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
                            ) { Text("Call", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                AssignedLeadsSection(viewModel = viewModel, onLeadClick = { phoneNumber = it })
            }

            // ── Follow-Up / Interested quick-redial section ──────────────────
            item {
                FollowUpInterestedSection(
                    logs = todaysLogs,
                    onLeadClick = { number -> phoneNumber = number }
                )
            }

            item {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    filters.forEach { filter ->
                        val label = if (filter == "Custom" && customLabel.isNotEmpty()) customLabel else filter
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = {
                                if (filter == "Custom") { showCustomDialog = true } else {
                                    selectedFilter = filter; customLabel = ""
                                    selectedStatusFilter = null
                                    val cal = Calendar.getInstance()
                                    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                                    cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                                    when (filter) {
                                        "Today"       -> viewModel.setDateRange(cal.timeInMillis, System.currentTimeMillis())
                                        "Yesterday"   -> { val e = cal.timeInMillis - 1; cal.add(Calendar.DAY_OF_YEAR, -1); viewModel.setDateRange(cal.timeInMillis, e) }
                                        "Last 7 Days" -> { cal.add(Calendar.DAY_OF_YEAR, -7); viewModel.setDateRange(cal.timeInMillis, System.currentTimeMillis()) }
                                        "Last Month"  -> { cal.add(Calendar.MONTH, -1); viewModel.setDateRange(cal.timeInMillis, System.currentTimeMillis()) }
                                        else          -> viewModel.setDateRange(null, null)
                                    }
                                }
                            },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PrimaryBlue, selectedLabelColor = Color.White)
                        )
                    }
                }
            }

            if (!hideAdminButton && (SyncManager.userRole == "Manager" || SyncManager.userRole == "TL" || SyncManager.userRole == "Admin")) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onNavigateToAdmin, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F))
                    ) { Text("Go to Management Dashboard", fontWeight = FontWeight.SemiBold) }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.width(4.dp).height(20.dp).background(PrimaryBlue, RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(8.dp))
                    Text("Today Call Log", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1A1A2E))
                    Spacer(Modifier.weight(1f))
                    Surface(shape = RoundedCornerShape(12.dp), color = BlueLight) {
                        Text("${todaysLogs.size} calls", fontSize = 11.sp, color = PrimaryBlue, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by name, company, or number...", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = DividerColor
                    )
                )
            }

            item {
                Spacer(Modifier.height(10.dp))
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(4.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatPill(
                                label = "Total",
                                value = "${todaysLogs.size}",
                                textColor = PrimaryBlue,
                                bgColor = BlueLight,
                                border = if (selectedStatusFilter == null) BorderStroke(1.5.dp, PrimaryBlue) else null,
                                modifier = Modifier.weight(1f),
                                onClick = { selectedStatusFilter = null }
                            )
                            StatPill(
                                label = "Interested",
                                value = "${todaysLogs.count { it.status.equals("Interested", ignoreCase = true) }}",
                                textColor = GreenOk,
                                bgColor = GreenLight,
                                border = if (selectedStatusFilter == "Interested") BorderStroke(1.5.dp, GreenOk) else null,
                                modifier = Modifier.weight(1f),
                                onClick = { selectedStatusFilter = "Interested" }
                            )
                            StatPill(
                                label = "Follow Up",
                                value = "${todaysLogs.count { it.status.equals("Follow Up",  ignoreCase = true) }}",
                                textColor = OrangeOk,
                                bgColor = OrangeLight,
                                border = if (selectedStatusFilter == "Follow Up") BorderStroke(1.5.dp, OrangeOk) else null,
                                modifier = Modifier.weight(1f),
                                onClick = { selectedStatusFilter = "Follow Up" }
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = DividerColor)
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatPill(
                                label = "Enquiry",
                                value = "${todaysLogs.count { it.enquiryReceived.equals("Yes", ignoreCase = true) }}",
                                textColor = GreenOk,
                                bgColor = GreenLight,
                                border = if (selectedStatusFilter == "Enquiry") BorderStroke(1.5.dp, GreenOk) else null,
                                modifier = Modifier.weight(1f),
                                onClick = { selectedStatusFilter = "Enquiry" }
                            )
                            StatPill(
                                label = "No Enquiry",
                                value = "${todaysLogs.count { it.enquiryReceived.equals("No",  ignoreCase = true) }}",
                                textColor = RedOk,
                                bgColor = RedLight,
                                border = if (selectedStatusFilter == "No Enquiry") BorderStroke(1.5.dp, RedOk) else null,
                                modifier = Modifier.weight(1f),
                                onClick = { selectedStatusFilter = "No Enquiry" }
                            )
                            StatPill(
                                label = "Prospect",
                                value = "${todaysLogs.count { it.status.equals("Prospect", ignoreCase = true) }}",
                                textColor = PrimaryBlue,
                                bgColor = BlueLight,
                                border = if (selectedStatusFilter == "Prospect") BorderStroke(1.5.dp, PrimaryBlue) else null,
                                modifier = Modifier.weight(1f),
                                onClick = { selectedStatusFilter = "Prospect" }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (todaysLogs.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No calls recorded", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
                        }
                    }
                }
            } else {
                item {
                    val filterText = when {
                        selectedStatusFilter != null -> "Filtered by: $selectedStatusFilter"
                        else -> "All Calls Logged"
                    }
                    Text(
                        text = filterText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color.DarkGray,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                if (filteredLogs.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                            Text("No matching records found.", color = Color.Gray)
                        }
                    }
                } else {
                    items(items = filteredLogs, key = { it.id }) { log ->
                        CallLogItem(log = log, viewModel = viewModel, uiState = uiState)
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }

    if (showCustomDialog) {
        CustomDateRangeDialog(onDismiss = { showCustomDialog = false },
            onApply = { start, end ->
                selectedFilter = "Custom"
                customLabel = "${df.format(Date(start))}-${df.format(Date(end))}"
                viewModel.setDateRange(start, end)
            }
        )
    }
}

// ─── Follow-Up / Interested quick-redial section ───────────────────────────────
@Composable
fun FollowUpInterestedSection(
    logs: List<CallRecord>,
    onLeadClick: (String) -> Unit
) {
    val context = LocalContext.current

    val followUpLeads = remember(logs) {
        logs.filter { log ->
            log.status.equals("Follow Up", ignoreCase = true) ||
            log.status.equals("Interested", ignoreCase = true)
        }.distinctBy { it.number }
    }

    if (followUpLeads.isEmpty()) return

    Spacer(modifier = Modifier.height(12.dp))
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, OrangeOk.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(18.dp)
                        .background(OrangeOk, RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Follow Up / Interested (${followUpLeads.size})",
                    fontWeight = FontWeight.Bold,
                    color = OrangeOk,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap a card to load number into dialer",
                fontSize = 10.sp,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(10.dp))

            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(followUpLeads) { log ->
                    val isFollowUp = log.status.equals("Follow Up", ignoreCase = true)
                    val cardBg     = if (isFollowUp) OrangeLight else GreenLight
                    val cardBorder = if (isFollowUp) Color(0xFFFFB74D) else Color(0xFF81C784)
                    val textColor  = if (isFollowUp) OrangeOk else GreenOk
                    val statusLabel = if (isFollowUp) "Follow Up" else "Interested"

                    Card(
                        modifier = Modifier
                            .width(170.dp)
                            .clickable {
                                onLeadClick(log.number)
                                android.widget.Toast
                                    .makeText(context, "Loaded ${log.number} to dialer", android.widget.Toast.LENGTH_SHORT)
                                    .show()
                            },
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, cardBorder),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            // Status badge
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = textColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = statusLabel,
                                    fontSize = 9.sp,
                                    color = textColor,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = log.number,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = textColor
                            )
                            val displayName = when {
                                !log.contactName.isNullOrBlank() -> log.contactName
                                !log.name.isNullOrBlank() -> log.name
                                else -> ""
                            }
                            if (displayName.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = displayName,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp,
                                        color = Color(0xFF333333),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (!log.category.isNullOrBlank()) {
                                        Spacer(Modifier.width(6.dp))
                                        Surface(
                                            color = Color(0xFFE0F7FA),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = log.category,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF006064),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(log.date)),
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

@Composable
private fun StatPill(
    label: String,
    value: String,
    textColor: Color,
    bgColor: Color,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
    onClick: () -> Unit = {}
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = border,
        modifier = modifier.clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = textColor)
            Text(label,  fontSize = 9.sp, color = textColor.copy(alpha = 0.75f), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ─── CallLogItem ───────────────────────────────────────────────────────────────
// All editable fields use LOCAL mutable state (initialized once from log.* via
// remember(log.id)) so that the periodic auto-refresh does NOT reset user input.
@Composable
fun CallLogItem(log: CallRecord, viewModel: CallLogViewModel, uiState: UiState) {
    // ── Local editable state – initialized ONCE per unique log id ──────────────
    // These are NOT re-initialized when the parent recomposes due to auto-refresh,
    // because the key (log.id) stays the same. User-typed text is preserved.
    var localContactName  by remember(log.id) { mutableStateOf(log.contactName) }
    var localCompanyName  by remember(log.id) { mutableStateOf(log.companyName) }
    var localAddress      by remember(log.id) { mutableStateOf(log.address) }
    var localEmail        by remember(log.id) { mutableStateOf(log.email) }
    var localDescription  by remember(log.id) { mutableStateOf(log.description) }
    var localStatus       by remember(log.id) { mutableStateOf(log.status) }
    var localFormat       by remember(log.id) { mutableStateOf(log.format) }
    var localEnquiry      by remember(log.id) { mutableStateOf(log.enquiryReceived) }

    // isSaved: sync from server only if the card transitions to saved state
    // (i.e. only allow server to flip false→true, not true→false during editing)
    var justSaved by remember(log.id) { mutableStateOf(log.isSaved) }
    LaunchedEffect(log.isSaved) {
        if (log.isSaved) justSaved = true  // only accept true from server, not false
    }

    var expanded          by remember { mutableStateOf(false) }
    var showEnquiryDialog by remember { mutableStateOf(false) }

    val statusColor = when (localStatus.lowercase()) {
        "interested"     -> GreenOk
        "follow up"      -> OrangeOk
        "not interested" -> RedOk
        "prospect"       -> PurpleOk
        else             -> Color(0xFF78909C)
    }
    val statusBg = when (localStatus.lowercase()) {
        "interested"     -> GreenLight
        "follow up"      -> OrangeLight
        "not interested" -> RedLight
        "prospect"       -> PurpleLight
        else             -> Color(0xFFECEFF1)
    }

    val statuses = listOf("Interested", "Not Interested", "Prospect", "Follow Up")

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(3.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.5.dp, statusColor.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MetaChip("${log.duration}s")
                    MetaChip(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(log.date)))
                }
                Surface(shape = RoundedCornerShape(20.dp), color = statusBg) {
                    Text(localStatus, fontSize = 10.sp, color = statusColor, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
            Text(log.number, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFF1A1A2E), modifier = Modifier.padding(vertical = 6.dp))
            if (localEnquiry.isNotBlank()) {
                val isYes = localEnquiry.equals("Yes", true)
                Surface(shape = RoundedCornerShape(8.dp), color = if (isYes) GreenLight else RedLight) {
                    Text(if (isYes) "Enquiry Received" else "No Enquiry",
                        fontSize = 10.sp, color = if (isYes) GreenOk else RedOk, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
                Spacer(Modifier.height(6.dp))
            }
            if (justSaved) {
                if (localContactName.isNotBlank() || localCompanyName.isNotBlank() || localDescription.isNotBlank()) {
                    HorizontalDivider(color = DividerColor); Spacer(Modifier.height(6.dp))
                    if (localContactName.isNotBlank()) SavedRow("Contact", localContactName)
                    if (localCompanyName.isNotBlank()) SavedRow("Company", localCompanyName)
                    if (localAddress.isNotBlank())     SavedRow("Address", localAddress)
                    if (localEmail.isNotBlank())        SavedRow("Email",   localEmail)
                    if (localDescription.isNotBlank())  SavedRow("Notes",   localDescription)
                }
                Spacer(Modifier.height(4.dp))
                Text("Saved & Synced", color = GreenOk, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                HorizontalDivider(color = DividerColor); Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CrmField("Contact Person", localContactName, true, Modifier.weight(1f)) {
                        localContactName = it
                        viewModel.updateCallDetails(log.id, contactName = it)
                    }
                    CrmField("Company Name", localCompanyName, true, Modifier.weight(1f)) {
                        localCompanyName = it
                        viewModel.updateCallDetails(log.id, companyName = it)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CrmField("Address", localAddress, true, Modifier.weight(1f)) {
                        localAddress = it
                        viewModel.updateCallDetails(log.id, address = it)
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 14.dp)) {
                            Text(localStatus, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = statusColor, fontWeight = FontWeight.SemiBold)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            statuses.forEach { s ->
                                DropdownMenuItem(text = { Text(s) }, onClick = {
                                    if (s == "Interested") {
                                        showEnquiryDialog = true
                                    } else {
                                        localStatus = s
                                        localEnquiry = ""
                                        viewModel.updateCallDetails(log.id, status = s, enquiryReceived = "")
                                    }
                                    expanded = false
                                })
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                var formatExpanded by remember { mutableStateOf(false) }
                val formats = listOf(
                    "General Catalogue Sharing (Format 0005)",
                    "Collaboration Opportunity (Format 0003)",
                    "Revolutionize Your Printing (Format 0002)",
                    "Elevate Your Brand (Format 0004)"
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CrmField("Email", localEmail, true, Modifier.weight(1f)) {
                        localEmail = it
                        viewModel.updateCallDetails(log.id, email = it)
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(onClick = { formatExpanded = true }, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 14.dp)) {
                            Text(localFormat, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        DropdownMenu(expanded = formatExpanded, onDismissRequest = { formatExpanded = false }) {
                            formats.forEach { f ->
                                DropdownMenuItem(text = { Text(f) }, onClick = {
                                    localFormat = f
                                    viewModel.updateCallDetails(log.id, format = f)
                                    formatExpanded = false
                                })
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                CrmField("Description / Notes", localDescription, true, Modifier.fillMaxWidth(), minLines = 2) {
                    localDescription = it
                    viewModel.updateCallDetails(log.id, description = it)
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.saveCallLog(log.id) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    enabled = uiState !is UiState.Loading
                ) {
                    if (uiState is UiState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Save & Sync", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }

    if (showEnquiryDialog) {
        AlertDialog(onDismissRequest = { showEnquiryDialog = false }, shape = RoundedCornerShape(20.dp),
            title = { Text("Enquiry Received?", fontWeight = FontWeight.Bold) },
            text  = { Text("Did you receive an enquiry for this call?") },
            confirmButton = {
                Button(onClick = {
                    localStatus = "Interested"
                    localEnquiry = "Yes"
                    viewModel.updateCallDetails(log.id, status = "Interested", enquiryReceived = "Yes")
                    showEnquiryDialog = false
                },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenOk), shape = RoundedCornerShape(10.dp)) { Text("Yes") }
            },
            dismissButton = {
                Button(onClick = {
                    localStatus = "Follow Up"
                    localEnquiry = "No"
                    viewModel.updateCallDetails(log.id, status = "Follow Up", enquiryReceived = "No")
                    showEnquiryDialog = false
                },
                    colors = ButtonDefaults.buttonColors(containerColor = RedOk), shape = RoundedCornerShape(10.dp)) { Text("No") }
            }
        )
    }
}

@Composable
private fun MetaChip(text: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFF0F4FF)) {
        Text(text, fontSize = 10.sp, color = PrimaryBlue, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
    }
}

@Composable
private fun SavedRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, fontSize = 11.sp, color = Color.Gray, modifier = Modifier.width(80.dp))
        Text(value, fontSize = 11.sp, color = Color(0xFF1A1A2E), fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CrmField(label: String, value: String, enabled: Boolean, modifier: Modifier = Modifier, minLines: Int = 1, onValueChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label, fontSize = 10.sp) },
        modifier = modifier, enabled = enabled, shape = RoundedCornerShape(10.dp), minLines = minLines,
        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp))
}

private fun makeCall(context: Context, number: String) {
    val intent = Intent(Intent.ACTION_CALL).apply { data = Uri.parse("tel:$number") }
    context.startActivity(intent)
}
