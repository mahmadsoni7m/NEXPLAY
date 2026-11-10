import bcrypt from "bcryptjs";
import jwt from "jsonwebtoken";
import crypto from "crypto";
import { config } from "./config";
import { createUser, findUserByName, getUser, GAMES, UserRow } from "./db";

const NAME_RE = /^[A-Za-z0-9_\-\u0400-\u04FF]{3,20}$/;

export function validateCreds(username: unknown, password: unknown): string | null {
  if (typeof username !== "string" || !NAME_RE.test(username)) return "Username: 3-20 letters, digits, _ or -";
  if (typeof password !== "string" || password.length < 6 || password.length > 100) return "Password must be 6-100 characters";
  return null;
}

export async function register(username: string, password: string): Promise<UserRow> {
  if (findUserByName(username)) throw new Error("Username already taken");
  const ratings: Record<string, number> = {};
  const stats: UserRow["stats"] = {};
  for (const g of GAMES) {
    ratings[g] = 1000;
    stats[g] = { wins: 0, losses: 0, draws: 0 };
  }
  const u: UserRow = {
    id: crypto.randomUUID(),
    username,
    usernameLower: username.toLowerCase(),
    passHash: await bcrypt.hash(password, 10),
    avatar: crypto.randomInt(0, 8),
    createdAt: Date.now(),
    ratings,
    stats,
  };
  createUser(u);
  return u;
}

export async function login(username: string, password: string): Promise<UserRow> {
  const u = findUserByName(username);
  // Same error for unknown user / wrong password (no user enumeration).
  if (!u || !(await bcrypt.compare(password, u.passHash))) throw new Error("Invalid username or password");
  return u;
}

export function signToken(u: UserRow): string {
  return jwt.sign({ sub: u.id }, config.jwtSecret, { expiresIn: config.jwtExpiresIn as jwt.SignOptions["expiresIn"] });
}

export function verifyToken(token: string): UserRow | null {
  try {
    const p = jwt.verify(token, config.jwtSecret) as { sub?: string };
    return p.sub ? getUser(p.sub) ?? null : null;
  } catch {
    return null;
  }
}
