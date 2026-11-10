import { Chess } from "chess.js";
import { GamePlugin } from "./types";

/** Chess via the chess.js rules engine. Wire state = FEN. */
export const chess: GamePlugin = {
  initial: () => new Chess().fen(),
  turn: (s) => (s.split(" ")[1] === "w" ? "w" : "b"),
  apply(s, move, side) {
    const g = new Chess(s);
    if (g.turn() !== side) return { error: "Not your turn" };
    const from = String(move?.from ?? "");
    const to = String(move?.to ?? "");
    const promo = move?.promo ? String(move.promo).toLowerCase() : undefined;
    if (!/^[a-h][1-8]$/.test(from) || !/^[a-h][1-8]$/.test(to)) return { error: "Malformed move" };
    if (promo && !/^[nbrq]$/.test(promo)) return { error: "Malformed move" };
    try {
      const m = g.move({ from, to, promotion: promo });
      return { state: g.fen(), last: m.from + m.to + (m.promotion ?? "") };
    } catch {
      return { error: "Illegal move" };
    }
  },
  result(s) {
    const g = new Chess(s);
    if (g.isCheckmate()) return { winner: g.turn() === "w" ? "b" : "w" };
    if (g.isDraw() || g.isStalemate() || g.isInsufficientMaterial() || g.isThreefoldRepetition()) return { winner: "draw" };
    return null;
  },
};
