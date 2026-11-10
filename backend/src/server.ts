import http from "http";
import { WebSocketServer, WebSocket } from "ws";
import { config } from "./config";
import { allUsers, flush, GAMES, getUser, initDb, userMatches } from "./db";
import { login, register, signToken, validateCreds, verifyToken } from "./auth";
import * as rooms from "./rooms";

initDb();

// ---------- tiny rate limiter ----------
const buckets = new Map<string, { n: number; reset: number }>();
function allow(key: string, limit: number, windowMs: number): boolean {
  const now = Date.now();
  const b = buckets.get(key);
  if (!b || now > b.reset) {
    buckets.set(key, { n: 1, reset: now + windowMs });
    return true;
  }
  b.n++;
  return b.n <= limit;
}
setInterval(() => {
  const now = Date.now();
  for (const [k, b] of buckets) if (now > b.reset) buckets.delete(k);
}, 60_000).unref();

// ---------- HTTP ----------
function json(res: http.ServerResponse, code: number, body: unknown) {
  res.writeHead(code, { "Content-Type": "application/json", "Cache-Control": "no-store" });
  res.end(JSON.stringify(body));
}

function readBody(req: http.IncomingMessage): Promise<any> {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks: Buffer[] = [];
    req.on("data", (c: Buffer) => {
      size += c.length;
      if (size > 4096) { reject(new Error("Body too large")); req.destroy(); return; }
      chunks.push(c);
    });
    req.on("end", () => {
      try { resolve(chunks.length ? JSON.parse(Buffer.concat(chunks).toString("utf8")) : {}); }
      catch { reject(new Error("Invalid JSON")); }
    });
    req.on("error", reject);
  });
}

const ipOf = (req: http.IncomingMessage) => req.socket.remoteAddress || "unknown";

function leaderboard(game: string) {
  const rows = allUsers().map((u) => {
    let wins = 0, games = 0, rating: number;
    if (game === "overall") {
      const played = GAMES.filter((g) => u.stats[g] && u.stats[g].wins + u.stats[g].losses + u.stats[g].draws > 0);
      for (const g of GAMES) { wins += u.stats[g]?.wins ?? 0; games += (u.stats[g]?.wins ?? 0) + (u.stats[g]?.losses ?? 0) + (u.stats[g]?.draws ?? 0); }
      rating = played.length ? Math.round(played.reduce((a, g) => a + u.ratings[g], 0) / played.length) : 1000;
    } else {
      const s = u.stats[game] ?? { wins: 0, losses: 0, draws: 0 };
      wins = s.wins; games = s.wins + s.losses + s.draws; rating = u.ratings[game] ?? 1000;
    }
    return { username: u.username, avatar: u.avatar, rating, wins, games, winRate: games ? wins / games : 0 };
  }).filter((r) => r.games > 0);
  rows.sort((a, b) => b.rating - a.rating || b.wins - a.wins);
  return rows.slice(0, 50);
}

const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url || "/", "http://localhost");
    const path = url.pathname;
    if (req.method === "GET" && path === "/api/health") return json(res, 200, { ok: true, online: wss.clients.size });
    if (req.method === "POST" && (path === "/api/register" || path === "/api/login")) {
      if (!allow("auth:" + ipOf(req), 10, 60_000)) return json(res, 429, { error: "Too many attempts, try again later" });
      const body = await readBody(req);
      const bad = validateCreds(body.username, body.password);
      if (bad) return json(res, 400, { error: bad });
      try {
        const u = path === "/api/register" ? await register(body.username, body.password) : await login(body.username, body.password);
        return json(res, 200, { token: signToken(u), username: u.username, avatar: u.avatar });
      } catch (e: any) {
        return json(res, path === "/api/register" ? 409 : 401, { error: e.message });
      }
    }
    if (req.method === "GET" && path === "/api/leaderboard") {
      const game = url.searchParams.get("game") || "overall";
      if (game !== "overall" && !GAMES.includes(game)) return json(res, 400, { error: "Unknown game" });
      return json(res, 200, { game, players: leaderboard(game) });
    }
    if (req.method === "GET" && path === "/api/me") {
      const u = verifyToken((req.headers.authorization || "").replace(/^Bearer /, ""));
      if (!u) return json(res, 401, { error: "Unauthorized" });
      return json(res, 200, { username: u.username, avatar: u.avatar, ratings: u.ratings, stats: u.stats, matches: userMatches(u.id) });
    }
    json(res, 404, { error: "Not found" });
  } catch (e: any) {
    json(res, 400, { error: e.message || "Bad request" });
  }
});

// ---------- WebSocket ----------
const wss = new WebSocketServer({ server, path: "/ws", maxPayload: 4096 });
const connByUser = new Map<string, WebSocket>();

wss.on("connection", (ws, req) => {
  const ip = ipOf(req);
  let userId: string | null = null;
  let name = "";
  const authTimer = setTimeout(() => { if (!userId) ws.close(4001, "auth timeout"); }, 10_000);

  ws.on("message", (data) => {
    if (!allow("ws:" + (userId ?? ip) + ":" + (ws as any)._id, 40, 10_000)) {
      ws.send(JSON.stringify({ t: "err", msg: "Too many messages" }));
      return;
    }
    let m: any;
    try { m = JSON.parse(data.toString()); } catch { return; }
    if (typeof m !== "object" || m === null) return;

    if (m.t === "auth") {
      if (!allow("wsauth:" + ip, 20, 60_000)) return ws.close(4003, "rate limited");
      const u = verifyToken(String(m.token || ""));
      if (!u) { ws.send(JSON.stringify({ t: "err", msg: "Invalid token, please log in again" })); return ws.close(4002, "bad token"); }
      const prev = connByUser.get(u.id);
      if (prev && prev !== ws) prev.close(4000, "replaced by a new connection");
      connByUser.set(u.id, ws);
      userId = u.id;
      name = u.username;
      clearTimeout(authTimer);
      ws.send(JSON.stringify({ t: "authed", user: { name: u.username, avatar: u.avatar } }));
      rooms.attachUser(u.id, u.username, ws);
      return;
    }
    if (!userId) return;
    switch (m.t) {
      case "create": rooms.createRoom(userId, name, ws, String(m.game)); break;
      case "join": rooms.joinRoom(userId, name, ws, String(m.code)); break;
      case "queue": rooms.enqueue(userId, name, ws, String(m.game)); break;
      case "unqueue": rooms.dequeue(userId, ws); break;
      case "move": rooms.move(userId, ws, m.move); break;
      case "resign": rooms.resign(userId); break;
      case "leave": rooms.leave(userId); break;
      default: break;
    }
  });

  ws.on("close", () => {
    clearTimeout(authTimer);
    if (userId) {
      if (connByUser.get(userId) === ws) connByUser.delete(userId);
      rooms.detachConn(userId, ws);
    }
  });
  ws.on("error", () => {});
});

setInterval(() => rooms.sweep(), 10_000).unref();

server.listen(config.port, config.host, () => {
  console.log(`NEXPLAY server listening on http://${config.host}:${config.port}  (ws path /ws)`);
});

function shutdown() {
  console.log("Shutting down…");
  try { flush(); } catch {}
  process.exit(0);
}
process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);
