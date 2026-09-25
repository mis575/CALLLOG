package com.example.calllog

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calllog.data.*
import com.example.calllog.network.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

class CallLogViewModel(private val context: Context) : ViewModel() {

    private val repository = CallLogRepository(context)
    private val _uiState = MutableStateFlow<UiState>(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _todaysLogs = MutableStateFlow<List<CallRecord>>(emptyList())
    val todaysLogs: StateFlow<List<CallRecord>> = _todaysLogs.asStateFlow()

    private val _startDate = MutableStateFlow<Long?>(null)
    private val _endDate = MutableStateFlow<Long?>(null)

    init {
        // Default to Today
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        _startDate.value = cal.timeInMillis
        _endDate.value = System.currentTimeMillis()
        
        loadTodaysLogs()
    }

    fun setDateRange(start: Long?, end: Long?) {
        _startDate.value = start
        _endDate.value = end
        loadTodaysLogs()
    }

    private fun cleanNumber(num: String): String {
        val digits = num.filter { it.isDigit() }
        return if (digits.length >= 10) digits.takeLast(10) else digits
    }

    fun loadTodaysLogs(ignoreDates: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            // 0. Capture current UI state to preserve unsaved edits
            val currentUIStateMap = _todaysLogs.value.associateBy { it.id }

            val startVal = if (ignoreDates) null else _startDate.value
            val endVal = if (ignoreDates) null else _endDate.value

            // 1. Get system call logs from device
            val systemLogs = repository.getFilteredCallLogs(SyncManager.userEmpId, startVal, endVal)

            // 2. Fetch all saved logs for this user from DB using getFilteredLogs
            val dbLogs = SyncManager.getFilteredLogs(
                ids = listOf(SyncManager.userEmpId),
                start = startVal,
                end = endVal,
                includeSubs = false
            )

            // Create maps for efficient lookup
            val dbMap = dbLogs.associateBy { it.id }
            val systemMap = systemLogs.associateBy { it.id }

            val mergedLogsList = mutableListOf<CallRecord>()
            val matchedDbIds = mutableSetOf<String>()

            // Process all system logs (and update with DB state if saved)
            systemLogs.forEach { systemLog ->
                var dbLog = dbMap[systemLog.id]
                val currentLocalLog = currentUIStateMap[systemLog.id]
                
                // Fuzzy matching if not found by exact ID (same number within 10 minutes)
                if (dbLog == null) {
                    val tenMinsMs = 10 * 60 * 1000
                    dbLog = dbLogs.find {
                        cleanNumber(it.number) == cleanNumber(systemLog.number) &&
                        Math.abs(it.date - systemLog.date) <= tenMinsMs
                    }
                }

                if (dbLog != null) {
                    matchedDbIds.add(dbLog.id)
                    // Update system log with database values
                    mergedLogsList.add(
                        systemLog.copy(
                            description = dbLog.description,
                            contactName = dbLog.contactName,
                            companyName = dbLog.companyName,
                            address = dbLog.address,
                            status = dbLog.status,
                            email = dbLog.email,
                            format = dbLog.format,
                            isSaved = dbLog.isSaved,
                            syncedBy = if (!dbLog.syncedBy.isNullOrBlank()) dbLog.syncedBy else SyncManager.userName,
                            syncedByEmpId = if (!dbLog.syncedByEmpId.isNullOrBlank()) dbLog.syncedByEmpId else SyncManager.userEmpId,
                            enquiryReceived = dbLog.enquiryReceived
                        )
                    )
                } else if (currentLocalLog != null && !currentLocalLog.isSaved) {
                    // PRESERVE local edits if not saved in DB yet
                    mergedLogsList.add(
                        systemLog.copy(
                            isSaved = false,
                            description = currentLocalLog.description,
                            contactName = currentLocalLog.contactName,
                            companyName = currentLocalLog.companyName,
                            address = currentLocalLog.address,
                            status = currentLocalLog.status,
                            email = currentLocalLog.email,
                            format = currentLocalLog.format,
                            syncedBy = SyncManager.userName,
                            syncedByEmpId = SyncManager.userEmpId,
                            enquiryReceived = currentLocalLog.enquiryReceived
                        )
                    )
                } else {
                    // Not saved yet — preserve any in-progress edits the user has already typed
                    // so that auto-refresh doesn't wipe their unsaved work
                    val inProgress = _todaysLogs.value.find { it.id == systemLog.id }
                    val cleanSysNum = cleanNumber(systemLog.number)
                    val assigned = _assignedContacts.value.find { cleanNumber(it.number) == cleanSysNum }
                    if (inProgress != null && !inProgress.isSaved) {
                        // User has already started editing this log — keep their current edits
                        mergedLogsList.add(inProgress.copy(syncedBy = SyncManager.userName, syncedByEmpId = SyncManager.userEmpId))
                    } else {
                        mergedLogsList.add(
                            systemLog.copy(
                                isSaved = false,
                                description = "",
                                contactName = assigned?.name ?: "",
                                companyName = "",
                                address = "",
                                status = "Select Status",
                                email = "",
                                format = "Select Format",
                                syncedBy = SyncManager.userName,
                                syncedByEmpId = SyncManager.userEmpId,
                                enquiryReceived = ""
                            )
                        )
                    }
                }
            }

            // Next, add any database logs that are NOT in the system call logs and weren't matched
            dbLogs.forEach { dbLog ->
                if (!matchedDbIds.contains(dbLog.id) && !systemMap.containsKey(dbLog.id)) {
                    mergedLogsList.add(
                        dbLog.copy(
                            syncedBy = if (!dbLog.syncedBy.isNullOrBlank()) dbLog.syncedBy else SyncManager.userName,
                            syncedByEmpId = if (!dbLog.syncedByEmpId.isNullOrBlank()) dbLog.syncedByEmpId else SyncManager.userEmpId
                        )
                    )
                }
            }

            // Filter out 0s duration / unconnected calls from call log list in app, and sort by date descending
            val finalLogs = mergedLogsList
                .filter { it.effectiveDuration > 0 }
                .sortedByDescending { it.date }
            
            android.util.Log.d("CallLogVM", "Merged call logs count: ${finalLogs.size}")
            _todaysLogs.value = finalLogs
        }
    }

