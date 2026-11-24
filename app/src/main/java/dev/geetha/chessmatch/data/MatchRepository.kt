package dev.geetha.chessmatch.data

import com.squareup.moshi.Moshi
import dev.geetha.chessmatch.rules.Move
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MatchRepository @Inject constructor(
    private val api: MatchApi,
    private val moshi: Moshi,
) {
    suspend fun create(): MatchCall<String> = call {
        api.create(CreateMatchRequest(white = "Geetha", black = "Device")).id
    }

    suspend fun move(id: String, move: Move): MatchCall<Unit> = call {
        api.move(
            id,
            MoveRequest(
                from = move.from.algebraic(),
                to = move.to.algebraic(),
                promotion = move.promotion?.letter?.toString(),
            ),
        )
        Unit
    }

    suspend fun recent(): MatchCall<List<RecentMatch>> = call {
        api.recent().matches.map { match ->
            RecentMatch(
                id = match.id,
                white = match.white,
                black = match.black,
                status = match.status,
                result = match.result,
                inCheck = match.inCheck,
                ply = match.ply,
                updatedAt = match.updatedAt,
            )
        }
    }

    suspend fun detail(id: String): MatchCall<RecentMatch> = call {
        val match = api.match(id)
        RecentMatch(
            id = match.id,
            white = match.white,
            black = match.black,
            status = match.status,
            result = match.result,
            inCheck = match.inCheck,
            ply = match.ply,
            updatedAt = match.updatedAt,
            sans = match.moves.map { it.san },
        )
    }

    private suspend fun <T> call(block: suspend () -> T): MatchCall<T> {
        try {
            return MatchCall.Ok(block())
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (http: HttpException) {
            return MatchCall.Failed(httpMessage(http))
        } catch (io: IOException) {
            return MatchCall.Failed("The match server is not reachable.")
        } catch (error: Exception) {
            return MatchCall.Failed("The match server sent a response the app could not read.")
        }
    }

    private fun httpMessage(http: HttpException): String {
        val raw = http.response()?.errorBody()?.string().orEmpty()
        val parsed = runCatching { moshi.adapter(ErrorBody::class.java).fromJson(raw) }.getOrNull()
        return parsed?.message ?: "The server rejected that request (${http.code()})."
    }
}
