import fs from "fs";
import path from "path";
import { config } from "./config";

/**
 * Tiny file-backed database (JSON) with versioned migrations.
 * Chosen on purpose: no native modules, so it installs cleanly in Termux.
 * For large-scale deployments replace this module with PostgreSQL.
 */
export interface UserRow {
  id: string;
  username: string;
  usernameLower: string;
  passHash: string;
  avatar: number;
  createdAt: number;
  ratings: Record<string, number>;
  stats: Record<string, { wins: number; losses: number; draws: number }>;
}
export interface MatchRow {
  id: string;
  game: string;
  white: string;
  black: string;
  result: string;
  ts: number;
}
interface DbShape {
  version: number;
  users: Record<string, UserRow>;
  matches: MatchRow[];
}

export const GAMES = ["checkers", "chess", "backgammon", "domino"];

type Migration = (db: DbShape) => void;
/** Append new migrations to the end; never edit old ones. */
const MIGRATIONS: Migration[] = [
  // 1: initial schema
  (db) => {
    db.users = db.users || {};
    db.matches = db.matches || [];
  },
  // 2: ensure every user has per-game ratings/stats
  (db) => {
    for (const u of Object.values(db.users)) {
      u.ratings = u.ratings || {};
      u.stats = u.stats || {};
      for (const g of GAMES) {
        if (u.ratings[g] === undefined) u.ratings[g] = 1000;
        if (!u.stats[g]) u.stats[g] = { wins: 0, losses: 0, draws: 0 };
      }
    }
  },
];

const file = path.join(config.dataDir, "db.json");
let db: DbShape = { version: 0, users: {}, matches: [] };
let writeTimer: NodeJS.Timeout | null = null;

export function initDb(): void {
  fs.mkdirSync(config.dataDir, { recursive: true });
  if (fs.existsSync(file)) {
    try {
      db = JSON.parse(fs.readFileSync(file, "utf8"));
    } catch (e) {
      const bak = file + ".corrupt-" + Date.now();
      fs.copyFileSync(file, bak);
      console.error("db.json unreadable, backed up to", bak);
      db = { version: 0, users: {}, matches: [] };
    }
  }
  while (db.version < MIGRATIONS.length) {
    MIGRATIONS[db.version](db);
    db.version++;
    console.log("DB migrated to version", db.version);
  }
  flush();
}

export function flush(): void {
  const tmp = file + ".tmp";
  fs.writeFileSync(tmp, JSON.stringify(db));
  fs.renameSync(tmp, file);
}

function scheduleFlush(): void {
  if (writeTimer) return;
  writeTimer = setTimeout(() => {
    writeTimer = null;
    try { flush(); } catch (e) { console.error("flush failed", e); }
  }, 500);
}

export function findUserByName(name: string): UserRow | undefined {
  const l = name.toLowerCase();
  return Object.values(db.users).find((u) => u.usernameLower === l);
}
export function getUser(id: string): UserRow | undefined {
  return db.users[id];
}
export function createUser(u: UserRow): void {
  db.users[u.id] = u;
  scheduleFlush();
}
export function saveUser(): void {
  scheduleFlush();
}
export function addMatch(m: MatchRow): void {
  db.matches.push(m);
  if (db.matches.length > 5000) db.matches.splice(0, db.matches.length - 5000);
  scheduleFlush();
}
export function allUsers(): UserRow[] {
  return Object.values(db.users);
}
export function userMatches(id: string, limit = 20): MatchRow[] {
  return db.matches.filter((m) => m.white === id || m.black === id).slice(-limit).reverse();
}
