import http from 'node:http';
import { randomUUID } from 'node:crypto';
import { outcome, parseFen, parseSquare, play, STARTING_FEN, toFen } from './chess.js';
import {
  insertMatch,
  insertMove,
  listMatches,
  loadMatch,
  loadMoves,
  openDatabase,
  transaction,
  updateMatch,
} from './db.js';

export class HttpError extends Error {
  constructor(status, message, code = 'bad_request') {
    super(message);
    this.status = status;
    this.code = code;
  }
}

function nowIso() {
  return new Date().toISOString();
}

function send(res, status, payload) {
  const body = payload == null ? '' : JSON.stringify(payload);
  res.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'access-control-allow-origin': '*',
    'access-control-allow-methods': 'GET,POST,OPTIONS',
    'access-control-allow-headers': 'content-type',
    'content-length': Buffer.byteLength(body),
  });
  res.end(body);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on('data', (chunk) => {
      size += chunk.length;
      if (size > 1_000_000) {
        reject(new HttpError(413, 'Request body is too large.', 'bad_request'));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => {
      if (chunks.length === 0) {
        resolve(null);
        return;
      }
      try {
        resolve(JSON.parse(Buffer.concat(chunks).toString('utf8')));
      } catch {
        reject(new HttpError(400, 'Body must be JSON.', 'bad_request'));
      }
    });
    req.on('error', reject);
  });
}

function cleanName(value, label) {
  if (typeof value !== 'string') throw new HttpError(400, `${label} must be text.`, 'bad_request');
  const name = value.trim();
  if (name.length < 1 || name.length > 40) {
    throw new HttpError(400, 'Player names must be 1 to 40 characters.', 'bad_request');
  }
  return name;
}

function presentMove(row) {
  return {
    ply: row.ply,
    uci: row.uci,
    san: row.san,
    fen: row.fen,
  };
}

function presentMatch(row, moves) {
  return {
    id: row.id,
    white: row.white,
    black: row.black,
    status: row.status,
    result: row.result,
    inCheck: row.in_check === 1,
    fen: row.fen,
    ply: moves.length,
    moves: moves.map(presentMove),
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}

function presentSummary(row) {
  return {
    id: row.id,
    white: row.white,
    black: row.black,
    status: row.status,
    result: row.result,
    inCheck: row.in_check === 1,
    ply: row.ply,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}

function createMatch(db, body, clock) {
  const white = cleanName(body?.white ?? 'Geetha', 'White');
  const black = cleanName(body?.black ?? 'Device', 'Black');
  const start = outcome(parseFen(STARTING_FEN));
  const match = {
    id: randomUUID(),
    white,
    black,
    status: start.status,
    result: start.result,
    inCheck: start.inCheck,
    fen: STARTING_FEN,
    createdAt: clock,
    updatedAt: clock,
  };
  insertMatch(db, match);
  return presentMatch(loadMatch(db, match.id), []);
}

function postMove(db, id, body, clock) {
  if (!body || typeof body.from !== 'string' || typeof body.to !== 'string') {
    throw new HttpError(400, 'A move needs from and to squares.', 'bad_request');
  }
  const from = parseSquare(body.from);
  const to = parseSquare(body.to);
  if (!from || !to) {
    throw new HttpError(400, 'Squares are files a–h and ranks 1–8.', 'bad_request');
  }
  let promotion = null;
  if (body.promotion != null && body.promotion !== '') {
    if (typeof body.promotion !== 'string') {
      throw new HttpError(400, 'Pawn promotion needs a queen, rook, bishop, or knight.', 'bad_request');
    }
    promotion = body.promotion.trim().toLowerCase();
    if (!'qrbn'.includes(promotion) || promotion.length !== 1) {
      throw new HttpError(400, 'Pawn promotion needs a queen, rook, bishop, or knight.', 'bad_request');
    }
  }
  const row = loadMatch(db, id);
  if (!row) throw new HttpError(404, 'No such match.', 'not_found');
  const played = play(parseFen(row.fen), from, to, promotion);
  if (!played.ok) {
    const over = played.message === 'The game is already over.';
    throw new HttpError(over ? 409 : 422, played.message, over ? 'game_over' : 'illegal_move');
  }
  const fen = toFen(played.position);
  const ply = loadMoves(db, id).length + 1;
  transaction(db, () => {
    insertMove(db, {
      matchId: id,
      ply,
      uci: played.uci,
      san: played.san,
      fen,
      createdAt: clock,
    });
    updateMatch(db, {
      id,
      status: played.outcome.status,
      result: played.outcome.result,
      inCheck: played.outcome.inCheck,
      fen,
      updatedAt: clock,
    });
  });
  return presentMatch(loadMatch(db, id), loadMoves(db, id));
}

export function handleRequest(db, req, res, body, clock = nowIso()) {
  const url = new URL(req.url ?? '/', 'http://localhost');
  const parts = url.pathname.split('/').filter(Boolean);
  const method = req.method ?? 'GET';
  if (method === 'OPTIONS') {
    send(res, 204, null);
    return;
  }
  if (method === 'GET' && parts.length === 1 && parts[0] === 'health') {
    send(res, 200, { ok: true, service: 'chess-match' });
    return;
  }
  if (parts[0] === 'matches' && parts.length === 1 && method === 'GET') {
    const requested = Number(url.searchParams.get('limit') ?? 20);
    const limit = Number.isFinite(requested) ? Math.min(50, Math.max(1, Math.trunc(requested))) : 20;
    send(res, 200, { matches: listMatches(db, limit).map(presentSummary) });
    return;
  }
  if (parts[0] === 'matches' && parts.length === 1 && method === 'POST') {
    send(res, 201, createMatch(db, body, clock));
    return;
  }
  if (parts[0] === 'matches' && parts.length === 2 && method === 'GET') {
    const row = loadMatch(db, parts[1]);
    if (!row) throw new HttpError(404, 'No such match.', 'not_found');
    send(res, 200, presentMatch(row, loadMoves(db, parts[1])));
    return;
  }
  if (parts[0] === 'matches' && parts.length === 3 && parts[2] === 'moves' && method === 'POST') {
    send(res, 200, postMove(db, parts[1], body, clock));
    return;
  }
  throw new HttpError(404, 'No such route.', 'not_found');
}

export function createServer({ dbPath, host = '127.0.0.1', port = 8791 } = {}) {
  const db = openDatabase(dbPath);
  const server = http.createServer(async (req, res) => {
    try {
      const body = await readBody(req);
      handleRequest(db, req, res, body, nowIso());
    } catch (error) {
      const status = error instanceof HttpError ? error.status : 500;
      const message = error instanceof HttpError ? error.message : 'The match service hit an unexpected error.';
      const code = error instanceof HttpError ? error.code : 'server_error';
      if (status === 500) console.error(error);
      if (!res.headersSent) send(res, status, { error: code, message });
    }
  });
  return new Promise((resolve) => {
    server.listen(port, host, () => {
      const address = server.address();
      resolve({
        host,
        port: typeof address === 'object' && address ? address.port : port,
        close: () => new Promise((done, reject) => {
          server.close((error) => {
            try {
              db.close();
            } catch {
              // Already closed.
            }
            if (error) reject(error);
            else done();
          });
        }),
      });
    });
  });
}
