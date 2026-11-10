/** Standard Elo. score: 1 win, 0.5 draw, 0 loss. */
export function eloDelta(ra: number, rb: number, score: number, k = 32): number {
  const expected = 1 / (1 + Math.pow(10, (rb - ra) / 400));
  return Math.round(k * (score - expected));
}
