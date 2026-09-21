#!/bin/bash

set -e

PROJECT_PATH="/home/ubuntu/rp-gym"
COMPOSE_FILE="${PROJECT_PATH}/docker-compose-prod.yml"
NGINX_CONF="${PROJECT_PATH}/nginx/service-url.inc"
DRAIN_SECONDS=10
NGINX_READY_RETRIES=15
NGINX_READY_INTERVAL=2

cd "${PROJECT_PATH}"

ENV_FILE="${PROJECT_PATH}/.env"

if [ ! -f "${ENV_FILE}" ]; then
    echo ".env file not found: ${ENV_FILE}"
    exit 1
fi

for VAR in ZIPKIN_UI_USER ZIPKIN_UI_PASSWORD_HASH GRAFANA_ROOT_URL
do
    if ! grep -qE "^${VAR}=.+" "${ENV_FILE}"; then
        echo "Required environment variable is missing or empty: ${VAR}"
        exit 1
    fi
done

echo "Blue-Green Deploy Start"

if [ ! -f "${NGINX_CONF}" ]; then
    echo "Nginx config not found: ${NGINX_CONF}"
    exit 1
fi

CURRENT=$(grep -E "server gateway-(blue|green):19001;" "${NGINX_CONF}" | awk '{print $2}' | cut -d':' -f1)

if [ "${CURRENT}" = "gateway-blue" ]; then
    TARGET="green"
    BEFORE="blue"
elif [ "${CURRENT}" = "gateway-green" ]; then
    TARGET="blue"
    BEFORE="green"
else
    echo "Invalid current gateway configuration."
    echo "Current: ${CURRENT}"
    exit 1
fi

echo "Current Environment : ${BEFORE}"
echo "Deploy Target       : ${TARGET}"

TARGET_SERVICES=(
    "eureka-server-${TARGET}"
    "gateway-${TARGET}"
    "user-service-${TARGET}"
    "health-service-${TARGET}"
    "game-service-${TARGET}"
    "notification-service-${TARGET}"
)

BEFORE_SERVICES=(
    "eureka-server-${BEFORE}"
    "gateway-${BEFORE}"
    "user-service-${BEFORE}"
    "health-service-${BEFORE}"
    "game-service-${BEFORE}"
    "notification-service-${BEFORE}"
)

if docker inspect -f '{{.State.Running}}' "rp-gym-gateway-${BEFORE}" 2>/dev/null | grep -q '^true$'; then
    HAS_BEFORE_ENV=true
else
    HAS_BEFORE_ENV=false
fi

if docker inspect -f '{{.State.Running}}' rp-gym-nginx 2>/dev/null | grep -q '^true$'; then
    NGINX_WAS_RUNNING=true
else
    NGINX_WAS_RUNNING=false
fi

if [ "${HAS_BEFORE_ENV}" = true ]; then
    echo "Previous environment detected: gateway-${BEFORE}"
else
    echo "No previous environment detected. Treating as initial deployment."
fi

rollback() {
    echo "Rollback Start"

    if [ "${HAS_BEFORE_ENV}" = true ]; then
        echo "Restoring previous environment: gateway-${BEFORE}"

        cat > "${NGINX_CONF}" <<EOF
upstream gateway {
    server gateway-${BEFORE}:19001;
}
EOF

        if ! docker exec rp-gym-nginx nginx -t; then
            echo "Nginx rollback configuration test failed."
            return 1
        fi

        if ! docker exec rp-gym-nginx nginx -s reload; then
            echo "Nginx rollback reload failed."
            return 1
        fi

        echo "Nginx rollback success."
    else
        echo "No previous environment exists. Skipping Nginx traffic rollback."

        if [ "${NGINX_WAS_RUNNING}" = false ]; then
            echo "Stopping Nginx started by this deployment."
            docker compose -f "${COMPOSE_FILE}" stop nginx || true
        fi
    fi

    echo "Stopping target environment: ${TARGET}"

    docker compose -f "${COMPOSE_FILE}" stop "${TARGET_SERVICES[@]}" || true

    echo "Rollback Success"
    return 0
}

