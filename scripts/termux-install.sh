#!/data/data/com.termux/files/usr/bin/bash
# NEXPLAY server installer for Termux. Run once:  bash scripts/termux-install.sh
set -e
cd "$(dirname "$0")/../backend"

echo "==> Навсозии баста ҳо (pkg update)..."
pkg update -y && pkg upgrade -y
echo "==> Насб кардани Node.js..."
pkg install -y nodejs-lts || pkg install -y nodejs
node -v
npm -v

echo "==> Насб кардани вобастагиҳо (npm install)..."
npm install

if [ ! -f .env ]; then
  cp .env.example .env
  SECRET=$(node -e "console.log(require('crypto').randomBytes(48).toString('hex'))")
  # Put a freshly generated random secret into .env (never hardcode secrets in code).
  sed -i "s/^JWT_SECRET=.*/JWT_SECRET=$SECRET/" .env
  echo "==> Файли .env сохта шуд (JWT_SECRET тасодуфӣ)."
fi

echo "==> Сохтани сервер (npm run build)..."
npm run build
echo
echo "Тайёр! Сервер оғоз кардан: bash scripts/termux-start.sh"
