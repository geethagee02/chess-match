import { mkdirSync } from 'node:fs';
import { dirname } from 'node:path';
import { DatabaseSync } from 'node:sqlite';

const SCHEMA = `
CREATE TABLE IF NOT EXISTS matches (
  id TEXT PRIMARY KEY,
  white TEXT NOT NULL,
  black TEXT NOT NULL,
  status TEXT NOT NULL,
  result TEXT NOT NULL,
  in_check INTEGER NOT NULL,
  fen TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS moves (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  match_id TEXT NOT NULL REFERENCES matches(id),
  ply INTEGER NOT NULL,
  uci TEXT NOT NULL,
  san TEXT NOT NULL,
  fen TEXT NOT NULL,
  created_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_matches_updated ON matches(updated_at);
CREATE INDEX IF NOT EXISTS idx_moves_match ON moves(match_id, ply);
`;

export function openDatabase(dbPath) {
  mkdirSync(dirname(dbPath), { recursive: true });
  const db = new DatabaseSync(dbPath);
  db.exec('PRAGMA foreign_keys = ON');
  db.exec(SCHEMA);
  return db;
}

export function transaction(db, fn) {
  db.exec('BEGIN');
  try {
    const result = fn();
    db.exec('COMMIT');
    return result;
  } catch (error) {
    try {
      db.exec('ROLLBACK');
    } catch {
      // The original error is the one worth surfacing.
    }
    throw error;
  }
}

export function insertMatch(db, match) {
  db.prepare(`
    INSERT INTO matches (id, white, black, status, result, in_check, fen, created_at, updated_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
  `).run(
    match.id,
    match.white,
    match.black,
    match.status,
    match.result,
    match.inCheck ? 1 : 0,
    match.fen,
    match.createdAt,
    match.updatedAt,
  );
}

export function updateMatch(db, match) {
  db.prepare(`
    UPDATE matches
    SET status = ?, result = ?, in_check = ?, fen = ?, updated_at = ?
    WHERE id = ?
  `).run(
    match.status,
    match.result,
    match.inCheck ? 1 : 0,
    match.fen,
    match.updatedAt,
    match.id,
  );
}

export function insertMove(db, move) {
  db.prepare(`
    INSERT INTO moves (match_id, ply, uci, san, fen, created_at)
    VALUES (?, ?, ?, ?, ?, ?)
  `).run(move.matchId, move.ply, move.uci, move.san, move.fen, move.createdAt);
}

export function loadMatch(db, id) {
  return db.prepare('SELECT * FROM matches WHERE id = ?').get(id) ?? null;
}

export function loadMoves(db, id) {
  return db.prepare('SELECT ply, uci, san, fen, created_at FROM moves WHERE match_id = ? ORDER BY ply').all(id);
}

export function listMatches(db, limit) {
  return db.prepare(`
    SELECT matches.*, (SELECT COUNT(*) FROM moves WHERE moves.match_id = matches.id) AS ply
    FROM matches
    ORDER BY updated_at DESC, rowid DESC
    LIMIT ?
  `).all(limit);
}
