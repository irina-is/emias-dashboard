#!/usr/bin/env bash
set -euo pipefail

# ── Настройки ─────────────────────────────────────────────────────────────────
REMOTE_HOST="root@186.246.30.21"
REMOTE_DIR="/opt/deploy-portal"
SERVICE_NAME="deploy-portal"
JAR_NAME="deploy-portal.jar"
PORTAL_PORT=8082
HEALTH_TIMEOUT=90
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

step() {
  local n=$1; local title=$2
  echo -e "\n${BLUE}${BOLD}┌─────────────────────────────────────────────────┐${NC}"
  echo -e "${BLUE}${BOLD}│  ${CYAN}[${n}/4]${NC}${BOLD} ${title}$(printf '%*s' $((43 - ${#title} - 6)) '')${BLUE}│${NC}"
  echo -e "${BLUE}${BOLD}└─────────────────────────────────────────────────┘${NC}"
}
ok()   { echo -e "  ${GREEN}${BOLD}✔${NC}  $1"; }
info() { echo -e "  ${DIM}$1${NC}"; }

fail() {
  local msg=$1; local hint=${2:-""}
  echo -e "\r\033[K"
  echo -e "${RED}${BOLD}"
  echo "  ╔═══════════════════════════════════════════════════╗"
  echo "  ║                                                   ║"
  echo "  ║   ✗  ОШИБКА                                      ║"
  echo "  ║                                                   ║"
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
echo -e "\n${MAGENTA}${BOLD}${NC}"
echo -e "  ${BOLD}Деплой-портал  →  Новосибирск${NC}  ${DIM}${DEPLOY_TIME}${NC}"
echo -e "  ${DIM}${REMOTE_HOST}:${REMOTE_DIR}${NC}"
echo -e "  ${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}\n"

# ── Проверка окружения ────────────────────────────────────────────────────────
info "Проверка окружения..."

command -v mvn     &>/dev/null || fail "Maven не найден"    "brew install maven"
command -v sshpass &>/dev/null || fail "sshpass не найден"  "brew install hudochenkov/sshpass/sshpass"
command -v scp     &>/dev/null || fail "scp не найден"      "Установите OpenSSH"

[[ -f "deploy-portal/pom.xml" ]] \
  || fail "deploy-portal/pom.xml не найден" "Запускайте из корня проекта emias-dashboard"

ok "Окружение в порядке"

# ── Запрос пароля SSH ─────────────────────────────────────────────────────────
echo -e "\n  ${CYAN}${BOLD}Пароль SSH для ${REMOTE_HOST}:${NC}"
read -rs SSH_PASSWORD
echo ""

SSH_EXIT=0
ssh_cmd "echo ok" &>/dev/null || SSH_EXIT=$?
if [[ $SSH_EXIT -eq 5 ]]; then
  fail "Неверный пароль SSH" "Проверьте пароль и попробуйте снова"
elif [[ $SSH_EXIT -ne 0 ]]; then
  fail "Не удалось подключиться к ${REMOTE_HOST}" "Проверьте: сервер включён, порт 22 открыт"
fi
ok "Сервер доступен, пароль верный"

# ── 1. Сборка ─────────────────────────────────────────────────────────────────
step 1 "Сборка deploy-portal"
BUILD_START=$SECONDS

MVN_OUTPUT=$(mktemp)
if ! (cd deploy-portal && mvn -DskipTests clean package) > "$MVN_OUTPUT" 2>&1; then
  ERRORS=$(grep -E "\[ERROR\]" "$MVN_OUTPUT" | head -5 | sed 's/\[ERROR\] //' || true)
  rm -f "$MVN_OUTPUT"
  fail "Сборка завершилась с ошибкой:
${ERRORS:-см. вывод mvn}" "Запустите вручную: cd deploy-portal && mvn clean package"
fi
rm -f "$MVN_OUTPUT"

JAR_PATH="deploy-portal/target/${JAR_NAME}"
[[ -f "$JAR_PATH" ]] || fail "JAR не найден: ${JAR_PATH}" "Проверьте <finalName> в deploy-portal/pom.xml"

JAR_SIZE=$(du -sh "$JAR_PATH" | cut -f1)
BUILD_TIME=$((SECONDS - BUILD_START))
ok "JAR собран за ${BUILD_TIME}с  •  ${JAR_SIZE}"

# ── 2. Подготовка сервера (первый запуск) ─────────────────────────────────────
step 2 "Настройка сервера"

ssh_cmd bash << EOF
set -e

# Директория
mkdir -p ${REMOTE_DIR}

# Конфиг — создаём только если не существует (не перезаписываем пароль)
if [ ! -f ${REMOTE_DIR}/application.properties ]; then
  cat > ${REMOTE_DIR}/application.properties << 'PROPS'
server.port=${PORTAL_PORT}

# Bcrypt-хэш пароля. Сгенерировать: htpasswd -bnBC 10 "" ПАРОЛЬ | tr -d ':\n'
deploy.password=\$2y\$10\$pXV2.lBvHIxg7I1yaWwcYeViZuMr/eJqp8wVybSZlTqxdqR109jXW

deploy.jar-path=/opt/emias-dashboard/dashboard-1.0.0.jar
deploy.service-name=emias-dashboard
deploy.history-file=${REMOTE_DIR}/history.json

spring.servlet.multipart.max-file-size=200MB
spring.servlet.multipart.max-request-size=200MB
spring.jackson.serialization.write-dates-as-timestamps=false
PROPS
  echo "  Создан конфиг ${REMOTE_DIR}/application.properties"
else
  echo "  Конфиг уже существует — не перезаписываем"
fi

# Systemd-сервис — создаём только если не существует
if [ ! -f /etc/systemd/system/${SERVICE_NAME}.service ]; then
  cat > /etc/systemd/system/${SERVICE_NAME}.service << 'SVC'
[Unit]
Description=EMIAS Deploy Portal
After=network.target

[Service]
User=root
WorkingDirectory=${REMOTE_DIR}
ExecStart=/usr/bin/java -jar ${REMOTE_DIR}/${JAR_NAME} \
  --spring.config.location=${REMOTE_DIR}/application.properties
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
SVC
  systemctl daemon-reload
  systemctl enable ${SERVICE_NAME}
  echo "  Создан systemd-сервис ${SERVICE_NAME}"
else
  echo "  Сервис уже зарегистрирован"
fi
EOF

ok "Сервер настроен"

# ── 3. Копирование JAR и перезапуск ───────────────────────────────────────────
step 3 "Загрузка JAR и перезапуск"
UPLOAD_START=$SECONDS

if ! scp_cmd "$JAR_PATH" "${REMOTE_HOST}:${REMOTE_DIR}/${JAR_NAME}.new" 2>/tmp/scp_err; then
  fail "Не удалось загрузить JAR" "$(cat /tmp/scp_err)"
fi
ok "Загружен за $((SECONDS - UPLOAD_START))с"

ssh_cmd bash << EOF
set -e
mv ${REMOTE_DIR}/${JAR_NAME}.new ${REMOTE_DIR}/${JAR_NAME}
systemctl restart ${SERVICE_NAME}
EOF
ok "Сервис перезапущен"

# ── 4. Ожидание старта — стримим логи ────────────────────────────────────────
step 4 "Ожидание запуска (таймаут ${HEALTH_TIMEOUT}с)"

LOG_FILE=$(mktemp)
started=false

ssh_cmd journalctl -u "${SERVICE_NAME}" -f -n 20 --no-pager 2>/dev/null >> "$LOG_FILE" &
SSH_LOG_PID=$!

echo -e "  ${DIM}Логи сервиса:${NC}\n"

last_line=0
elapsed=0

while [[ $elapsed -lt $HEALTH_TIMEOUT ]]; do
  sleep 1
  elapsed=$((elapsed + 1))

  current_lines=$(wc -l < "$LOG_FILE" 2>/dev/null | tr -d ' ')
  if [[ "$current_lines" -gt "$last_line" ]]; then
    while IFS= read -r line; do
      echo -e "  ${DIM}${line}${NC}"
    done < <(tail -n +"$((last_line + 1))" "$LOG_FILE")
    last_line=$current_lines
  fi

  if grep -q "Деплой-портал запущен:" "$LOG_FILE" 2>/dev/null; then
    started=true
    break
  fi

  if grep -q "Application run failed\|BUILD FAILURE" "$LOG_FILE" 2>/dev/null; then
    break
  fi

  svc_status=$(ssh_cmd systemctl is-active "${SERVICE_NAME}" 2>/dev/null || echo "unknown")
  [[ "$svc_status" == "failed" ]] && break
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
  printf "  ║   🌐  %-43s║\n" "http://186.246.30.21:${PORTAL_PORT}"
  printf "  ║   ⏱   Общее время: %-30s║\n" "${TOTAL_TIME}с"
  echo "  ║                                                   ║"
  echo "  ║   ⚠️   Смените пароль в:                          ║"
  printf "  ║       %-43s║\n" "${REMOTE_DIR}/application.properties"
  echo "  ║                                                   ║"
  echo "  ╚═══════════════════════════════════════════════════╝"
  echo -e "${NC}"
else
  fail "Таймаут ${HEALTH_TIMEOUT}с или ошибка запуска" \
       "Логи: ssh ${REMOTE_HOST} journalctl -u ${SERVICE_NAME} -n 100"
fi
