package com.example.calllog.network

import com.example.calllog.data.CallRecord
import com.example.calllog.data.DeleteUserRequest
import com.example.calllog.data.GetLogsRequest
import com.example.calllog.data.GetTeamLogsRequest
import com.example.calllog.data.LoginRequest
import com.example.calllog.data.LoginResponse
import com.example.calllog.data.RegisterRequest
import com.example.calllog.data.SyncRequest
import com.example.calllog.data.TeamMember
import com.example.calllog.data.UpdateHierarchyRequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface UnifiedSyncApiService {
    @POST("login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>

    @POST("createUser")
    suspend fun createUser(
        @Body request: RegisterRequest
    ): Response<LoginResponse>

    @POST("register")
    suspend fun register(
        @Body request: RegisterRequest
    ): Response<LoginResponse>

    @GET("getAllUsers")
    suspend fun getAllUsers(): Response<List<TeamMember>>

    @POST("updateUserHierarchy")
    suspend fun updateUserHierarchy(
        @Body request: UpdateHierarchyRequest
    ): Response<LoginResponse>

    @POST("deleteUser")
    suspend fun deleteUser(
        @Body request: DeleteUserRequest
    ): Response<LoginResponse>

    @GET("getMembers/{empId}")
    suspend fun getMembers(
        @Path("empId") empId: String
    ): Response<List<TeamMember>>

    @POST("getFilteredLogs")
    suspend fun getFilteredLogs(
        @Body request: GetTeamLogsRequest
    ): Response<List<CallRecord>>

    @POST("getLogs")
    suspend fun getSavedLogs(
        @Body request: GetLogsRequest
    ): Response<List<CallRecord>>

    @POST("sync")
    suspend fun syncCallLogs(
        @Body request: SyncRequest
    ): Response<ResponseBody>
}
