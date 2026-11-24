const STARTING_FEN = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1';

const KNIGHT = [
  [1, 2], [2, 1], [-1, 2], [-2, 1],
  [1, -2], [2, -1], [-1, -2], [-2, -1],
];
const ROOK_DIRS = [[1, 0], [-1, 0], [0, 1], [0, -1]];
const BISHOP_DIRS = [[1, 1], [1, -1], [-1, 1], [-1, -1]];
const VALUES = { q: 9, r: 5, b: 3, n: 3, p: 1, k: 0 };
const SAN = { k: 'K', q: 'Q', r: 'R', b: 'B', n: 'N', p: '' };

export function startingPosition() {
  return parseFen(STARTING_FEN);
}

export function parseSquare(text) {
  const raw = String(text ?? '').trim().toLowerCase();
  if (!/^[a-h][1-8]$/.test(raw)) return null;
  return square(raw.charCodeAt(0) - 97, raw.charCodeAt(0 + 1) - 49);
}

function square(file, rank) {
  return { file, rank, index: rank * 8 + file };
}

function at(index) {
  return square(index % 8, Math.floor(index / 8));
}

export function algebraic(sq) {
  return String.fromCharCode(97 + sq.file) + String(sq.rank + 1);
}

function same(a, b) {
  return Boolean(a) && Boolean(b) && a.file === b.file && a.rank === b.rank;
}

function opposite(side) {
  return side === 'w' ? 'b' : 'w';
}

function piece(side, type) {
  return { side, type };
}

function fenChar(item) {
  const letter = item.type;
  return item.side === 'w' ? letter.toUpperCase() : letter;
}

function emptyRights() {
  return { K: false, Q: false, k: false, q: false };
}

function rightsFen(rights) {
  const text = `${rights.K ? 'K' : ''}${rights.Q ? 'Q' : ''}${rights.k ? 'k' : ''}${rights.q ? 'q' : ''}`;
  return text || '-';
}

function withoutRook(rights, sq) {
  const next = { ...rights };
  const name = algebraic(sq);
  if (name === 'a1') next.Q = false;
  if (name === 'h1') next.K = false;
  if (name === 'a8') next.q = false;
  if (name === 'h8') next.k = false;
  return next;
}

export function parseFen(fen) {
  const parts = fen.trim().split(/\s+/);
  if (parts.length < 4) throw new Error('FEN needs a board, side, castling, and en-passant square.');
  const ranks = parts[0].split('/');
  if (ranks.length !== 8) throw new Error('FEN board needs 8 ranks.');
  const squares = Array(64).fill(null);
  ranks.forEach((rank, index) => {
    const rankIndex = 7 - index;
    let file = 0;
    for (const char of rank) {
      if (/[1-8]/.test(char)) {
        file += Number(char);
      } else {
        const type = char.toLowerCase();
        if (!'kqrbnp'.includes(type) || file > 7) throw new Error('Bad FEN piece.');
        squares[rankIndex * 8 + file] = piece(char === char.toUpperCase() ? 'w' : 'b', type);
        file += 1;
      }
    }
    if (file !== 8) throw new Error('FEN rank does not fill the board.');
  });
  if (parts[1] !== 'w' && parts[1] !== 'b') throw new Error('FEN side must be w or b.');
  const castling = emptyRights();
  if (parts[2] !== '-') {
    if ([...parts[2]].some((char) => !'KQkq'.includes(char))) throw new Error('Bad castling rights.');
    castling.K = parts[2].includes('K');
    castling.Q = parts[2].includes('Q');
    castling.k = parts[2].includes('k');
    castling.q = parts[2].includes('q');
  }
  const enPassant = parts[3] === '-' ? null : parseSquare(parts[3]);
  if (parts[3] !== '-' && !enPassant) throw new Error('Bad en-passant square.');
  return {
    squares,
    sideToMove: parts[1],
    castling,
    enPassant,
    halfmoveClock: Number(parts[4] ?? 0) || 0,
    fullmove: Number(parts[5] ?? 1) || 1,
  };
}

export function toFen(position) {
  let board = '';
  for (let rank = 7; rank >= 0; rank -= 1) {
    let empty = 0;
    for (let file = 0; file < 8; file += 1) {
      const item = position.squares[rank * 8 + file];
      if (!item) {
        empty += 1;
      } else {
        if (empty > 0) {
          board += String(empty);
          empty = 0;
        }
        board += fenChar(item);
      }
    }
    if (empty > 0) board += String(empty);
    if (rank > 0) board += '/';
  }
  const side = position.sideToMove;
  const ep = position.enPassant ? algebraic(position.enPassant) : '-';
  return `${board} ${side} ${rightsFen(position.castling)} ${ep} ${position.halfmoveClock} ${position.fullmove}`;
}

