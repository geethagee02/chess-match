import assert from 'node:assert/strict';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { after, before, test } from 'node:test';
import { createServer } from './app.js';
import {
  legalMoves,
  outcome,
  parseFen,
  playUci,
  startingPosition,
  toFen,
  STARTING_FEN,
} from './chess.js';

const root = mkdtempSync(join(tmpdir(), 'chess-match-'));
const dbPath = join(root, 'chess.sqlite');
let running;

before(async () => {
  running = await createServer({ dbPath, host: '127.0.0.1', port: 0 });
});

after(async () => {
  await running.close();
  rmSync(root, { recursive: true, force: true });
});

function base() {
  return `http://127.0.0.1:${running.port}`;
}

function uciSet(position) {
  return new Set(legalMoves(position).map((move) => (
    `${String.fromCharCode(97 + move.from.file)}${move.from.rank + 1}${String.fromCharCode(97 + move.to.file)}${move.to.rank + 1}${move.promotion ?? ''}`
  )));
}

async function createMatch(white = 'Geetha', black = 'Device') {
  const response = await fetch(`${base()}/matches`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ white, black }),
  });
  assert.equal(response.status, 201);
  return response.json();
}

async function postMove(id, from, to, promotion) {
  return fetch(`${base()}/matches/${id}/moves`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ from, to, promotion }),
  });
}

test('pawn, knight, and the starting position', () => {
  const start = startingPosition();
  assert.equal(toFen(start), STARTING_FEN);
  const moves = uciSet(start);
  assert.equal(moves.size, 20);
  assert.equal(moves.has('e2e4'), true);
  assert.equal(moves.has('e2e3'), true);
  assert.equal(moves.has('e2e5'), false);
  assert.equal(moves.has('b1c3'), true);
  assert.equal(moves.has('b1a3'), true);
  assert.equal(moves.has('b1d2'), false);
  assert.equal(moves.has('b1b3'), false);

  const e4 = playUci(start, 'e2e4');
  assert.equal(e4.ok, true);
  assert.equal(e4.san, 'e4');
  assert.equal(toFen(e4.position), 'rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1');
  const black = uciSet(e4.position);
  assert.equal(black.has('e7e5'), true);
  assert.equal(black.has('e7e6'), true);
  assert.equal(black.has('e7e4'), false);

  const blocked = parseFen('4k3/8/8/8/8/4p3/4P3/4K3 w - - 0 1');
  assert.equal([...uciSet(blocked)].some((move) => move.startsWith('e2')), false);

  const knight = parseFen('4k3/8/8/8/8/2p5/8/1N2K3 w - - 0 1');
  const captured = playUci(knight, 'b1c3');
  assert.equal(captured.ok, true);
  assert.equal(captured.san, 'Nxc3');
  assert.equal(playUci(knight, 'b1b3').ok, false);
  assert.equal(playUci(knight, 'b1c4').message, 'Illegal move.');

  const passed = playUci(parseFen('4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1'), 'e5d6');
  assert.equal(passed.ok, true);
  assert.equal(passed.san, 'exd6');
  assert.equal(passed.position.squares[4 * 8 + 3], null);
  assert.equal(passed.position.squares[4 * 8 + 4], null);
  assert.equal(passed.position.squares[5 * 8 + 3].side, 'w');
  assert.equal(passed.position.squares[5 * 8 + 3].type, 'p');

  const promote = parseFen('k7/4P3/8/8/8/8/8/4K3 w - - 0 1');
  assert.equal(playUci(promote, 'e7e8').message, 'Pawn promotion needs a queen, rook, bishop, or knight.');
  const queen = playUci(promote, 'e7e8q');
  assert.equal(queen.ok, true);
  assert.equal(queen.position.squares[7 * 8 + 4].type, 'q');
});

