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
    val startDate: StateFlow<Long?> = _startDate.asStateFlow()

    private val _endDate = MutableStateFlow<Long?>(null)
    val endDate: StateFlow<Long?> = _endDate.asStateFlow()

    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    init {
        // Default to All for reports
        _startDate.value = null
        _endDate.value = null
        loadCategories()
    }

    fun loadCategories() {
        viewModelScope.launch {
            val list = SyncManager.getCategories()
            _categories.value = list
        }
    }

    fun addCategory(name: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val success = SyncManager.addCategory(name)
            if (success) {
                _uiState.value = UiState.Success("Category added successfully")
                loadCategories()
                onResult(true)
            } else {
                _uiState.value = UiState.Error("Failed to add category")
                onResult(false)
            }
        }
    }

    fun updateUserCategory(empId: String, category: String, refreshId: String? = null) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val success = SyncManager.updateUserCategory(empId, category)
            if (success) {
                _uiState.value = UiState.Success("User category updated")
                loadAllUsers()
                if (refreshId != null) {
                    loadMembers(refreshId)
                }
            } else {
                _uiState.value = UiState.Error("Failed to update user category")
            }
        }
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

    fun assignContacts(leads: List<com.example.calllog.data.LeadInfo>, assignedTo: String, assignedBy: String, onResult: (Boolean) -> Unit) {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            val success = SyncManager.assignContacts(leads, assignedTo, assignedBy)
            if (success) {
                _uiState.value = UiState.Success("Successfully assigned ${leads.size} contacts")
                onResult(true)
            } else {
                _uiState.value = UiState.Error("Failed to assign contacts")
                onResult(false)
            }
        }
    }

    fun exportToCsv(context: Context): String? {
        if (_reports.value.isEmpty()) return null
        val csv = StringBuilder("ID,User,Number,Caller,Type,Date,Duration,Notes,Contact,Company,Address,Status,Enquiry Received\n")
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        _reports.value.forEach { l ->
            csv.append("${l.id},${l.syncedBy ?: "Unknown"},${l.number},${l.name ?: "Unknown"},${l.type},${df.format(Date(l.date))},${l.duration}s,${l.description.replace(",", " ")},${l.contactName.replace(",", " ")},${l.companyName.replace(",", " ")},${l.address.replace(",", " ")},${l.status},${l.enquiryReceived}\n")
        }

        val filename = "Report_${System.currentTimeMillis()}.csv"

        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri).use { outputStream ->
                        outputStream?.write(csv.toString().toByteArray())
                    }
                    "Downloads/$filename"
                } else null
            } else {
                @Suppress("DEPRECATION")
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadDir.exists()) downloadDir.mkdirs()
                val file = File(downloadDir, filename)
                FileOutputStream(file).use { it.write(csv.toString().toByteArray()) }
                file.absolutePath
            }
        } catch (e: Exception) {
            android.util.Log.e("AdminVM", "Export error: ${e.message}", e)
            null
        }
    }

    fun syncLeadsFromSheet(context: Context) {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val res = SyncManager.syncLeadsFromSheet()
                if (res.success) {
                    _uiState.value = UiState.Success(res.message)
                    loadTeamLeads(SyncManager.userEmpId)
                } else {
                    _uiState.value = UiState.Error(res.message)
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Sync failed: ${e.localizedMessage}")
            }
        }
    }

    fun removeLead(number: String, currentBrowsingId: String) {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            val success = SyncManager.removeLead(number, SyncManager.userEmpId)
            if (success) {
                _uiState.value = UiState.Success("Lead removed successfully")
                loadTeamLeads(currentBrowsingId)
            } else {
                _uiState.value = UiState.Error("Failed to remove lead")
            }
        }
    }
}
