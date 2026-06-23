#!/usr/bin/env bash
set -euo pipefail

# ── Настройки ─────────────────────────────────────────────────────────────────
REMOTE_HOST="root@186.246.30.21"
REMOTE_DIR="/opt/emias-dashboard"
SERVICE_NAME="emias-dashboard"
JAR_NAME="dashboard-1.0.0.jar"
HEALTH_URL="http://186.246.30.21/spec/"
NGINX_CONF="/etc/nginx/sites-available/emias-dashboard"
NGINX_ENABLED="/etc/nginx/sites-enabled/emias-dashboard"
HEALTH_TIMEOUT=120
HEALTH_INTERVAL=3
SSH_TIMEOUT=10

# ── Цвета ─────────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
BOLD='\033[1m'
DIM='\033[2m'
NC='\033[0m'

# ── Хелперы ───────────────────────────────────────────────────────────────────
step() {
  local n=$1; local title=$2
  echo -e "\n${BLUE}${BOLD}┌─────────────────────────────────────────────────┐${NC}"
  echo -e "${BLUE}${BOLD}│  ${CYAN}[${n}/4]${NC}${BOLD} ${title}$(printf '%*s' $((43 - ${#title} - 6)) '')${BLUE}│${NC}"
  echo -e "${BLUE}${BOLD}└─────────────────────────────────────────────────┘${NC}"
}
ok()   { echo -e "  ${GREEN}${BOLD}✔${NC}  $1"; }
warn() { echo -e "  ${YELLOW}${BOLD}⚠${NC}  $1"; }
info() { echo -e "  ${DIM}$1${NC}"; }

fail() {
  local msg=$1
  local hint=${2:-""}
  # Очищаем строку спиннера если он работал
  echo -e "\r\033[K"
  echo -e "${RED}${BOLD}"
  echo "  ╔═══════════════════════════════════════════════════╗"
  echo "  ║                                                   ║"
  echo "  ║   ✗  ОШИБКА                                      ║"
  echo "  ║                                                   ║"
  # Разбиваем длинное сообщение на строки по 47 символов
  while IFS= read -r line; do
    printf "  ║   %-47s║\n" "$line"
  done < <(echo "$msg" | fold -s -w 47)
  if [[ -n "$hint" ]]; then
    echo "  ║                                                   ║"
    echo "  ║   Подсказка:                                      ║"
    while IFS= read -r line; do
      printf "  ║   %-47s║\n" "$line"
    done < <(echo "$hint" | fold -s -w 47)
  fi
  echo "  ║                                                   ║"
  echo "  ╚═══════════════════════════════════════════════════╝"
  echo -e "${NC}"
  exit 1
}

ssh_cmd() {
  sshpass -p "$SSH_PASSWORD" \
    ssh -o ConnectTimeout=$SSH_TIMEOUT \
        -o StrictHostKeyChecking=accept-new \
        -o PasswordAuthentication=yes \
        -o PubkeyAuthentication=no \
        "$REMOTE_HOST" "$@"
}

scp_cmd() {
  sshpass -p "$SSH_PASSWORD" \
    scp -o ConnectTimeout=$SSH_TIMEOUT \
        -o StrictHostKeyChecking=accept-new \
        -o PasswordAuthentication=yes \
        -o PubkeyAuthentication=no \
        "$@"
}

# ── Заголовок ─────────────────────────────────────────────────────────────────
DEPLOY_TIME=$(date '+%d.%m.%Y %H:%M:%S')
echo -e "\n${MAGENTA}${BOLD}"
echo -e "${NC}"
echo -e "  ${BOLD}Dashboard  →  Новосибирск${NC}  ${DIM}${DEPLOY_TIME}${NC}"
echo -e "  ${DIM}${REMOTE_HOST}:${REMOTE_DIR}${NC}"
echo -e "  ${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}\n"

# ── Проверка окружения ────────────────────────────────────────────────────────
info "Проверка окружения..."

command -v mvn &>/dev/null \
  || fail "Maven не найден в PATH" "Установите Maven: brew install maven"

command -v ssh &>/dev/null \
  || fail "ssh не найден" "Установите OpenSSH"

