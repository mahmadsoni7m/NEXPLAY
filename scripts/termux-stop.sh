#!/data/data/com.termux/files/usr/bin/bash
cd "$(dirname "$0")/../backend" || exit 1
if [ -f server.pid ] && kill -0 "$(cat server.pid)" 2>/dev/null; then
  kill "$(cat server.pid)" && echo "Сервер қатъ шуд."
else
  echo "Сервер кор намекунад."
fi
rm -f server.pid
command -v termux-wake-unlock >/dev/null 2>&1 && termux-wake-unlock
exit 0
