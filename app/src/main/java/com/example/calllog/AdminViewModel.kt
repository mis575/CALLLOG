package com.example.calllog

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calllog.data.*
import com.example.calllog.network.SyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class AdminViewModel : ViewModel() {
    private val _members = MutableStateFlow<List<TeamMember>>(emptyList())
    val members: StateFlow<List<TeamMember>> = _members.asStateFlow()

    private val _allUsers = MutableStateFlow<List<TeamMember>>(emptyList())
    val allUsers: StateFlow<List<TeamMember>> = _allUsers.asStateFlow()

    private val _reports = MutableStateFlow<List<CallRecord>>(emptyList())
    val reports: StateFlow<List<CallRecord>> = _reports.asStateFlow()

    private val _teamLeadsData = MutableStateFlow<List<AssignedContact>>(emptyList())
    val teamLeadsData: StateFlow<List<AssignedContact>> = _teamLeadsData.asStateFlow()

    private val _uiState = MutableStateFlow<UiState>(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _startDate = MutableStateFlow<Long?>(null)
    private val _endDate = MutableStateFlow<Long?>(null)

    init {
        // Default to Today for reports
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        _startDate.value = cal.timeInMillis
        _endDate.value = System.currentTimeMillis()
    }

    fun setDateRange(start: Long?, end: Long?) {
        _startDate.value = start
        _endDate.value = end
    }

    fun loadMembers(targetEmpId: String? = null) {
        viewModelScope.launch {
            _members.value = SyncManager.getMembers(targetEmpId)
        }
    }

    fun loadAllUsers() {
        viewModelScope.launch {
            _allUsers.value = SyncManager.getAllUsers()
        }
    }

    fun loadReports(ids: List<String>, includeSubs: Boolean = false) {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            _reports.value = SyncManager.getFilteredLogs(ids, _startDate.value, _endDate.value, includeSubs)
            _uiState.value = UiState.Success("Loaded")
        }
    }

    fun fetchAndExport(context: Context, ids: List<String>, includeSubs: Boolean = false) {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            val logs = SyncManager.getFilteredLogs(ids, _startDate.value, _endDate.value, includeSubs)
            _reports.value = logs
            if (logs.isNotEmpty()) {
                val path = exportToCsv(context)
                if (path != null) _uiState.value = UiState.Success("Exported: $path")
                else _uiState.value = UiState.Error("Export failed")
            } else {
                _uiState.value = UiState.Error("No data to export")
            }
        }
    }

    fun createUser(req: RegisterRequest, refreshId: String? = null) {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            if (SyncManager.createUser(req)) {
                loadMembers(refreshId)
                loadAllUsers() // Keep global list in sync too
                _uiState.value = UiState.Success("User Created")
            } else _uiState.value = UiState.Error("Creation Failed")
        }
    }

    fun updateHierarchy(empId: String, role: String, reportsTo: String, refreshId: String? = null) {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            if (SyncManager.updateUserHierarchy(UpdateHierarchyRequest(empId, role, reportsTo))) {
                loadAllUsers()
                loadMembers(refreshId) // Also refresh team members
                _uiState.value = UiState.Success("Updated")
            } else _uiState.value = UiState.Error("Update Failed")
        }
    }

    fun deleteUser(empId: String, refreshId: String? = null) {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            if (SyncManager.deleteUser(empId)) {
                loadAllUsers()
                loadMembers(refreshId)
                _uiState.value = UiState.Success("Deleted")
            } else _uiState.value = UiState.Error("Delete Failed")
        }
    }

    private val _transferMembers = MutableStateFlow<List<TeamMember>>(emptyList())
    val transferMembers: StateFlow<List<TeamMember>> = _transferMembers.asStateFlow()

    fun loadTransferMembers(empId: String) {
        viewModelScope.launch {
            _transferMembers.value = SyncManager.getTeamMembersForTransfer(empId)
        }
    }

    fun loadTeamLeads(empId: String) {
        viewModelScope.launch {
            _teamLeadsData.value = SyncManager.getTeamLeads(empId)
        }
    }

    fun assignContacts(numbers: List<String>, assignedTo: String, assignedBy: String, onResult: (Boolean) -> Unit) {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            val success = SyncManager.assignContacts(numbers, assignedTo, assignedBy)
            if (success) {
                _uiState.value = UiState.Success("Successfully assigned ${numbers.size} contacts")
                onResult(true)
            } else {
                _uiState.value = UiState.Error("Failed to assign contacts")
                onResult(false)
            }
        }
    }

    fun exportToCsv(context: Context): String? {
        if (_reports.value.isEmpty()) return null
        val csv = StringBuilder("ID,User,Number,Caller,Type,Date,Duration,Notes,Contact,Company,Address,Status\n")
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        _reports.value.forEach { l ->
            csv.append("${l.id},${l.syncedBy ?: "Unknown"},${l.number},${l.name ?: "Unknown"},${l.type},${df.format(Date(l.date))},${l.duration}s,${l.description},${l.contactName},${l.companyName},${l.address},${l.status}\n")
        }
        return try {
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Report_${System.currentTimeMillis()}.csv")
            FileOutputStream(file).use { it.write(csv.toString().toByteArray()) }
            file.absolutePath
        } catch (e: Exception) { null }
    }
}
