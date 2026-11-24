package dev.geetha.chessmatch.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.geetha.chessmatch.data.MatchCall
import dev.geetha.chessmatch.data.MatchRepository
import dev.geetha.chessmatch.data.RecentMatch
import dev.geetha.chessmatch.rules.DeviceOpponent
import dev.geetha.chessmatch.rules.GameStatus
import dev.geetha.chessmatch.rules.Move
import dev.geetha.chessmatch.rules.PieceType
import dev.geetha.chessmatch.rules.Position
import dev.geetha.chessmatch.rules.Side
import dev.geetha.chessmatch.rules.Square
import dev.geetha.chessmatch.rules.TrackedPiece
import dev.geetha.chessmatch.rules.legalMoves
import dev.geetha.chessmatch.rules.outcome
import dev.geetha.chessmatch.rules.positionAfter
import dev.geetha.chessmatch.rules.relocate
import dev.geetha.chessmatch.rules.san
import dev.geetha.chessmatch.rules.startingPosition
import dev.geetha.chessmatch.rules.startingTracked
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PendingPromotion(
    val from: Square,
    val to: Square,
    val options: List<PieceType>,
)

data class GameUiState(
    val history: List<Move> = emptyList(),
    val position: Position = startingPosition(),
    val pieces: List<TrackedPiece> = startingTracked(),
    val sans: List<String> = emptyList(),
    val selected: Square? = null,
    val targets: List<Move> = emptyList(),
    val pendingPromotion: PendingPromotion? = null,
    val thinking: Boolean = false,
    val awaitingServer: Boolean = true,
    val matchId: String? = null,
    val serverNote: String = "Starting a recorded match…",
    val recentOpen: Boolean = false,
    val recentLoading: Boolean = false,
    val recent: List<RecentMatch> = emptyList(),
    val detail: RecentMatch? = null,
    val boardEpoch: Int = 0,
)

