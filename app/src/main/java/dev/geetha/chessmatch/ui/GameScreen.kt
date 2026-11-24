package dev.geetha.chessmatch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.geetha.chessmatch.data.RecentMatch
import dev.geetha.chessmatch.rules.GameStatus
import dev.geetha.chessmatch.rules.Move
import dev.geetha.chessmatch.rules.PieceType
import dev.geetha.chessmatch.rules.Side
import dev.geetha.chessmatch.rules.capturedType
import dev.geetha.chessmatch.rules.findKing
import dev.geetha.chessmatch.rules.outcome
import dev.geetha.chessmatch.rules.positionAfter
import dev.geetha.chessmatch.rules.startingPosition
import dev.geetha.chessmatch.ui.board.ChessBoard
import dev.geetha.chessmatch.ui.board.PieceArt
import dev.geetha.chessmatch.ui.theme.Frame
import dev.geetha.chessmatch.ui.theme.Gold
import dev.geetha.chessmatch.ui.theme.Ink
import dev.geetha.chessmatch.ui.theme.Ivory
import dev.geetha.chessmatch.ui.theme.Muted
import dev.geetha.chessmatch.ui.theme.Panel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun GameScreen(viewModel: GameViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val result = remember(state.position) { outcome(state.position) }
    val taken = remember(state.history) { takenPieces(state.history) }
    val checkSquare = if (result.inCheck) findKing(state.position, state.position.sideToMove) else null
    val terminal = result.status == GameStatus.CHECKMATE || result.status == GameStatus.STALEMATE
    val inputEnabled = !state.thinking && !state.awaitingServer && !terminal && state.pendingPromotion == null &&
        state.position.sideToMove == Side.WHITE
    BackHandler(enabled = state.recentOpen) { viewModel.closeRecent() }

    Box(Modifier.fillMaxSize().background(Ink)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text("Chess Match", color = Ivory, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text("Geetha vs Device", color = Muted, style = MaterialTheme.typography.bodyMedium)
            Text(
                state.serverNote,
                color = if (state.matchId == null) Color(0xFFE0A090) else Gold,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            PlayerStrip(
                name = "Device",
                active = state.position.sideToMove == Side.BLACK && !terminal,
                pieces = taken.second,
                side = Side.WHITE,
            )
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val side = if (maxWidth < maxHeight) maxWidth else maxHeight
                Box(
                    Modifier
                        .size(side)
                        .shadow(10.dp, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                        .background(Frame)
                        .padding(10.dp),
                ) {
                    ChessBoard(
                        pieces = state.pieces,
                        selected = state.selected,
                        targets = state.targets,
                        position = state.position,
                        lastMove = state.history.lastOrNull(),
                        checkSquare = checkSquare,
                        enabled = inputEnabled,
                        boardEpoch = state.boardEpoch,
                        onSquare = viewModel::onSquare,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            PlayerStrip(
                name = "You",
                active = state.position.sideToMove == Side.WHITE && !terminal && !state.thinking,
                pieces = taken.first,
                side = Side.BLACK,
            )
            Text(
                statusLine(state, result.status),
                color = if (result.inCheck || terminal) Color(0xFFE7B2A4) else Ivory,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 8.dp),
            )
            val pending = state.pendingPromotion
            if (pending != null) {
                PromotionRow(
                    options = pending.options,
                    onChoose = viewModel::choosePromotion,
                    onCancel = viewModel::cancelPromotion,
                )
            }
            MoveLine(state.sans)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = viewModel::newGame,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("New game")
                }
                OutlinedButton(
                    onClick = viewModel::openRecent,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Recent")
                }
            }
        }
        if (state.recentOpen) {
            RecentMatches(
                state = state,
                onClose = viewModel::closeRecent,
                onOpen = viewModel::openDetail,
            )
        }
    }
}

@Composable
private fun PlayerStrip(name: String, active: Boolean, pieces: List<PieceType>, side: Side) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (active) Gold else Muted.copy(alpha = 0.35f)),
        )
        Text(
            name,
            color = Ivory,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(start = 8.dp),
        )
        Box(Modifier.padding(start = 8.dp).weight(1f)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                pieces.forEach { type ->
                    PieceArt(side, type, Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun PromotionRow(options: List<PieceType>, onChoose: (PieceType) -> Unit, onCancel: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Panel)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Promote", color = Muted, fontSize = 13.sp)
        options.forEach { type ->
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Ink)
                    .border(1.dp, Gold.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    .clickable { onChoose(type) },
                contentAlignment = Alignment.Center,
            ) {
                PieceArt(Side.WHITE, type, Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        Text("Cancel", color = Gold, modifier = Modifier.clickable(onClick = onCancel).padding(6.dp))
    }
}

@Composable
private fun MoveLine(sans: List<String>) {
    val scroll = rememberScrollState()
    LaunchedEffect(sans.size) {
        scroll.animateScrollTo(scroll.maxValue)
    }
    Text(
        text = if (sans.isEmpty()) "White to move. Tap a piece, then a highlighted square." else paired(sans),
        color = Muted,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .horizontalScroll(scroll),
    )
}

@Composable
private fun RecentMatches(
    state: GameUiState,
    onClose: () -> Unit,
    onOpen: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Recent matches", color = Ivory, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Text("Close", color = Gold, modifier = Modifier.clickable(onClick = onClose).padding(8.dp))
        }
        Text("Stored by the match server.", color = Muted, modifier = Modifier.padding(bottom = 12.dp))
        if (state.recentLoading && state.recent.isEmpty()) {
            CircularProgressIndicator(color = Gold, modifier = Modifier.padding(top = 24.dp).align(Alignment.CenterHorizontally))
        } else if (state.recent.isEmpty()) {
            Text("No recorded matches yet. Start the server, then tap New game.", color = Ivory, modifier = Modifier.padding(top = 12.dp))
        } else {
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.recent, key = { it.id }) { match ->
                    RecentRow(match, expanded = state.detail?.id == match.id, detail = state.detail, onOpen = onOpen)
                }
            }
        }
    }
}