    fun updateCallDetails(
        callId: String,
        description: String? = null,
        contactName: String? = null,
        companyName: String? = null,
        address: String? = null,
        status: String? = null,
        email: String? = null,
        format: String? = null,
        enquiryReceived: String? = null,
        followUpDate: String? = null
    ) {
        _todaysLogs.value = _todaysLogs.value.map { log ->
            if (log.id == callId) {
                val newStatus = status ?: log.status
                val statusLower = newStatus.lowercase().trim()
                val isUnconnected = statusLower == "busy" || statusLower == "not answering" || statusLower == "no answer" || statusLower == "missed" || statusLower == "rejected" || statusLower == "unconnected"
                val newDuration = if (isUnconnected) 0L else log.duration

                log.copy(
                    description = description ?: log.description,
                    contactName = contactName ?: log.contactName,
                    companyName = companyName ?: log.companyName,
                    address = address ?: log.address,
                    status = newStatus,
                    duration = newDuration,
                    email = email ?: log.email,
                    format = format ?: log.format,
                    enquiryReceived = enquiryReceived ?: log.enquiryReceived,
                    followUpDate = followUpDate ?: log.followUpDate
                )
            } else log
        }
    }

    fun saveCallLog(logId: String) {
        // Always look up the LATEST version of the log from state — never use the stale composable parameter
        val latestLog = _todaysLogs.value.find { it.id == logId } ?: return
        val statusLower = latestLog.status.lowercase().trim()
        val isUnconnected = statusLower == "busy" || statusLower == "not answering" || statusLower == "no answer" || statusLower == "missed" || statusLower == "rejected" || statusLower == "unconnected"
        val logToSync = if (isUnconnected) latestLog.copy(duration = 0L, isSaved = true) else latestLog.copy(isSaved = true)
        _uiState.value = UiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val success = SyncManager.syncLogs(listOf(logToSync))
                if (success) {
                    _uiState.value = UiState.Success("Saved and synced!")
                    loadTodaysLogs() // Refresh to lock UI
                    loadAssignedContacts() // Refresh pending assigned leads
                } else {
                    _uiState.value = UiState.Error("Sync failed.")
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Error: ${e.localizedMessage}")
            }
        }
    }

    private val _assignedContacts = MutableStateFlow<List<AssignedContact>>(emptyList())
    val assignedContacts: StateFlow<List<AssignedContact>> = _assignedContacts.asStateFlow()

    fun loadAssignedContacts() {
        viewModelScope.launch(Dispatchers.IO) {
            _assignedContacts.value = SyncManager.getAssignedContacts(SyncManager.userEmpId)
        }
    }

    fun login(request: LoginRequest, onResult: (Boolean) -> Unit) {
        _uiState.value = UiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            val response = SyncManager.login(context, request)
            if (response.success) {
                _uiState.value = UiState.Success("LOGIN_SUCCESS")
                onResult(true)
            } else {
                _uiState.value = UiState.Error(response.message)
                onResult(false)
            }
        }
    }

    fun register(request: RegisterRequest) {
        _uiState.value = UiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            if (SyncManager.register(request)) {
                _uiState.value = UiState.Success("REGISTER_SUCCESS")
            } else {
                _uiState.value = UiState.Error("Registration failed")
            }
        }
    }

    fun getContactDetails(number: String, onResult: (CallRecord?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val details = SyncManager.getContactDetails(number)
            onResult(details)
        }
    }

    private val _contactHistory = MutableStateFlow<List<CallRecord>>(emptyList())
    val contactHistory: StateFlow<List<CallRecord>> = _contactHistory.asStateFlow()

    fun loadContactHistory(number: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _contactHistory.value = SyncManager.getContactHistory(number)
        }
    }
}
