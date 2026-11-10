#!/data/data/com.termux/files/usr/bin/bash
# Start the NEXPLAY server in the background (survives closing the session while Termux runs).
cd "$(dirname "$0")/../backend" || exit 1
if [ ! -f dist/server.js ]; then echo "Аввал: bash scripts/termux-install.sh"; exit 1; fi
if [ -f server.pid ] && kill -0 "$(cat server.pid)" 2>/dev/null; then
  echo "Сервер аллакай кор мекунад (PID $(cat server.pid))."; exit 0
fi
command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock
nohup node dist/server.js > server.log 2>&1 &
echo $! > server.pid
sleep 2
if kill -0 "$(cat server.pid)" 2>/dev/null; then
  PORT=$(grep -E '^PORT=' .env 2>/dev/null | cut -d= -f2); PORT=${PORT:-8080}
  IP=$(ifconfig 2>/dev/null | grep -Eo 'inet (192\.168|10\.|172\.)[0-9.]+' | head -1 | awk '{print $2}')
  echo "Сервер кор мекунад. Лог: backend/server.log"
  echo "Дар як Wi-Fi: ${IP:-<IP-и телефон>}:$PORT  (ин суроғаро дар барнома нависед)"
else
  echo "Сервер оғоз нашуд. Нигоҳ кунед: backend/server.log"; tail -n 20 server.log; rm -f server.pid; exit 1
fi
