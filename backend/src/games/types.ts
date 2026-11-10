/** Server-side game plugin. Add a new file + register it in games/index.ts to support a new online game. */
export interface GameResult {
  winner: "w" | "b" | "draw";
}
export interface GamePlugin {
  /** Initial wire state string. */
  initial(): string;
  /** Whose turn is it, from the wire state. */
  turn(state: string): "w" | "b";
  /** Validate and apply a move. Returns new state + short "last move" text, or an error. */
  apply(state: string, move: any, side: "w" | "b"): { state: string; last: string } | { error: string };
  /** Game result if finished. */
  result(state: string): GameResult | null;
}
