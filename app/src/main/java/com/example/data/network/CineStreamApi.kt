package com.example.data.network

import com.example.data.model.Movie
import com.example.data.model.ServerStatusResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface CineStreamApi {
    @GET("api/movies")
    suspend fun getMovies(
        @Query("genero") genre: String? = null,
        @Query("search") search: String? = null,
        @Query("destacadas") featured: Boolean? = null
    ): List<Movie>

    @GET("api/movies/{id}")
    suspend fun getMovieDetail(
        @Path("id") id: Int
    ): Movie

    @GET("api/genres")
    suspend fun getGenres(): List<String>

    @GET("api/status")
    suspend fun getServerStatus(): ServerStatusResponse

    @GET("api/live/channels")
    suspend fun getLiveChannels(): List<com.example.data.model.LiveChannel>
}

object NetworkClient {
    private var currentBaseUrl: String = "http://10.0.2.2:8000/" // Default para Android Emulator

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private var retrofitInstance: Retrofit? = null
    private var apiServiceInstance: CineStreamApi? = null

    fun getApi(baseUrl: String = currentBaseUrl): CineStreamApi {
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        if (apiServiceInstance == null || normalizedUrl != currentBaseUrl) {
            currentBaseUrl = normalizedUrl
            retrofitInstance = Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
            apiServiceInstance = retrofitInstance!!.create(CineStreamApi::class.java)
        }
        return apiServiceInstance!!
    }

    fun getBaseUrl(): String = currentBaseUrl
}