function occupant(position, sq) {
  return position.squares[sq.index];
}

export function findKing(position, side) {
  for (let index = 0; index < 64; index += 1) {
    const item = position.squares[index];
    if (item && item.side === side && item.type === 'k') return at(index);
  }
  return null;
}

function step(delta) {
  if (delta > 0) return 1;
  if (delta < 0) return -1;
  return 0;
}

function clearBetween(position, from, target) {
  const stepFile = step(target.file - from.file);
  const stepRank = step(target.rank - from.rank);
  let file = from.file + stepFile;
  let rank = from.rank + stepRank;
  while (file !== target.file || rank !== target.rank) {
    if (position.squares[rank * 8 + file]) return false;
    file += stepFile;
    rank += stepRank;
  }
  return true;
}

function attacks(position, from, target, item) {
  const fileDelta = target.file - from.file;
  const rankDelta = target.rank - from.rank;
  if (fileDelta === 0 && rankDelta === 0) return false;
  if (item.type === 'n') {
    return (Math.abs(fileDelta) === 1 && Math.abs(rankDelta) === 2)
      || (Math.abs(fileDelta) === 2 && Math.abs(rankDelta) === 1);
  }
  if (item.type === 'k') return Math.abs(fileDelta) <= 1 && Math.abs(rankDelta) <= 1;
  if (item.type === 'p') {
    const forward = item.side === 'w' ? 1 : -1;
    return rankDelta === forward && Math.abs(fileDelta) === 1;
  }
  if (item.type === 'b') {
    return fileDelta !== 0 && Math.abs(fileDelta) === Math.abs(rankDelta) && clearBetween(position, from, target);
  }
  if (item.type === 'r') {
    return (fileDelta === 0 || rankDelta === 0) && clearBetween(position, from, target);
  }
  return (fileDelta === 0 || rankDelta === 0 || Math.abs(fileDelta) === Math.abs(rankDelta))
    && clearBetween(position, from, target);
}

export function isAttacked(position, target, by) {
  for (let index = 0; index < 64; index += 1) {
    const item = position.squares[index];
    if (!item || item.side !== by) continue;
    if (attacks(position, at(index), target, item)) return true;
  }
  return false;
}

function isCastle(position, move) {
  const moving = occupant(position, move.from);
  return Boolean(moving) && moving.type === 'k' && Math.abs(move.to.file - move.from.file) === 2;
}

function castleRookFrom(move) {
  return square(move.to.file === 6 ? 7 : 0, move.from.rank);
}

function castleRookTo(move) {
  return square(move.to.file === 6 ? 5 : 3, move.from.rank);
}

export function victimSquare(position, move) {
  if (occupant(position, move.to)) return move.to;
  const moving = occupant(position, move.from);
  if (!moving || moving.type !== 'p' || move.from.file === move.to.file) return null;
  if (!same(position.enPassant, move.to)) return null;
  return square(move.to.file, move.from.rank);
}

export function positionAfter(position, move) {
  const moving = occupant(position, move.from);
  const victim = occupant(position, move.to);
  const squares = position.squares.slice();
  const capturedAt = victimSquare(position, move);
  squares[move.from.index] = null;
  if (capturedAt && capturedAt.index !== move.to.index) squares[capturedAt.index] = null;
  squares[move.to.index] = move.promotion ? piece(moving.side, move.promotion) : moving;
  if (isCastle(position, move)) {
    const rookFrom = castleRookFrom(move);
    const rookTo = castleRookTo(move);
    squares[rookTo.index] = squares[rookFrom.index];
    squares[rookFrom.index] = null;
  }
  let castling = { ...position.castling };
  if (moving.type === 'k') {
    if (moving.side === 'w') {
      castling.K = false;
      castling.Q = false;
    } else {
      castling.k = false;
      castling.q = false;
    }
  }
  if (moving.type === 'r') castling = withoutRook(castling, move.from);
  if (victim && victim.type === 'r') castling = withoutRook(castling, move.to);
  const enPassant = moving.type === 'p' && Math.abs(move.to.rank - move.from.rank) === 2
    ? square(move.from.file, (move.from.rank + move.to.rank) / 2)
    : null;
  const halfmoveClock = moving.type === 'p' || capturedAt ? 0 : position.halfmoveClock + 1;
  const fullmove = position.sideToMove === 'b' ? position.fullmove + 1 : position.fullmove;
  return {
    squares,
    sideToMove: opposite(position.sideToMove),
    castling,
    enPassant,
    halfmoveClock,
    fullmove,
  };
}

