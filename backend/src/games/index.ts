import { GamePlugin } from "./types";
import { checkers } from "./checkers";
import { chess } from "./chess";

/** Games with server-side validation. Backgammon and domino are not registered (client-only, local/AI). */
export const PLUGINS: Record<string, GamePlugin> = { checkers, chess };
