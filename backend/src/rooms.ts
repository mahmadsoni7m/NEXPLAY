import crypto from "crypto";
import type WebSocket from "ws";
import { PLUGINS } from "./games";
import { addMatch, getUser, saveUser } from "./db";
import { eloDelta } from "./elo";

type Color = "w" | "b";
interface Seat {
  userId: string;
  name: string;
  conn?: WebSocket;
  offlineSince?: number;
}
interface Room {
  code: string;
  game: string;
  seats: { w?: Seat; b?: Seat };
  state: string;
  phase: "waiting" | "playing" | "over";
  result: Color | "draw" | null;
  delta: { w: number; b: number };
  last: string | null;
  seq: number;
  createdAt: number;
  left: Set<string>;
}

const CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
const FORFEIT_AFTER_MS = 120_000;
const WAITING_TTL_MS = 15 * 60_000;

const rooms = new Map<string, Room>();
const userRoom = new Map<string, string>();
const queues = new Map<string, { userId: string; conn: WebSocket }[]>();

const send = (ws: WebSocket | undefined, obj: unknown) => {
  if (ws && ws.readyState === 1) ws.send(JSON.stringify(obj));
};

function newCode(): string {
  for (;;) {
    let c = "";
    for (let i = 0; i < 5; i++) c += CODE_CHARS[crypto.randomInt(0, CODE_CHARS.length)];
    if (!rooms.has(c)) return c;
  }
}

function snapshot(room: Room, color: Color) {
  const opp = room.seats[color === "w" ? "b" : "w"];
  const plugin = PLUGINS[room.game];
  return {
    code: room.code,
    game: room.game,
    color,
    state: room.state,
    turn: plugin.turn(room.state),
    opp: opp ? { name: opp.name, online: !!opp.conn } : null,
    phase: room.phase,
    result: room.result,
    delta: room.delta[color],
    last: room.last,
    seq: room.seq,
  };
}

function broadcast(room: Room) {
  for (const c of ["w", "b"] as Color[]) {
    const seat = room.seats[c];
    if (seat) send(seat.conn, { t: "room", room: snapshot(room, c) });
  }
}

export function onlineCount(): number {
  let n = 0;
  for (const r of rooms.values()) for (const c of ["w", "b"] as Color[]) if (r.seats[c]?.conn) n++;
  return n;
}

export function attachUser(userId: string, name: string, conn: WebSocket) {
  const code = userRoom.get(userId);
  const room = code ? rooms.get(code) : undefined;
  if (!room) {
    send(conn, { t: "room", room: null });
    return;
  }
  const color: Color = room.seats.w?.userId === userId ? "w" : "b";
  const seat = room.seats[color]!;
  seat.conn = conn;
  seat.offlineSince = undefined;
  seat.name = name;
  broadcast(room);
}

export function detachConn(userId: string, conn: WebSocket) {
  // remove from queues
  for (const [g, q] of queues) queues.set(g, q.filter((e) => e.conn !== conn));
  const code = userRoom.get(userId);
  const room = code ? rooms.get(code) : undefined;
  if (!room) return;
  for (const c of ["w", "b"] as Color[]) {
    const seat = room.seats[c];
    if (seat && seat.userId === userId && seat.conn === conn) {
      seat.conn = undefined;
      seat.offlineSince = Date.now();
    }
  }
  broadcast(room);
}

function ensureFree(userId: string): string | null {
  const code = userRoom.get(userId);
  if (code) {
    const r = rooms.get(code);
    if (r && r.phase !== "over") return "You are already in a game";
    leave(userId);
  }
  return null;
}

export function createRoom(userId: string, name: string, conn: WebSocket, game: string) {
  if (!PLUGINS[game]) return send(conn, { t: "err", msg: "Online play is not available for this game" });
  const busy = ensureFree(userId);
  if (busy) return send(conn, { t: "err", msg: busy });
  const room = mk(game);
  room.seats.w = { userId, name, conn };
  userRoom.set(userId, room.code);
  broadcast(room);
}

function mk(game: string): Room {
  const room: Room = {
    code: newCode(), game, seats: {}, state: PLUGINS[game].initial(), phase: "waiting", result: null,
    delta: { w: 0, b: 0 }, last: null, seq: 0, createdAt: Date.now(), left: new Set(),
  };
  rooms.set(room.code, room);
  return room;
}

export function joinRoom(userId: string, name: string, conn: WebSocket, code: string) {
  const busy = ensureFree(userId);
  if (busy) return send(conn, { t: "err", msg: busy });
  const room = rooms.get(String(code).toUpperCase());
  if (!room || room.phase !== "waiting") return send(conn, { t: "err", msg: "Room not found or already started" });
  if (room.seats.w?.userId === userId) return send(conn, { t: "err", msg: "You created this room" });
  room.seats.b = { userId, name, conn };
  room.phase = "playing";
  userRoom.set(userId, room.code);
  broadcast(room);
}

