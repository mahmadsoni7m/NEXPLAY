# NEXPLAY — Бозӣ кун. Рақобат кун. Ғолиб шав.

Платформаи бозиҳои Android: **Шашка, Шоҳмот, Нард, Домино**. Забонҳо: Тоҷикӣ (асосӣ), Русӣ, English.
Package: `com.nexplay.game` · Kotlin + Jetpack Compose + Material 3 · Сервер: Node.js + TypeScript + WebSocket.

## ⚠️ Бо ростӣ: чӣ тайёр аст ва чӣ санҷида нашудааст
- Лоиҳа дар муҳити бе Android SDK ва бе интернет навишта шуд. **Kotlin ва Gradle дар ин ҷо коммпайл нашуданд.** Аввалин сохтани воқеӣ дар GitHub Actions мешавад. Агар хатогии коммпайл баромад, матни хатогиро ба ман фиристед — ислоҳ мекунам.
- Мантиқи шашкаи сервер (TypeScript) бо тестҳо санҷида шуд (3 тест гузашт). Муҳаррикҳои Kotlin бо тестҳои воҳид (`EnginesTest.kt`) пурра карда шудаанд, ки дар Actions иҷро мешаванд.
- `gradle-wrapper.jar` бинарист ва дар ин ҷо сохта нашуд; workflow онро худаш месозад (қадами «Create Gradle wrapper if missing»).
- **Онлайн** (ҳуҷра, код, matchmaking, reconnect, санҷиши ҳаракат дар сервер) ҳозир барои **Шашка ва Шоҳмот** кор мекунад. Нард ва Домино: бозӣ бо компютер ва ду нафар дар як дастгоҳ; онлайни онҳо ҳанӯз нест (сервер плагин-сохтор дорад — `backend/src/games/`).
- Базаи сервер: файли JSON бо migration (барои Termux, бе модулҳои native). Дар телефон: DataStore (Room истифода нашуд, то хатогии KSP нашавад).
- Wi-Fi/LAN: ҳамон протоколи сервер; суроғаи сервер (масалан `192.168.1.5:8080`) дар барнома навишта мешавад.

## 0. Тарҳи нав (геймерӣ)
Splash бо анимация, фони «арена» бо зарраҳои neon, панелҳои HUD бо кунҷҳои бурида, тугмаҳои дурахшон бо ларзиш (haptic), навигатсияи бозӣ, тахтаи дурахшон, анимацияи пайдошавии мӯҳра, экрани ғалаба бо конфетти, **тангаҳо**, **сандуқи рӯзона бо силсилаи рӯзҳо**, **8 дастовард**, «Бозии зуд».

## 1. Танҳо 6 қадам (барои навомӯз)
1. ZIP-ро кушоед (Extract).
2. Дар GitHub repository-и нав созед (**New repository**).
3. **Ҳамаи файлҳоро** (аз ҷумла папкаи `.github`) ба repository бор кунед (Add file → Upload files). Диққат: папкаи `.github` бояд бо файли `workflows/android.yml` бошад.
4. Ба табаки **Actions** гузаред. Workflow «Android CI» худаш оғоз мешавад (ё **Run workflow** пахш кунед).
5. 5–10 дақиқа интизор шавед, то сабз шавад ✅.
6. Дар поёни саҳифаи run, қисми **Artifacts** → **NEXPLAY-ANDROID-APK** → зеркашӣ кунед, ZIP-ро кушоед, `app-debug.apk`-ро дар телефон насб кунед (иҷозаи «насб аз манбаи номаълум» лозим).

Агар workflow сурх шуд: run-ро кушоед, қадами сурхро пахш кунед, матни хатогиро нусха кунед.

## 2. Сервер дар Termux (ихтиёрӣ, барои бозии онлайн)
```bash
pkg install -y git unzip      # агар лозим бошад
cd NEXPLAY                    # папкаи кушодашуда
bash scripts/termux-install.sh   # Node.js, npm install, .env (JWT_SECRET тасодуфӣ), build
bash scripts/termux-start.sh     # оғоз (дар пасзамина)
bash scripts/termux-stop.sh      # қатъ
```
Лог: `backend/server.log`. Танзимот: `backend/.env` (PORT, JWT_SECRET, DATA_DIR). Парол/калидҳо ҳеҷ гоҳ дар код нестанд.

### Пайваст шудан аз барнома
1. Барнома → Бозӣ (масалан Шашка) → **Онлайн / Wi-Fi** → суроғаро нависед: `IP:8080`.
2. **Қайд шудан** (register) ё **Ворид шудан** → **Пайваст шудан**.
3. Ҳуҷра созед ва кодро ба дӯст диҳед, ё «Ҷустуҷӯи ҳариф».