@HiltViewModel
class GameViewModel @Inject constructor(
    private val repository: MatchRepository,
    private val opponent: DeviceOpponent,
) : ViewModel() {
    private val _state = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    private var generation = 0
    private var thinkJob: Job? = null

    init {
        newGame()
    }

    fun newGame() {
        generation += 1
        val token = generation
        thinkJob?.cancel()
        _state.value = GameUiState(
            serverNote = "Starting a recorded match…",
            awaitingServer = true,
            boardEpoch = token,
        )
        thinkJob = viewModelScope.launch {
            when (val created = repository.create()) {
                is MatchCall.Ok -> {
                    if (token != generation) return@launch
                    _state.update {
                        it.copy(
                            matchId = created.value,
                            awaitingServer = false,
                            serverNote = "Recording match ${created.value.take(8)}",
                        )
                    }
                    refreshRecent()
                }
                is MatchCall.Failed -> {
                    if (token != generation) return@launch
                    _state.update {
                        it.copy(
                            awaitingServer = false,
                            serverNote = "Playing locally. ${created.message}",
                        )
                    }
                }
            }
        }
    }

    fun onSquare(square: Square) {
        val state = _state.value
        if (!canMove(state)) return
        val selected = state.selected
        if (selected == null) {
            select(square)
            return
        }
        val hits = state.targets.filter { it.to == square }
        if (hits.isEmpty()) {
            val piece = state.position[square]
            if (piece?.side == Side.WHITE) select(square) else clearSelection()
            return
        }
        val promotions = listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)
            .filter { type -> hits.any { it.promotion == type } }
        if (promotions.isNotEmpty()) {
            _state.update { it.copy(pendingPromotion = PendingPromotion(selected, square, promotions)) }
            return
        }
        commit(hits.first())
    }

    fun choosePromotion(type: PieceType) {
        val state = _state.value
        val pending = state.pendingPromotion ?: return
        val move = state.targets.firstOrNull {
            it.from == pending.from && it.to == pending.to && it.promotion == type
        } ?: return
        _state.value = state.copy(pendingPromotion = null)
        commit(move)
    }

    fun cancelPromotion() {
        _state.update { it.copy(pendingPromotion = null) }
    }

    fun openRecent() {
        _state.update { it.copy(recentOpen = true, recentLoading = true, detail = null) }
        viewModelScope.launch {
            when (val listed = repository.recent()) {
                is MatchCall.Ok -> _state.update { it.copy(recent = listed.value, recentLoading = false) }
                is MatchCall.Failed -> _state.update {
                    it.copy(recentLoading = false, serverNote = listed.message)
                }
            }
        }
    }

    fun closeRecent() {
        _state.update { it.copy(recentOpen = false, detail = null) }
    }

    fun openDetail(id: String) {
        _state.update { it.copy(detail = null) }
        viewModelScope.launch {
            when (val match = repository.detail(id)) {
                is MatchCall.Ok -> _state.update { it.copy(detail = match.value) }
                is MatchCall.Failed -> _state.update { it.copy(serverNote = match.message) }
            }
        }
    }

    private fun select(square: Square) {
        val state = _state.value
        val piece = state.position[square]
        if (piece?.side != Side.WHITE) return
        val targets = legalMoves(state.position).filter { it.from == square }
        _state.value = state.copy(selected = square, targets = targets, pendingPromotion = null)
    }

    private fun clearSelection() {
        _state.update { it.copy(selected = null, targets = emptyList(), pendingPromotion = null) }
    }

    private fun canMove(state: GameUiState): Boolean {
        if (state.thinking || state.awaitingServer || state.pendingPromotion != null) return false
        val status = outcome(state.position).status
        if (status == GameStatus.CHECKMATE || status == GameStatus.STALEMATE) return false
        return state.position.sideToMove == Side.WHITE
    }

    private fun commit(move: Move) {
        val token = generation
        val before = _state.value
        val rendered = render(before.history + move)
        val status = outcome(rendered.position).status
        val finished = status == GameStatus.CHECKMATE || status == GameStatus.STALEMATE
        val deviceReplies = !finished && rendered.position.sideToMove == Side.BLACK
        _state.value = before.copy(
            history = before.history + move,
            position = rendered.position,
            pieces = rendered.pieces,
            sans = rendered.sans,
            selected = null,
            targets = emptyList(),
            pendingPromotion = null,
            thinking = deviceReplies,
        )
        thinkJob?.cancel()
        thinkJob = viewModelScope.launch {
            if (!sync(move) || token != generation) {
                if (token == generation && _state.value.history != before.history) {
                    val note = _state.value.serverNote
                    _state.value = before.copy(serverNote = note)
                }
                return@launch
            }
            if (!deviceReplies) return@launch
            delay(420)
            if (token != generation) return@launch
            val reply = opponent.choose(_state.value.position) ?: run {
                _state.update { it.copy(thinking = false) }
                return@launch
            }
            val preReply = _state.value
            val replyRendered = render(preReply.history + reply)
            _state.value = preReply.copy(
                history = preReply.history + reply,
                position = replyRendered.position,
                pieces = replyRendered.pieces,
                sans = replyRendered.sans,
                thinking = true,
            )
            if (!sync(reply) || token != generation) {
                if (token == generation) {
                    val note = _state.value.serverNote
                    _state.value = preReply.copy(serverNote = note, thinking = false)
                }
                return@launch
            }
            delay(280)
            if (token != generation) return@launch
            _state.update { it.copy(thinking = false) }
        }
    }

    private suspend fun sync(move: Move): Boolean {
        val id = _state.value.matchId ?: return true
        return when (val result = repository.move(id, move)) {
            is MatchCall.Ok -> true
            is MatchCall.Failed -> {
                _state.update { it.copy(serverNote = result.message) }
                false
            }
        }
    }

    private fun refreshRecent() {
        viewModelScope.launch {
            when (val listed = repository.recent()) {
                is MatchCall.Ok -> _state.update { it.copy(recent = listed.value) }
                is MatchCall.Failed -> Unit
            }
        }
    }

    private fun render(history: List<Move>): Rendered {
        var position = startingPosition()
        var pieces = startingTracked()
        val sans = mutableListOf<String>()
        for (move in history) {
            sans += san(position, move)
            pieces = relocate(pieces, position, move)
            position = positionAfter(position, move)
        }
        return Rendered(position, pieces, sans.toList())
    }

    private data class Rendered(
        val position: Position,
        val pieces: List<TrackedPiece>,
        val sans: List<String>,
    )
}