@Composable
private fun RecentRow(match: RecentMatch, expanded: Boolean, detail: RecentMatch?, onOpen: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Panel)
            .clickable { onOpen(match.id) }
            .padding(14.dp),
    ) {
        Text("${match.white} vs ${match.black}", color = Ivory, fontWeight = FontWeight.SemiBold)
        Text(matchLabel(match), color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        Text(formatStamp(match.updatedAt), color = Muted.copy(alpha = 0.8f), fontSize = 12.sp)
        if (expanded) {
            val sans = detail?.sans.orEmpty()
            Text(
                text = if (sans.isEmpty()) "No moves stored." else paired(sans),
                color = Ivory,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private fun statusLine(state: GameUiState, status: GameStatus): String {
    if (state.awaitingServer) return "Setting up the match"
    if (state.thinking) return "Device is thinking"
    return when (status) {
        GameStatus.CHECKMATE -> if (state.position.sideToMove == Side.WHITE) "Checkmate · Device wins" else "Checkmate · You win"
        GameStatus.STALEMATE -> "Stalemate · Draw"
        GameStatus.CHECK -> if (state.position.sideToMove == Side.WHITE) "Check · Your turn" else "Check"
        GameStatus.ONGOING -> if (state.position.sideToMove == Side.WHITE) "Your turn" else "Device to move"
    }
}

private fun matchLabel(match: RecentMatch): String = when (match.status) {
    "checkmate" -> if (match.result == "1-0") "Checkmate · ${match.white} won" else "Checkmate · ${match.black} won"
    "stalemate" -> "Stalemate · ${match.ply} moves"
    "check" -> "Check · ${match.ply} moves"
    else -> "In progress · ${match.ply} moves"
}

private fun paired(sans: List<String>): String = sans.mapIndexed { index, san ->
    if (index % 2 == 0) "${index / 2 + 1}. $san" else san
}.joinToString("  ")

private fun takenPieces(history: List<Move>): Pair<List<PieceType>, List<PieceType>> {
    var position = startingPosition()
    val whiteTook = mutableListOf<PieceType>()
    val blackTook = mutableListOf<PieceType>()
    for (move in history) {
        val type = capturedType(position, move)
        if (type != null) {
            if (position.sideToMove == Side.WHITE) whiteTook += type else blackTook += type
        }
        position = positionAfter(position, move)
    }
    val order = compareByDescending<PieceType> { it.value }
    return whiteTook.sortedWith(order) to blackTook.sortedWith(order)
}

private val stamp: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")

private fun formatStamp(iso: String): String = runCatching {
    Instant.parse(iso).atZone(ZoneId.systemDefault()).format(stamp)
}.getOrDefault(iso)