test('check, checkmate, and stalemate', () => {
  let position = startingPosition();
  let last;
  for (const move of ['e2e4', 'e7e5', 'd1h5', 'b8c6', 'h5f7']) {
    last = playUci(position, move);
    assert.equal(last.ok, true, move);
    position = last.position;
  }
  assert.equal(last.san, 'Qxf7+');
  assert.equal(outcome(position).status, 'check');
  assert.equal(playUci(position, 'a7a6').message, 'Illegal move.');

  const pinned = parseFen('4k3/8/8/8/4n3/8/8/4R2K b - - 0 1');
  assert.equal(legalMoves(pinned).some((move) => move.from.file === 4 && move.from.rank === 3), false);

  let mate = startingPosition();
  for (const move of ['f2f3', 'e7e5', 'g2g4', 'd8h4']) {
    last = playUci(mate, move);
    assert.equal(last.ok, true, move);
    mate = last.position;
  }
  assert.equal(last.san, 'Qh4#');
  assert.equal(last.outcome.status, 'checkmate');
  assert.equal(last.outcome.result, '0-1');
  assert.equal(toFen(mate), 'rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3');
  assert.equal(playUci(mate, 'e2e3').message, 'The game is already over.');

  const drawn = outcome(parseFen('7k/5Q2/6K1/8/8/8/8/8 b - - 0 1'));
  assert.equal(drawn.status, 'stalemate');
  assert.equal(drawn.inCheck, false);
  assert.equal(outcome(parseFen('7k/6Q1/6K1/8/8/8/8/8 b - - 0 1')).status, 'checkmate');
});

test('the API records a legal move and rejects an illegal one', async () => {
  const health = await fetch(`${base()}/health`);
  assert.equal(health.status, 200);

  const first = await createMatch();
  const second = await createMatch('Geetha', 'Device');
  const listed = await fetch(`${base()}/matches?limit=10`);
  const listBody = await listed.json();
  assert.equal(listBody.matches[0].id, second.id);
  assert.equal(listBody.matches[1].id, first.id);
  assert.equal(listBody.matches[0].ply, 0);
  assert.equal(listBody.matches[0].status, 'ongoing');

  const accepted = await postMove(second.id, 'e2', 'e4');
  assert.equal(accepted.status, 200);
  const after = await accepted.json();
  assert.equal(after.fen, 'rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1');
  assert.equal(after.moves.length, 1);
  assert.equal(after.moves[0].san, 'e4');
  assert.equal(after.ply, 1);

  const pawn = await postMove(second.id, 'e7', 'e4');
  assert.equal(pawn.status, 422);
  const pawnBody = await pawn.json();
  assert.equal(pawnBody.error, 'illegal_move');
  assert.equal(pawnBody.message, 'Illegal move.');

  const knight = await postMove(first.id, 'b1', 'c3');
  assert.equal(knight.status, 200);
  const knightBody = await knight.json();
  assert.equal(knightBody.moves[0].san, 'Nc3');
  assert.equal(knightBody.moves[0].uci, 'b1c3');

  const notAKnight = await postMove(first.id, 'b8', 'b6');
  assert.equal(notAKnight.status, 422);

  const detail = await fetch(`${base()}/matches/${second.id}`);
  const stored = await detail.json();
  assert.equal(stored.moves.length, 1);
  assert.equal(stored.fen, after.fen);

  const missing = await fetch(`${base()}/matches/not-a-match`);
  assert.equal(missing.status, 404);
});

test('a move that ignores check is refused and checkmate closes the match', async () => {
  const match = await createMatch();
  for (const [from, to] of [['e2', 'e4'], ['e7', 'e5'], ['d1', 'h5'], ['b8', 'c6'], ['h5', 'f7']]) {
    const response = await postMove(match.id, from, to);
    assert.equal(response.status, 200, `${from}${to}`);
  }
  const ignored = await postMove(match.id, 'a7', 'a6');
  assert.equal(ignored.status, 422);
  const body = await ignored.json();
  assert.equal(body.error, 'illegal_move');
  const still = await fetch(`${base()}/matches/${match.id}`);
  const stored = await still.json();
  assert.equal(stored.moves.length, 5);
  assert.equal(stored.status, 'check');
  assert.equal(stored.inCheck, true);

  const mate = await createMatch();
  for (const [from, to] of [['f2', 'f3'], ['e7', 'e5'], ['g2', 'g4'], ['d8', 'h4']]) {
    const response = await postMove(mate.id, from, to);
    assert.equal(response.status, 200, `${from}${to}`);
  }
  const finished = await (await fetch(`${base()}/matches/${mate.id}`)).json();
  assert.equal(finished.status, 'checkmate');
  assert.equal(finished.result, '0-1');
  assert.equal(finished.moves[3].san, 'Qh4#');
  const extra = await postMove(mate.id, 'e2', 'e3');
  assert.equal(extra.status, 409);
  const extraBody = await extra.json();
  assert.equal(extraBody.error, 'game_over');
  assert.equal(extraBody.message, 'The game is already over.');
});