export function enqueue(userId: string, name: string, conn: WebSocket, game: string) {
  if (!PLUGINS[game]) return send(conn, { t: "err", msg: "Online play is not available for this game" });
  const busy = ensureFree(userId);
  if (busy) return send(conn, { t: "err", msg: busy });
  const q = (queues.get(game) ?? []).filter((e) => e.userId !== userId && e.conn.readyState === 1);
  const other = q.shift();
  if (other) {
    queues.set(game, q);
    const room = mk(game);
    const flip = crypto.randomInt(0, 2) === 1;
    const a = { userId: other.userId, name: getUser(other.userId)?.username ?? "?", conn: other.conn };
    const b = { userId, name, conn };
    room.seats.w = flip ? b : a;
    room.seats.b = flip ? a : b;
    room.phase = "playing";
    userRoom.set(a.userId, room.code);
    userRoom.set(b.userId, room.code);
    broadcast(room);
  } else {
    q.push({ userId, conn });
    queues.set(game, q);
    send(conn, { t: "queued" });
  }
}

export function dequeue(userId: string, conn: WebSocket) {
  for (const [g, q] of queues) queues.set(g, q.filter((e) => e.userId !== userId));
  send(conn, { t: "unqueued" });
}

export function move(userId: string, conn: WebSocket, mv: unknown) {
  const room = rooms.get(userRoom.get(userId) ?? "");
  if (!room || room.phase !== "playing") return send(conn, { t: "err", msg: "No active game" });
  const color: Color = room.seats.w?.userId === userId ? "w" : "b";
  const plugin = PLUGINS[room.game];
  const r = plugin.apply(room.state, mv, color);
  if ("error" in r) {
    send(conn, { t: "err", msg: r.error });
    send(conn, { t: "room", room: snapshot(room, color) }); // resync client
    return;
  }
  room.state = r.state;
  room.last = r.last;
  room.seq++;
  const res = plugin.result(room.state);
  if (res) finish(room, res.winner);
  else broadcast(room);
}

export function resign(userId: string) {
  const room = rooms.get(userRoom.get(userId) ?? "");
  if (!room || room.phase !== "playing") return;
  const color: Color = room.seats.w?.userId === userId ? "w" : "b";
  finish(room, color === "w" ? "b" : "w");
}

export function leave(userId: string) {
  const code = userRoom.get(userId);
  const room = code ? rooms.get(code) : undefined;
  userRoom.delete(userId);
  if (!room) return;
  if (room.phase === "playing") {
    const color: Color = room.seats.w?.userId === userId ? "w" : "b";
    finish(room, color === "w" ? "b" : "w");
  }
  room.left.add(userId);
  if (room.phase === "waiting") {
    rooms.delete(room.code);
  } else if (room.left.size >= 2 || [room.seats.w, room.seats.b].every((s) => !s || room.left.has(s.userId))) {
    rooms.delete(room.code);
  }
}

function finish(room: Room, winner: Color | "draw") {
  room.phase = "over";
  room.result = winner;
  const w = room.seats.w!, b = room.seats.b!;
  const uw = getUser(w.userId), ub = getUser(b.userId);
  if (uw && ub) {
    const g = room.game;
    const sw = winner === "w" ? 1 : winner === "draw" ? 0.5 : 0;
    const dw = eloDelta(uw.ratings[g], ub.ratings[g], sw);
    const db_ = eloDelta(ub.ratings[g], uw.ratings[g], 1 - sw);
    uw.ratings[g] += dw;
    ub.ratings[g] += db_;
    room.delta = { w: dw, b: db_ };
    const bump = (u: typeof uw, won: boolean, draw: boolean) => {
      const s = u.stats[g];
      if (draw) s.draws++; else if (won) s.wins++; else s.losses++;
    };
    bump(uw, winner === "w", winner === "draw");
    bump(ub, winner === "b", winner === "draw");
    saveUser();
    addMatch({ id: crypto.randomUUID(), game: g, white: uw.id, black: ub.id, result: String(winner), ts: Date.now() });
  }
  broadcast(room);
}

/** Housekeeping: forfeit long-disconnected players, drop stale rooms. Call on an interval. */
export function sweep() {
  const now = Date.now();
  for (const room of [...rooms.values()]) {
    if (room.phase === "waiting" && now - room.createdAt > WAITING_TTL_MS) {
      for (const s of [room.seats.w, room.seats.b]) if (s) userRoom.delete(s.userId);
      rooms.delete(room.code);
      continue;
    }
    if (room.phase === "playing") {
      for (const c of ["w", "b"] as Color[]) {
        const s = room.seats[c];
        if (s && s.offlineSince && now - s.offlineSince > FORFEIT_AFTER_MS) {
          finish(room, c === "w" ? "b" : "w");
          break;
        }
      }
    }
    if (room.phase === "over") {
      const stale = [room.seats.w, room.seats.b].every((s) => !s || (!s.conn && s.offlineSince && now - s.offlineSince > 600_000));
      if (stale) {
        for (const s of [room.seats.w, room.seats.b]) if (s) userRoom.delete(s.userId);
        rooms.delete(room.code);
      }
    }
  }
}
