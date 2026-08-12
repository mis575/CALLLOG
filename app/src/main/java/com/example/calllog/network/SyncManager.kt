package com.example.calllog.network

import android.content.Context
import com.example.calllog.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

import java.util.concurrent.TimeUnit

object SyncManager {
    private const val BACKEND_BASE_URL = "https://call-log-qaq7.onrender.com/"
    private const val PREFS_NAME = "CRM_Prefs"
    
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn
    
    var userName: String = ""
    var userRole: String = "Executive"
    var userEmpId: String = ""
 
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        .build()

    private val api = Retrofit.Builder()
        .baseUrl(BACKEND_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(UnifiedSyncApiService::class.java)

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        userName = prefs.getString("name", "") ?: ""
        userRole = prefs.getString("role", "Executive") ?: "Executive"
        userEmpId = prefs.getString("empId", "") ?: ""
        _isLoggedIn.value = userName.isNotEmpty()
    }

    suspend fun login(context: Context, request: LoginRequest): LoginResponse {
        return try {
            val response = api.login(request)
            if (response.isSuccessful && response.body()?.success == true) {
                val body = response.body()!!
                userName = body.userName ?: ""
                userRole = body.role ?: "Executive"
                userEmpId = body.empId ?: ""
                
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putString("name", userName).putString("role", userRole).putString("empId", userEmpId).apply()
                
                _isLoggedIn.value = true
                body
            } else LoginResponse(false, "Login Failed")
        } catch (e: Exception) { LoginResponse(false, e.message ?: "Error") }
    }

    suspend fun register(request: RegisterRequest): Boolean {
        return try { api.register(request).isSuccessful } catch (e: Exception) { false }
    }

    fun logout(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        userName = ""
        userRole = "Executive"
        userEmpId = ""
        _isLoggedIn.value = false
    }

    suspend fun createUser(request: RegisterRequest): Boolean {
        return try { api.createUser(request).isSuccessful } catch (e: Exception) { false }
    }

    suspend fun getAllUsers(): List<TeamMember> {
        return try { api.getAllUsers().body() ?: emptyList() } catch (e: Exception) { emptyList() }
    }

    suspend fun updateUserHierarchy(request: UpdateHierarchyRequest): Boolean {
        return try { api.updateUserHierarchy(request).isSuccessful } catch (e: Exception) { false }
    }

    suspend fun deleteUser(empId: String): Boolean {
        return try { api.deleteUser(DeleteUserRequest(empId)).isSuccessful } catch (e: Exception) { false }
    }

    suspend fun getMembers(targetId: String? = null): List<TeamMember> {
        val id = targetId ?: userEmpId
        return try { api.getMembers(id).body() ?: emptyList() } catch (e: Exception) { emptyList() }
    }

    suspend fun getFilteredLogs(ids: List<String>, start: Long? = null, end: Long? = null, includeSubs: Boolean = false): List<CallRecord> {
        return try { 
            api.getFilteredLogs(GetTeamLogsRequest(ids, start, end, includeSubs)).body() ?: emptyList() 
        } catch (e: Exception) { emptyList() }
    }

    suspend fun fetchSavedLogs(ids: List<String>): List<CallRecord> {
        return try { api.getSavedLogs(GetLogsRequest(ids)).body() ?: emptyList() } catch (e: Exception) { emptyList() }
    }

    suspend fun syncLogs(logs: List<CallRecord>): Boolean {
        var attempts = 3
        while (attempts > 0) {
            try {
                val response = api.syncCallLogs(SyncRequest(userName, userEmpId, logs))
                if (response.isSuccessful) return true
            } catch (e: Exception) {
                // Ignore transient errors and retry
            }
            attempts--
            if (attempts > 0) {
                kotlinx.coroutines.delay(1500)
            }
        }
        return false
    }

    suspend fun getTeamMembersForTransfer(empId: String): List<TeamMember> {
        return try { api.getTeamMembersForTransfer(empId).body() ?: emptyList() } catch (e: Exception) { emptyList() }
    }

    suspend fun assignContacts(leads: List<com.example.calllog.data.LeadInfo>, assignedTo: String, assignedBy: String): Boolean {
        return try {
            val response = api.assignContacts(com.example.calllog.data.ContactAssignmentRequest(leads = leads, assignedTo = assignedTo, assignedBy = assignedBy))
            response.isSuccessful && response.body()?.success == true
        } catch (e: Exception) { false }
    }

    suspend fun getAssignedContacts(empId: String): List<AssignedContact> {
        return try { api.getAssignedContacts(empId).body() ?: emptyList() } catch (e: Exception) { emptyList() }
    }

    suspend fun getTeamLeads(empId: String): List<AssignedContact> {
        return try { api.getTeamLeads(empId).body() ?: emptyList() } catch (e: Exception) { emptyList() }
    }

    suspend fun getLatestVersion(): VersionInfo? {
        return try { api.getVersionInfo().body() } catch (e: Exception) { null }
    }

    suspend fun syncLeadsFromSheet(): AssignResponse {
        return try {
            val response = api.syncLeadsFromSheet()
            if (response.isSuccessful && response.body() != null) {
                response.body()!!
            } else AssignResponse(false, "Server error")
        } catch (e: Exception) { AssignResponse(false, e.message ?: "Error") }
    }
}