### Дар як Wi-Fi (LAN)
Ҳарду телефон ба як Wi-Fi пайваст бошанд; суроғаи IP-и телефони сервер (`termux-start.sh` онро нишон медиҳад).

### Аз интернет — муҳим
Сервер дар телефон аз интернет **танҳо вақте** дастрас аст, ки: (1) провайдер IP-и «сафед»/ҷамъиятӣ диҳад (на CGNAT), (2) дар роутер port forwarding ба телефон танзим шавад, (3) файрвол/роутер порти 8080-ро нигоҳ надорад, (4) телефон бедор бошад ва Termux ёфт нашавад (`termux-wake-lock`). Бисёр вақт ин кор намекунад. **Алтернативаҳои бехатар:**
- **Cloudflare Tunnel** ё **Tailscale** (бе кушодани порт, бо HTTPS/VPN).
- **VPS** (Hetzner, DigitalOcean ва ғ.) ё Render/Fly.io: `npm ci && npm run build && npm start`, пеши он reverse proxy бо HTTPS.
- Барои HTTPS суроғаро чун `https://server.com` нависед — барнома `wss://` истифода мебарад.
⚠ Барнома HTTP-и оддиро барои LAN/Termux иҷозат медиҳад. Дар интернет ҳатман HTTPS истифода баред, вагарна паролҳо кушода мераванд.

## 3. Сохтор
```
NEXPLAY/
├── app/                      Android (Kotlin, Compose, Material 3)
│   └── src/main/java/com/nexplay/game/
│       ├── core/ui, core/nav     компонентҳо, мавзӯъ, GameRegistry, навигатсия
│       ├── data/                 DataStore, Repository, Net (REST + WebSocket), Strings (3 забон)
│       └── feature/{home,checkers,chess,backgammon,domino,online,leaderboard,profile,settings}
├── backend/                  Node.js + TypeScript (HTTP + WebSocket)
├── scripts/                  termux-install.sh, termux-start.sh, termux-stop.sh
├── .github/workflows/android.yml
└── README.md
```
Илова кардани бозии нав: экран созед → як сатр дар `core/nav/GameRegistry.kt` илова кунед (+ плагин дар `backend/src/games/` барои онлайн).

## 4. Қоидаҳо ва AI
- **Шашка** (русӣ): гирифтан ҳатмист, гирифтани пай дар пай, шоҳ (дамка) парвозкунанда, навбати 30 ҳаракат бе гирифтан = дураг. AI: Minimax + Alpha-Beta, чуқурӣ ва вақт аз сатҳ вобаста; сатҳи «Устод» = **SONI**.
- **Шоҳмот**: ҳамаи ҳаракатҳои қонунӣ, шоҳ/мот, рокировка, en passant, табдили пиёда, пат, маводи нокифоя, қоидаи 50 ҳаракат, се такрор. AI: alpha-beta, 4 сатҳ.
- **Нард**: зар, зарбаи ҷуфт, қоидаи «ҳамаи зарҳоро истифода бар», бар (bar), баровардан, задан. AI: 4 сатҳ. (Гаммон/кубаи дучанд нест.)
- **Домино** (double-six): навбат, бозор, пас, холҳо то 100, AI 4 сатҳ, режими ду нафар бо пӯшиши махфӣ.

## 5. Бехатарии сервер
bcrypt барои паролҳо, JWT, `JWT_SECRET` аз `.env` (сервер бе он оғоз намешавад), маҳдудияти суръат (HTTP ва WebSocket), андозаи пайғом ≤4 КБ, санҷиши навбат ва қонунӣ будани ҳар ҳаракат дар сервер, як пайвастшавӣ барои як корбар, фориғ шудан (forfeit) баъди 2 дақиқа қатъ будан.

## 6. Ҳалли мушкилот
| Мушкил | Ҳал |
|---|---|
| Actions сурх: Kotlin хато | Матни хатогиро ба ман фиристед |
| `.github` бор нашуд | Дар Upload ҳамаи папкаҳоро кашед, ё Git истифода баред |
| APK насб намешавад | Иҷозати «насб аз манбаи номаълум» диҳед |
| Сервер оғоз намешавад | `cat backend/server.log`; `.env` ва `JWT_SECRET`-ро санҷед |
| Барнома пайваст намешавад | Суроға (IP:порт), як Wi-Fi будан, `termux-start.sh`, файрвол |
| Termux хоб меравад | `termux-wake-lock`, батареяро дар танзимот «Бе маҳдудият» кунед |
| `npm install` хато | `pkg update`, баъд `npm install` такрор; интернетро санҷед |

## 7. Тест
- Сервер: `cd backend && npm install && npm test`.
- Android: `./gradlew testDebugUnitTest` (дар Actions худкор).

## 8. Release (ихтиёрӣ)
Дар GitHub → Settings → Secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`; баъд Actions → Run workflow → job «release». Debug APK ба ин ниёз надорад.