wait_for_target_gateway() {
    local CONTAINER="rp-gym-gateway-${TARGET}"
    local TARGET_IP
    local HTTP_CODE

    TARGET_IP=$(docker inspect \
        -f '{{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}}' \
        "${CONTAINER}" 2>/dev/null | awk '{print $1}')

    if [ -z "${TARGET_IP}" ]; then
        echo "Target Gateway IP not found: ${CONTAINER}"
        return 1
    fi

    echo "Target Gateway IP: ${TARGET_IP}"
    echo "Waiting for gateway-${TARGET} to become reachable..."

    for ((i=1; i<=5; i++)); do
        HTTP_CODE=$(curl \
            --max-time 2 \
            -s \
            -o /dev/null \
            -w "%{http_code}" \
            "http://${TARGET_IP}:19001/" || true)

        if [[ "${HTTP_CODE}" =~ ^[1-4][0-9][0-9]$ ]]; then
            echo "Target Gateway is reachable. HTTP status: ${HTTP_CODE}"
            return 0
        fi

        echo "Target Gateway is not reachable yet. HTTP status: ${HTTP_CODE:-000} (attempt ${i}/5)"
        sleep 2
    done

    echo "Target Gateway failed to become reachable."
    return 1
}

echo "Start Shared Infrastructure"

docker compose -f "${COMPOSE_FILE}" up -d \
    postgres-user \
    postgres-health \
    postgres-game \
    postgres-notification \
    kafka \
    redis \
    prometheus \
    grafana \
    loki \
    alloy \
    zipkin

echo "Build & Start ${TARGET}"

if ! docker compose -f "${COMPOSE_FILE}" up -d --build --wait --wait-timeout 300 "${TARGET_SERVICES[@]}"; then
    echo "Target environment failed to become healthy."
    docker compose -f "${COMPOSE_FILE}" ps
    docker compose -f "${COMPOSE_FILE}" logs --tail=100 "${TARGET_SERVICES[@]}"
    exit 1
fi

echo "Target environment is healthy."

echo "Switch Nginx to gateway-${TARGET}"

cat > "${NGINX_CONF}" <<EOF
upstream gateway {
    server gateway-${TARGET}:19001;
}
EOF

if [ "${NGINX_WAS_RUNNING}" = false ]; then
    echo "Nginx is not running. Starting Nginx..."

    if ! docker compose -f "${COMPOSE_FILE}" up -d nginx; then
        echo "Nginx start failed."

        if ! rollback; then
            echo "CRITICAL: Automatic rollback failed."
        fi

        exit 1
    fi

    echo "Testing Nginx configuration"

    if ! docker exec rp-gym-nginx nginx -t; then
        echo "Nginx configuration test failed."

        if ! rollback; then
            echo "CRITICAL: Automatic rollback failed."
        fi

        exit 1
    fi

    echo "Nginx started with gateway-${TARGET}"

else
    echo "Testing Nginx configuration"

    if ! docker exec rp-gym-nginx nginx -t; then
        echo "Nginx configuration test failed."

        cat > "${NGINX_CONF}" <<EOF
upstream gateway {
    server gateway-${BEFORE}:19001;
}
EOF

        exit 1
    fi

    echo "Reload Nginx"

    if ! docker exec rp-gym-nginx nginx -s reload; then
        echo "Nginx reload failed."

        if ! rollback; then
            echo "CRITICAL: Automatic rollback failed."
        fi

        exit 1
    fi

    echo "Nginx switched to gateway-${TARGET}"
fi

echo "Verify target environment"

if ! wait_for_target_gateway; then
    echo "Target environment verification failed."

    if ! rollback; then
        echo "CRITICAL: Automatic rollback failed."
    fi

    exit 1
fi

HTTP_CODE=$(curl \
    --max-time 5 \
    -s \
    -o /dev/null \
    -w "%{http_code}" \
    http://localhost/ || true)

echo "Nginx HTTP status: ${HTTP_CODE}"

if [[ ! "${HTTP_CODE}" =~ ^[1-4][0-9][0-9]$ ]]; then
    echo "Nginx traffic verification failed."

    if ! rollback; then
        echo "CRITICAL: Automatic rollback failed."
    fi

    exit 1
fi

echo "Target environment verification successful."

if [ "${HAS_BEFORE_ENV}" = true ]; then
    echo "Connection Draining: ${DRAIN_SECONDS}s"
    sleep "${DRAIN_SECONDS}"

    echo "Stop ${BEFORE} Environment"

    docker compose -f "${COMPOSE_FILE}" stop "${BEFORE_SERVICES[@]}"
else
    echo "No previous environment to stop."
fi

echo "Deploy Success"
echo "Active Environment : ${TARGET}"