function addSlides(position, from, side, directions, moves) {
  for (const [fileDelta, rankDelta] of directions) {
    let file = from.file + fileDelta;
    let rank = from.rank + rankDelta;
    while (file >= 0 && file < 8 && rank >= 0 && rank < 8) {
      const dest = square(file, rank);
      const blocking = occupant(position, dest);
      if (!blocking) moves.push({ from, to: dest, promotion: null });
      else {
        if (blocking.side !== side) moves.push({ from, to: dest, promotion: null });
        break;
      }
      file += fileDelta;
      rank += rankDelta;
    }
  }
}

function addPawnMove(from, to, promote, moves) {
  if (!promote) {
    moves.push({ from, to, promotion: null });
    return;
  }
  for (const promotion of ['q', 'r', 'b', 'n']) moves.push({ from, to, promotion });
}

function pseudoLegal(position) {
  const moves = [];
  const side = position.sideToMove;
  for (let index = 0; index < 64; index += 1) {
    const item = position.squares[index];
    if (!item || item.side !== side) continue;
    const from = at(index);
    if (item.type === 'p') {
      const forward = side === 'w' ? 1 : -1;
      const startRank = side === 'w' ? 1 : 6;
      const lastRank = side === 'w' ? 7 : 0;
      const one = from.rank + forward;
      if (one >= 0 && one < 8) {
        const push = square(from.file, one);
        if (!occupant(position, push)) {
          addPawnMove(from, push, one === lastRank, moves);
          if (from.rank === startRank) {
            const two = square(from.file, from.rank + forward * 2);
            if (!occupant(position, two)) moves.push({ from, to: two, promotion: null });
          }
        }
        for (const fileDelta of [-1, 1]) {
          const file = from.file + fileDelta;
          if (file < 0 || file > 7) continue;
          const dest = square(file, one);
          const blocking = occupant(position, dest);
          if (blocking && blocking.side !== side) addPawnMove(from, dest, one === lastRank, moves);
          else if (!blocking && same(position.enPassant, dest)) {
            const captured = occupant(position, square(dest.file, from.rank));
            if (captured && captured.type === 'p' && captured.side !== side) {
              moves.push({ from, to: dest, promotion: null });
            }
          }
        }
      }
    } else if (item.type === 'n') {
      for (const [fileDelta, rankDelta] of KNIGHT) {
        const file = from.file + fileDelta;
        const rank = from.rank + rankDelta;
        if (file < 0 || file > 7 || rank < 0 || rank > 7) continue;
        const dest = square(file, rank);
        const blocking = occupant(position, dest);
        if (!blocking || blocking.side !== side) moves.push({ from, to: dest, promotion: null });
      }
    } else if (item.type === 'b') {
      addSlides(position, from, side, BISHOP_DIRS, moves);
    } else if (item.type === 'r') {
      addSlides(position, from, side, ROOK_DIRS, moves);
    } else if (item.type === 'q') {
      addSlides(position, from, side, ROOK_DIRS.concat(BISHOP_DIRS), moves);
    } else if (item.type === 'k') {
      for (let fileDelta = -1; fileDelta <= 1; fileDelta += 1) {
        for (let rankDelta = -1; rankDelta <= 1; rankDelta += 1) {
          if (fileDelta === 0 && rankDelta === 0) continue;
          const file = from.file + fileDelta;
          const rank = from.rank + rankDelta;
          if (file < 0 || file > 7 || rank < 0 || rank > 7) continue;
          const dest = square(file, rank);
          const blocking = occupant(position, dest);
          if (!blocking || blocking.side !== side) moves.push({ from, to: dest, promotion: null });
        }
      }
      const rank = side === 'w' ? 0 : 7;
      const home = square(4, rank);
      const king = occupant(position, home);
      if (king && king.type === 'k' && king.side === side && !isAttacked(position, home, opposite(side))) {
        const enemy = opposite(side);
        const kingSide = side === 'w' ? position.castling.K : position.castling.k;
        const queenSide = side === 'w' ? position.castling.Q : position.castling.q;
        if (kingSide) {
          const rook = occupant(position, square(7, rank));
          const passed = [square(5, rank), square(6, rank)];
          if (rook && rook.type === 'r' && rook.side === side
            && passed.every((sq) => !occupant(position, sq) && !isAttacked(position, sq, enemy))) {
            moves.push({ from: home, to: square(6, rank), promotion: null });
          }
        }
        if (queenSide) {
          const rook = occupant(position, square(0, rank));
          const empty = [square(1, rank), square(2, rank), square(3, rank)];
          const passed = [square(3, rank), square(2, rank)];
          if (rook && rook.type === 'r' && rook.side === side
            && empty.every((sq) => !occupant(position, sq))
            && passed.every((sq) => !isAttacked(position, sq, enemy))) {
            moves.push({ from: home, to: square(2, rank), promotion: null });
          }
        }
      }
    }
  }
  return moves;
}

