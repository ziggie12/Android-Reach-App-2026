package com.example.munandydrive.data.remote

import com.example.munandydrive.data.model.CreateCustomerRequest
import com.example.munandydrive.data.model.Customer
import com.example.munandydrive.data.model.DriverLocation
import com.example.munandydrive.data.model.LoginRequest
import com.example.munandydrive.data.model.LoginResponse
import com.example.munandydrive.data.model.MutationResponse
import com.example.munandydrive.data.model.PostLocationRequest
import com.example.munandydrive.data.model.AddStopsRequest
import com.example.munandydrive.data.model.Route
import com.example.munandydrive.data.model.StatsResponse
import com.example.munandydrive.data.model.SyncedRoute
import com.example.munandydrive.data.model.ReportsSummary
import com.example.munandydrive.data.model.UpdateStopStatusRequest
import com.example.munandydrive.data.model.UploadPodRequest
import com.example.munandydrive.data.model.UploadPodResponse
import com.example.munandydrive.data.model.User
import okhttp3.MultipartBody
import retrofit2.http.*

interface MunandyApiService {

    // ── Auth ─────────────────────────────────────────────────────────────────
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("me")
    suspend fun verifyToken(): User

    // ── Users ────────────────────────────────────────────────────────────────
    @GET("users")
    suspend fun getUsers(): List<User>

    @POST("users")
    suspend fun createUser(@Body body: Map<String, String>): MutationResponse

    @PUT("users/{id}/status")
    suspend fun updateUserStatus(
        @Path("id") userId: Int,
        @Body body: Map<String, Boolean>
    ): MutationResponse

    // ── Customers ────────────────────────────────────────────────────────────
    @GET("customers")
    suspend fun getCustomers(): List<Customer>

    @POST("customers")
    suspend fun createCustomer(@Body body: CreateCustomerRequest): MutationResponse

    // ── Routes ───────────────────────────────────────────────────────────────
    @GET("routes")
    suspend fun getRoutes(): List<Route>

    @POST("routes")
    suspend fun createRoute(@Body body: Map<String, String>): MutationResponse

    @POST("routes/{id}/stops")
    suspend fun addStops(
        @Path("id") routeId: Int,
        @Body body: AddStopsRequest
    ): MutationResponse

    @PATCH("routes/{id}/status")
    suspend fun updateRouteStatus(
        @Path("id") routeId: Int,
        @Body body: Map<String, String>
    ): MutationResponse

    @PATCH("routes/{id}/assign")
    suspend fun assignRoute(
        @Path("id") routeId: Int,
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): MutationResponse

    @DELETE("routes/{id}")
    suspend fun deleteRoute(@Path("id") routeId: Int): MutationResponse

    @DELETE("routes/{routeId}/stops/{stopId}")
    suspend fun deleteStop(
        @Path("routeId") routeId: Int,
        @Path("stopId") stopId: Int
    ): MutationResponse

    // ── Stats ────────────────────────────────────────────────────────────────
    @GET("stats")
    suspend fun getStats(): StatsResponse

    // ── Live Tracking ────────────────────────────────────────────────────────
    @GET("tracking/locations/latest")
    suspend fun getActiveDriverLocations(): List<DriverLocation>

    @POST("tracking/locations")
    suspend fun postLocation(@Body request: PostLocationRequest): MutationResponse

    // ── Offline Sync ─────────────────────────────────────────────────────────
    @GET("sync/routes/{id}")
    suspend fun syncRoute(@Path("id") routeId: Int): SyncedRoute

    @PATCH("routes/{routeId}/stops/{stopId}/status")
    suspend fun updateStopStatus(
        @Path("routeId") routeId: Int,
        @Path("stopId") stopId: Int,
        @Body request: UpdateStopStatusRequest
    ): MutationResponse

    // ── Edit Customer ────────────────────────────────────────────────────────
    @PUT("customers/{id}")
    suspend fun updateCustomer(
        @Path("id") customerId: Int,
        @Body body: CreateCustomerRequest
    ): MutationResponse

    // ── Delete Customer ──────────────────────────────────────────────────────
    @DELETE("customers/{id}")
    suspend fun deleteCustomer(@Path("id") customerId: Int): MutationResponse

    // ── Change / Reset Password ──────────────────────────────────────────────
    @PATCH("users/me/password")
    suspend fun changePassword(@Body body: Map<String, String>): MutationResponse

    @PATCH("users/{id}/reset-password")
    suspend fun resetUserPassword(
        @Path("id") userId: Int,
        @Body body: Map<String, String>
    ): MutationResponse

    // ── Proof of Delivery ────────────────────────────────────────────────────
    @Multipart
    @POST("routes/{routeId}/stops/{stopId}/pod")
    suspend fun uploadPod(
        @Path("routeId") routeId: Int,
        @Path("stopId") stopId: Int,
        @Part photo: MultipartBody.Part
    ): UploadPodResponse

    // ── Reports ──────────────────────────────────────────────────────────────
    @GET("reports/summary")
    suspend fun getReportsSummary(): ReportsSummary
}
