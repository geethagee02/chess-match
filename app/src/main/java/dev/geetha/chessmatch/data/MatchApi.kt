package dev.geetha.chessmatch.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface MatchApi {
    @POST("matches")
    suspend fun create(@Body body: CreateMatchRequest): MatchDto

    @GET("matches")
    suspend fun recent(@Query("limit") limit: Int = 20): MatchListDto

    @GET("matches/{id}")
    suspend fun match(@Path("id") id: String): MatchDto

    @POST("matches/{id}/moves")
    suspend fun move(@Path("id") id: String, @Body body: MoveRequest): MatchDto
}
