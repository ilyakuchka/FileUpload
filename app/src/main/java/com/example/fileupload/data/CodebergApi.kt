package com.example.fileupload.data

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface CodebergApi {

    @GET("user/repos")
    suspend fun getUserRepos(
        @Query("limit") limit: Int = 50,
        @Query("page") page: Int = 1
    ): List<Repository>

    @GET("repos/{owner}/{repo}/contents/{filepath}")
    suspend fun getContents(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("filepath", encoded = true) filepath: String,
        @Query("ref") ref: String? = null
    ): ContentsResponse

    @POST("repos/{owner}/{repo}/contents/{filepath}")
    suspend fun createFile(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("filepath", encoded = true) filepath: String,
        @Body body: CreateFileOptions
    ): Response<FileResponse>

    @PUT("repos/{owner}/{repo}/contents/{filepath}")
    suspend fun updateFile(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("filepath", encoded = true) filepath: String,
        @Body body: UpdateFileOptions
    ): Response<FileResponse>
}

object ApiClient {
    private const val BASE_URL = "https://codeberg.org/api/v1/"

    fun create(token: String): CodebergApi {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "token $token")
                    .addHeader("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CodebergApi::class.java)
    }
}