command -v scp &>/dev/null \
  || fail "scp не найден" "Установите OpenSSH"

command -v sshpass &>/dev/null \
  || fail "sshpass не найден" "Установите: brew install hudochenkov/sshpass/sshpass"

command -v curl &>/dev/null \
  || fail "curl не найден" "Установите curl: brew install curl"

[[ -f "pom.xml" ]] \
  || fail "pom.xml не найден" "Запускайте скрипт из корня проекта"

ok "Окружение в порядке"

# ── Запрос пароля ─────────────────────────────────────────────────────────────
echo -e "\n  ${CYAN}${BOLD}Пароль для ${REMOTE_HOST}:${NC}"
read -rs SSH_PASSWORD
echo ""

# ── Проверка доступности сервера и пароля ─────────────────────────────────────
info "Проверка SSH-соединения с ${REMOTE_HOST}..."

SSH_EXIT=0
ssh_cmd "echo ok" &>/dev/null || SSH_EXIT=$?

if [[ $SSH_EXIT -eq 5 ]]; then
  fail "Неверный пароль для ${REMOTE_HOST}" \
       "Проверьте пароль и попробуйте снова"
elif [[ $SSH_EXIT -ne 0 ]]; then
  fail "Не удалось подключиться к серверу ${REMOTE_HOST}" \
       "Проверьте: сервер включён, порт 22 открыт"
fi
ok "Сервер доступен, пароль верный"

# ── Nginx — применяем конфиг если изменился ───────────────────────────────────
info "Проверка Nginx-конфига..."

LOCAL_CONF="nginx-novosibirsk.conf"
[[ -f "$LOCAL_CONF" ]] || fail "Файл $LOCAL_CONF не найден" "Запускайте из корня проекта"

REMOTE_CONF_CONTENT=$(ssh_cmd "cat $NGINX_CONF 2>/dev/null || echo ''")
LOCAL_CONF_CONTENT=$(cat "$LOCAL_CONF")

if [[ "$REMOTE_CONF_CONTENT" != "$LOCAL_CONF_CONTENT" ]]; then
  echo -e "  ${YELLOW}${BOLD}⚠${NC}  Обновляю Nginx-конфиг..."
  ssh_cmd bash << EOF
set -e
command -v nginx &>/dev/null || apt-get install -y nginx -q
mkdir -p /etc/nginx/sites-available /etc/nginx/sites-enabled
cat > ${NGINX_CONF} << 'NGINX'
$(cat "$LOCAL_CONF")
NGINX
ln -sf ${NGINX_CONF} ${NGINX_ENABLED}
# Отключаем дефолтный сайт если он мешает
rm -f /etc/nginx/sites-enabled/default
nginx -t && systemctl reload nginx
EOF
  ok "Nginx-конфиг обновлён и применён"
else
  ok "Nginx-конфиг актуален"
fi

# ── 1. Сборка ─────────────────────────────────────────────────────────────────
step 1 "Сборка (mvn clean package)"
BUILD_START=$SECONDS

MVN_OUTPUT=$(mktemp)
if ! mvn -DskipTests clean package > "$MVN_OUTPUT" 2>&1; then
  # Показываем последние строки вывода Maven чтобы было понятно что сломалось
  ERRORS=$(grep -E "ERROR|FAILURE|\[ERROR\]" "$MVN_OUTPUT" | head -5 | sed 's/\[ERROR\] //' || true)
  rm -f "$MVN_OUTPUT"
  fail "Сборка завершилась с ошибкой:
${ERRORS:-см. вывод mvn выше}" \
       "Запустите вручную: mvn clean package"
fi
rm -f "$MVN_OUTPUT"

JAR_PATH="target/${JAR_NAME}"
if [[ ! -f "$JAR_PATH" ]]; then
  fail "JAR не найден после сборки: ${JAR_PATH}" \
       "Проверьте <finalName> в pom.xml"
fi

JAR_SIZE=$(du -sh "$JAR_PATH" | cut -f1)
BUILD_TIME=$((SECONDS - BUILD_START))
ok "JAR собран за ${BUILD_TIME}с  •  ${JAR_SIZE}"

