package dev.geetha.chessmatch.data

data class CreateMatchRequest(
    val white: String,
    val black: String,
)

data class MoveRequest(
    val from: String,
    val to: String,
    val promotion: String? = null,
)

data class MoveDto(
    val ply: Int,
    val uci: String,
    val san: String,
    val fen: String,
)

data class MatchDto(
    val id: String,
    val white: String,
    val black: String,
    val status: String,
    val result: String,
    val inCheck: Boolean,
    val fen: String,
    val ply: Int,
    val moves: List<MoveDto>,
    val createdAt: String,
    val updatedAt: String,
)

data class MatchSummaryDto(
    val id: String,
    val white: String,
    val black: String,
    val status: String,
    val result: String,
    val inCheck: Boolean,
    val ply: Int,
    val createdAt: String,
    val updatedAt: String,
)

data class MatchListDto(
    val matches: List<MatchSummaryDto>,
)

data class ErrorBody(
    val error: String? = null,
    val message: String? = null,
)

data class RecentMatch(
    val id: String,
    val white: String,
    val black: String,
    val status: String,
    val result: String,
    val inCheck: Boolean,
    val ply: Int,
    val updatedAt: String,
    val sans: List<String> = emptyList(),
)

sealed class MatchCall<out T> {
    data class Ok<T>(val value: T) : MatchCall<T>()
    data class Failed(val message: String) : MatchCall<Nothing>()
}
