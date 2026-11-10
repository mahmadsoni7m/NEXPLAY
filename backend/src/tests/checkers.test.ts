import test from "node:test";
import assert from "node:assert";
import { initialState, legalMoves, checkers } from "../games/checkers";

test("initial position has 7 legal moves for white", () => {
  assert.strictEqual(legalMoves(initialState()).length, 7);
});

test("server accepts a legal move and rejects an illegal one", () => {
  const s = checkers.initial();
  const ok = checkers.apply(s, { path: [superFrom(), superTo()] }, "w");
  assert.ok("state" in ok);
  const bad = checkers.apply(s, { path: [0, 9] }, "w");
  assert.ok("error" in bad);
  const wrongTurn = checkers.apply(s, { path: [superFrom(), superTo()] }, "b");
  assert.ok("error" in wrongTurn);
});

function superFrom() { return legalMoves(initialState())[0].path[0]; }
function superTo() { return legalMoves(initialState())[0].path[1]; }

test("mandatory capture is enforced", () => {
  const cells = new Array(64).fill(0);
  cells[5 * 8 + 2] = 1; // white man at row5 col2
  cells[4 * 8 + 3] = 2; // black man adjacent diag
  cells[7 * 8 + 0] = 1; // another white man with simple moves
  const moves = legalMoves({ cells, turn: 1, quiet: 0 });
  assert.ok(moves.every((m) => m.captured.length > 0));
  assert.strictEqual(moves.length, 1);
});
