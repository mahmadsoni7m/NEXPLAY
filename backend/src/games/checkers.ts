import { GamePlugin } from "./types";

/** Russian draughts, identical rules/wire format to the Android client (64 chars + turn char). */
const EMPTY = 0, W = 1, B = 2, WK = 3, BK = 4;
const DIRS = [[-1, -1], [-1, 1], [1, -1], [1, 1]];

const owner = (p: number) => (p === W || p === WK ? W : p === B || p === BK ? B : 0);
const isKing = (p: number) => p === WK || p === BK;
const inB = (r: number, c: number) => r >= 0 && r < 8 && c >= 0 && c < 8;

export interface CkMove { path: number[]; captured: number[] }
export interface CkState { cells: number[]; turn: number; quiet: number }

export function fromWire(s: string, quiet = 0): CkState {
  const map: Record<string, number> = { ".": EMPTY, w: W, b: B, W: WK, B: BK };
  const cells: number[] = [];
  for (let i = 0; i < 64; i++) cells.push(map[s[i]] ?? EMPTY);
  return { cells, turn: s[64] === "w" ? W : B, quiet };
}
export function toWire(st: CkState): string {
  const ch = [".", "w", "b", "W", "B"];
  return st.cells.map((c) => ch[c]).join("") + (st.turn === W ? "w" : "b");
}
export function initialState(): CkState {
  const cells = new Array(64).fill(EMPTY);
  for (let r = 0; r < 8; r++) for (let c = 0; c < 8; c++) {
    if ((r + c) % 2 === 1) {
      if (r <= 2) cells[r * 8 + c] = B;
      if (r >= 5) cells[r * 8 + c] = W;
    }
  }
  return { cells, turn: W, quiet: 0 };
}

function jumps(st: CkState, origin: number, pos: number, king: boolean, cap: number[]): [number, number][] {
  const res: [number, number][] = [];
  const free = (sq: number) => st.cells[sq] === EMPTY || sq === origin;
  const enemy = (p: number) => p !== EMPTY && owner(p) !== st.turn;
  const r0 = Math.floor(pos / 8), c0 = pos % 8;
  for (const [dr, dc] of DIRS) {
    let r = r0 + dr, c = c0 + dc;
    if (!king) {
      if (!inB(r, c)) continue;
      const sq = r * 8 + c;
      if (enemy(st.cells[sq]) && !cap.includes(sq)) {
        const r2 = r + dr, c2 = c + dc;
        if (inB(r2, c2) && free(r2 * 8 + c2)) res.push([sq, r2 * 8 + c2]);
      }
    } else {
      while (inB(r, c) && free(r * 8 + c)) { r += dr; c += dc; }
      if (!inB(r, c)) continue;
      const sq = r * 8 + c;
      if (enemy(st.cells[sq]) && !cap.includes(sq)) {
        let r2 = r + dr, c2 = c + dc;
        while (inB(r2, c2) && free(r2 * 8 + c2)) { res.push([sq, r2 * 8 + c2]); r2 += dr; c2 += dc; }
      }
    }
  }
  return res;
}

export function legalMoves(st: CkState): CkMove[] {
  const promotesAt = (sq: number) => (st.turn === W && Math.floor(sq / 8) === 0) || (st.turn === B && Math.floor(sq / 8) === 7);
  const caps: CkMove[] = [];
  const dfs = (origin: number, pos: number, king: boolean, path: number[], cap: number[]) => {
    const js = jumps(st, origin, pos, king, cap);
    if (js.length === 0) {
      if (cap.length) caps.push({ path: [...path], captured: [...cap] });
      return;
    }
    for (const sq of [...new Set(js.map((j) => j[0]))]) {
      const lands = js.filter((j) => j[0] === sq).map((j) => j[1]);
      const newCap = [...cap, sq];
      let use = lands;
      if (king) {
        const cont = lands.filter((l) => jumps(st, origin, l, true, newCap).length > 0);
        if (cont.length) use = cont;
      }
      for (const l of use) {
        path.push(l);
        dfs(origin, l, king || promotesAt(l), path, newCap);
        path.pop();
      }
    }
  };
  for (let i = 0; i < 64; i++) {
    const p = st.cells[i];
    if (owner(p) === st.turn) dfs(i, i, isKing(p), [i], []);
  }
  if (caps.length) return caps;
  const out: CkMove[] = [];
  for (let i = 0; i < 64; i++) {
    const p = st.cells[i];
    if (owner(p) !== st.turn) continue;
    const r = Math.floor(i / 8), c = i % 8;
    if (isKing(p)) {
      for (const [dr, dc] of DIRS) {
        let rr = r + dr, cc = c + dc;
        while (inB(rr, cc) && st.cells[rr * 8 + cc] === EMPTY) { out.push({ path: [i, rr * 8 + cc], captured: [] }); rr += dr; cc += dc; }
      }
    } else {
      const dr = st.turn === W ? -1 : 1;
      for (const dc of [-1, 1]) {
        const rr = r + dr, cc = c + dc;
        if (inB(rr, cc) && st.cells[rr * 8 + cc] === EMPTY) out.push({ path: [i, rr * 8 + cc], captured: [] });
      }
    }
  }
  return out;
}

export function applyMove(st: CkState, m: CkMove): CkState {
  const cells = st.cells.slice();
  let piece = cells[m.path[0]];
  const to = m.path[m.path.length - 1];
  cells[m.path[0]] = EMPTY;
  for (const c of m.captured) cells[c] = EMPTY;
  const wasMan = !isKing(piece);
  const promo = (sq: number) => (st.turn === W && Math.floor(sq / 8) === 0) || (st.turn === B && Math.floor(sq / 8) === 7);
  if (wasMan && m.path.some(promo)) piece = st.turn === W ? WK : BK;
  cells[to] = piece;
  const quiet = m.captured.length || wasMan ? 0 : st.quiet + 1;
  return { cells, turn: st.turn === W ? B : W, quiet };
}

export function resultOf(st: CkState): "w" | "b" | "draw" | null {
  if (legalMoves(st).length === 0) return st.turn === W ? "b" : "w";
  if (st.quiet >= 30) return "draw";
  return null;
}

/**
 * The wire state carries 64 cells + turn. The quiet-move counter is kept in a suffix ":<n>" on the server
 * only; the client parser reads just the first 65 characters.
 */
const split = (s: string): [string, number] => {
  const [b, q] = s.split(":");
  return [b, parseInt(q || "0", 10) || 0];
};

export const checkers: GamePlugin = {
  initial: () => toWire(initialState()) + ":0",
  turn: (s) => (split(s)[0][64] === "w" ? "w" : "b"),
  apply(s, move, side) {
    const [wire, q] = split(s);
    const st = fromWire(wire, q);
    if ((st.turn === W ? "w" : "b") !== side) return { error: "Not your turn" };
    const path: unknown = move?.path;
    if (!Array.isArray(path) || path.length < 2 || path.length > 40 || !path.every((n) => Number.isInteger(n) && n >= 0 && n < 64)) {
      return { error: "Malformed move" };
    }
    const legal = legalMoves(st).find((m) => m.path.length === path.length && m.path.every((v, i) => v === path[i]));
    if (!legal) return { error: "Illegal move" };
    const next = applyMove(st, legal);
    return { state: toWire(next) + ":" + next.quiet, last: legal.path.join("-") };
  },
  result(s) {
    const [wire, q] = split(s);
    const r = resultOf(fromWire(wire, q));
    return r ? { winner: r } : null;
  },
};
