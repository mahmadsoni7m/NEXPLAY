import fs from "fs";
import path from "path";

/** Minimal .env loader (no external dependency, works in Termux). */
function loadEnvFile(file: string): void {
  if (!fs.existsSync(file)) return;
  for (const line of fs.readFileSync(file, "utf8").split(/\r?\n/)) {
    const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)\s*$/);
    if (!m) continue;
    let v = m[2];
    if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) v = v.slice(1, -1);
    if (process.env[m[1]] === undefined) process.env[m[1]] = v;
  }
}
loadEnvFile(path.resolve(process.cwd(), ".env"));

const secret = process.env.JWT_SECRET || "";
if (secret.length < 24 || secret.startsWith("CHANGE_ME")) {
  console.error("FATAL: JWT_SECRET is missing or too weak. Copy .env.example to .env and set a long random JWT_SECRET.");
  process.exit(1);
}

export const config = {
  port: parseInt(process.env.PORT || "8080", 10),
  host: process.env.HOST || "0.0.0.0",
  jwtSecret: secret,
  jwtExpiresIn: process.env.JWT_EXPIRES_IN || "30d",
  dataDir: path.resolve(process.cwd(), process.env.DATA_DIR || "./data"),
};