# ── 2. Копирование на сервер ───────────────────────────────────────────────────
step 2 "Загрузка JAR на сервер"
UPLOAD_START=$SECONDS

if ! scp_cmd "$JAR_PATH" "${REMOTE_HOST}:${REMOTE_DIR}/${JAR_NAME}.new" 2>/tmp/scp_err; then
  SCP_ERR=$(cat /tmp/scp_err)
  fail "Не удалось загрузить JAR на сервер" "${SCP_ERR:-нет деталей}"
fi

UPLOAD_TIME=$((SECONDS - UPLOAD_START))
ok "Загружен за ${UPLOAD_TIME}с"

# ── 3. Замена JAR и перезапуск ────────────────────────────────────────────────
step 3 "Перезапуск сервиса"

if ! ssh_cmd bash <<EOF 2>/tmp/ssh_err
  set -e
  mv ${REMOTE_DIR}/${JAR_NAME}.new ${REMOTE_DIR}/${JAR_NAME}
  systemctl restart ${SERVICE_NAME}
EOF
then
  SSH_ERR=$(cat /tmp/ssh_err)
  fail "Ошибка при перезапуске сервиса" "${SSH_ERR:-нет деталей}"
fi

ok "Команда перезапуска выполнена"

# ── 4. Ожидание старта — стримим логи ────────────────────────────────────────
step 4 "Ожидание запуска (таймаут ${HEALTH_TIMEOUT}с)"

LOG_FILE=$(mktemp)
started=false

# Стримим journalctl в фоне в файл
ssh_cmd journalctl -u "${SERVICE_NAME}" -f -n 30 --no-pager 2>/dev/null >> "$LOG_FILE" &
SSH_LOG_PID=$!

echo -e "  ${DIM}Логи сервиса:${NC}"
echo ""

last_line=0
elapsed=0

while [[ $elapsed -lt $HEALTH_TIMEOUT ]]; do
  sleep 1
  elapsed=$((elapsed + 1))

  # Выводим новые строки лога
  current_lines=$(wc -l < "$LOG_FILE" 2>/dev/null | tr -d ' ')
  if [[ "$current_lines" -gt "$last_line" ]]; then
    while IFS= read -r line; do
      echo -e "  ${DIM}${line}${NC}"
    done < <(tail -n +"$((last_line + 1))" "$LOG_FILE")
    last_line=$current_lines
  fi

  # Успех — нашли сигнальную строку
  if grep -q "Дашборд запущен:" "$LOG_FILE" 2>/dev/null; then
    started=true
    break
  fi

  # Сервис упал
  if grep -q "Failed to start\|Application run failed\|BUILD FAILURE" "$LOG_FILE" 2>/dev/null; then
    break
  fi

  svc_status=$(ssh_cmd systemctl is-active "${SERVICE_NAME}" 2>/dev/null || echo "unknown")
  if [[ "$svc_status" == "failed" ]]; then
    break
  fi
done

kill $SSH_LOG_PID 2>/dev/null
wait $SSH_LOG_PID 2>/dev/null
rm -f "$LOG_FILE"

echo ""

# ── Итог ──────────────────────────────────────────────────────────────────────
TOTAL_TIME=$SECONDS

if [[ "$started" == true ]]; then
  echo -e "\n${GREEN}${BOLD}"
  echo "  ╔═══════════════════════════════════════════════════╗"
  echo "  ║                                                   ║"
  echo "  ║   ✅  ДЕПЛОЙ ЗАВЕРШЁН УСПЕШНО                    ║"
  echo "  ║                                                   ║"
  printf "  ║   🌐  %-43s║\n" "http://186.246.30.21/spec/"
  printf "  ║   ⏱   Общее время: %-30s║\n" "${TOTAL_TIME}с"
  echo "  ║                                                   ║"
  echo "  ╚═══════════════════════════════════════════════════╝"
  echo -e "${NC}"
else
  fail "Таймаут ${HEALTH_TIMEOUT}с или ошибка запуска" \
       "Полные логи: ssh ${REMOTE_HOST} journalctl -u ${SERVICE_NAME} -n 100"
fi
