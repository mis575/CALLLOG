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
            val systemLogs = repository.getFilteredCallLogs(SyncManager.userEmpId, _startDate.value, _endDate.value)
            if (systemLogs.isEmpty()) {
                _todaysLogs.value = emptyList()
                return@launch
            }

            // Verify which logs are actually in MongoDB
            val savedLogsInDb = SyncManager.fetchSavedLogs(systemLogs.map { it.id })
            val dbMap = savedLogsInDb.associateBy { it.id }
            val assignedMap = _assignedContacts.value.associateBy { it.number }

            // Combine system info with DB info
            val finalLogs = systemLogs.map { systemLog ->
                val dbLog = dbMap[systemLog.id]
                android.util.Log.d("CallLogVM", "systemLog ID: ${systemLog.id}, dbLog ID: ${dbLog?.id}, dbLog isSaved: ${dbLog?.isSaved}")
                dbLog?.let { dbLogObj ->
                    // Explicitly map all fields including Email and Format
                    systemLog.copy(
                        description = dbLogObj.description,
                        contactName = dbLogObj.contactName,
                        companyName = dbLogObj.companyName,
                        address = dbLogObj.address,
                        status = dbLogObj.status,
                        email = dbLogObj.email,
                        format = dbLogObj.format,
                        isSaved = dbLogObj.isSaved,
                        syncedBy = dbLogObj.syncedBy
                    )
                } ?: run {
                    val assigned = assignedMap[systemLog.number]
                    systemLog.copy(
                        isSaved = false,
                        description = "",
                        contactName = assigned?.name ?: "",
                        companyName = "",
                        address = "",
                        status = "Select Status",
                        email = "",
                        format = "Select Format"
                    )
                }
            }
            android.util.Log.d("CallLogVM", "finalLogs: ${finalLogs.map { "${it.id}:${it.isSaved}" }}")
            
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
        format: String? = null
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
                    format = format ?: log.format
                )
            } else log
        }
    }

    fun saveCallLog(log: CallRecord) {
        _uiState.value = UiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val success = SyncManager.syncLogs(listOf(log.copy(isSaved = true)))
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
}
