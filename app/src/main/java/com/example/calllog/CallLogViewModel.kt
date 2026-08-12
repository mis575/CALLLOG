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

    fun loadTodaysLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            // 0. Capture current UI state to preserve unsaved edits
            val currentUIStateMap = _todaysLogs.value.associateBy { it.id }

            // 1. Get system call logs from device
            val systemLogs = repository.getFilteredCallLogs(SyncManager.userEmpId, _startDate.value, _endDate.value)

            // 2. Fetch all saved logs for this user from DB using getFilteredLogs
            val dbLogs = SyncManager.getFilteredLogs(
                ids = listOf(SyncManager.userEmpId),
                start = _startDate.value,
                end = _endDate.value,
                includeSubs = false
            )

            // Create maps for efficient lookup
            val dbMap = dbLogs.associateBy { it.id }
            val systemMap = systemLogs.associateBy { it.id }
            val assignedMap = _assignedContacts.value.associateBy { it.number }

            val mergedLogsList = mutableListOf<CallRecord>()

            // Process all system logs (and update with DB state if saved)
            systemLogs.forEach { systemLog ->
                val dbLog = dbMap[systemLog.id]
                val currentLocalLog = currentUIStateMap[systemLog.id]

                if (dbLog != null) {
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
                            syncedBy = dbLog.syncedBy,
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
                            enquiryReceived = currentLocalLog.enquiryReceived
                        )
                    )
                } else {
                    // Not saved yet, check if it's an assigned lead
                    val assigned = assignedMap[systemLog.number]
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
                            enquiryReceived = ""
                        )
                    )
                }
            }

            // Next, add any database logs that are NOT in the system call logs
            dbLogs.forEach { dbLog ->
                if (!systemMap.containsKey(dbLog.id)) {
                    mergedLogsList.add(dbLog)
                }
            }

            // Sort merged list by date descending so newest calls are first
            val finalLogs = mergedLogsList.sortedByDescending { it.date }
            
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
        enquiryReceived: String? = null
    ) {
        _todaysLogs.value = _todaysLogs.value.map { log ->
            if (log.id == callId) {
                log.copy(
                    description = description ?: log.description,
                    contactName = contactName ?: log.contactName,
                    companyName = companyName ?: log.companyName,
                    address = address ?: log.address,
                    status = status ?: log.status,
                    email = email ?: log.email,
                    format = format ?: log.format,
                    enquiryReceived = enquiryReceived ?: log.enquiryReceived
                )
            } else log
        }
    }

    fun saveCallLog(logId: String) {
        // Always look up the LATEST version of the log from state — never use the stale composable parameter
        val latestLog = _todaysLogs.value.find { it.id == logId } ?: return
        _uiState.value = UiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val success = SyncManager.syncLogs(listOf(latestLog.copy(isSaved = true)))
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
            loadTodaysLogs()
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
}
