package com.example.munandydrive.data.remote

import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenManager.getTokenBlocking()
        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        val response = chain.proceed(request)
        // If we sent a token and got 401 back, the token has expired
        if (response.code == 401 && token != null) {
            tokenManager.notifySessionExpired()
        }
        return response
    }
}