export function legalMoves(position) {
  const side = position.sideToMove;
  const enemy = opposite(side);
  return pseudoLegal(position).filter((move) => {
    const next = positionAfter(position, move);
    const king = findKing(next, side);
    return king && !isAttacked(next, king, enemy);
  });
}

export function outcome(position) {
  const king = findKing(position, position.sideToMove);
  const inCheck = Boolean(king) && isAttacked(position, king, opposite(position.sideToMove));
  const moves = legalMoves(position);
  let status = 'ongoing';
  if (moves.length === 0) status = inCheck ? 'checkmate' : 'stalemate';
  else if (inCheck) status = 'check';
  let result = '*';
  if (status === 'checkmate') result = position.sideToMove === 'w' ? '0-1' : '1-0';
  if (status === 'stalemate') result = '1/2-1/2';
  return { status, inCheck, result };
}

function uci(move) {
  return algebraic(move.from) + algebraic(move.to) + (move.promotion ?? '');
}

export function san(position, move) {
  const moving = occupant(position, move.from);
  const next = positionAfter(position, move);
  const status = outcome(next).status;
  const suffix = status === 'checkmate' ? '#' : status === 'check' ? '+' : '';
  if (moving.type === 'k' && Math.abs(move.to.file - move.from.file) === 2) {
    return (move.to.file > move.from.file ? 'O-O' : 'O-O-O') + suffix;
  }
  const capture = Boolean(victimSquare(position, move));
  const destination = algebraic(move.to);
  const promotion = move.promotion ? `=${SAN[move.promotion]}` : '';
  if (moving.type === 'p') {
    const body = capture ? `${String.fromCharCode(97 + move.from.file)}x${destination}` : destination;
    return body + promotion + suffix;
  }
  const others = legalMoves(position).filter((other) => (
    other.to.index === move.to.index
    && other.from.index !== move.from.index
    && other.promotion === move.promotion
    && occupant(position, other.from)?.type === moving.type
  ));
  let disambiguation = '';
  if (others.length > 0) {
    if (others.every((other) => other.from.file !== move.from.file)) {
      disambiguation = String.fromCharCode(97 + move.from.file);
    } else if (others.every((other) => other.from.rank !== move.from.rank)) {
      disambiguation = String(move.from.rank + 1);
    } else {
      disambiguation = algebraic(move.from);
    }
  }
  return SAN[moving.type] + disambiguation + (capture ? 'x' : '') + destination + promotion + suffix;
}

export function play(position, from, to, promotion) {
  const current = outcome(position);
  if (current.status === 'checkmate' || current.status === 'stalemate') {
    return { ok: false, message: 'The game is already over.' };
  }
  if (promotion === 'k' || promotion === 'p') {
    return { ok: false, message: 'Pawn promotion needs a queen, rook, bishop, or knight.' };
  }
  const moving = occupant(position, from);
  if (!moving) return { ok: false, message: 'That square is empty.' };
  if (moving.side !== position.sideToMove) return { ok: false, message: "It is not that piece's turn." };
  const exact = legalMoves(position).filter((move) => move.from.index === from.index && move.to.index === to.index);
  if (exact.length === 0) return { ok: false, message: 'Illegal move.' };
  let move;
  if (exact.some((item) => item.promotion)) {
    if (!promotion) return { ok: false, message: 'Pawn promotion needs a queen, rook, bishop, or knight.' };
    move = exact.find((item) => item.promotion === promotion);
    if (!move) return { ok: false, message: 'Pawn promotion needs a queen, rook, bishop, or knight.' };
  } else if (promotion) {
    return { ok: false, message: 'That move is not a promotion.' };
  } else {
    move = exact[0];
  }
  const next = positionAfter(position, move);
  return {
    ok: true,
    move,
    uci: uci(move),
    san: san(position, move),
    position: next,
    outcome: outcome(next),
  };
}

export function playUci(position, text) {
  const raw = String(text ?? '').trim().toLowerCase();
  if (!/^[a-h][1-8][a-h][1-8][qrbn]?$/.test(raw)) {
    const promotion = raw.length > 4 ? raw.slice(4) : '';
    if (promotion && !'qrbn'.includes(promotion)) {
      return { ok: false, message: 'Pawn promotion needs a queen, rook, bishop, or knight.' };
    }
    return { ok: false, message: 'Squares are files a–h and ranks 1–8.' };
  }
  const from = parseSquare(raw.slice(0, 2));
  const to = parseSquare(raw.slice(2, 4));
  const promotion = raw.length === 5 ? raw[4] : null;
  return play(position, from, to, promotion);
}

export { STARTING_FEN, VALUES };
