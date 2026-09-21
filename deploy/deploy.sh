#!/bin/bash

set -e

PROJECT_PATH="/home/ubuntu/rp-gym"
COMPOSE_FILE="${PROJECT_PATH}/docker-compose-prod.yml"
NGINX_CONF="${PROJECT_PATH}/nginx/service-url.inc"
DRAIN_SECONDS=10

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

rollback() {
    echo "Restoring previous environment: gateway-${BEFORE}"

    cat > "${NGINX_CONF}" <<EOF
upstream gateway {
    server gateway-${BEFORE}:19001;
}
EOF

    if docker exec rp-gym-nginx nginx -t; then
        docker exec rp-gym-nginx nginx -s reload
        echo "Nginx rollback success."
    else
        echo "Nginx rollback configuration test failed."
        return 1
    fi

    echo "Stopping target environment: ${TARGET}"

    docker compose -f "${COMPOSE_FILE}" stop "${TARGET_SERVICES[@]}" || true
}

echo "Start Shared Infrastructure"

docker compose -f "${COMPOSE_FILE}" up -d \
    postgres-user \
    postgres-health \
    postgres-game \
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

if ! docker ps --format '{{.Names}}' | grep -q "^rp-gym-nginx$"; then
    echo "Nginx is not running. Starting Nginx..."
    docker compose -f "${COMPOSE_FILE}" up -d nginx
fi

echo "Switch Nginx to gateway-${TARGET}"

cat > "${NGINX_CONF}" <<EOF
upstream gateway {
    server gateway-${TARGET}:19001;
}
EOF

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
    echo "Restoring previous environment: gateway-${BEFORE}"

    cat > "${NGINX_CONF}" <<EOF
upstream gateway {
    server gateway-${BEFORE}:19001;
}
EOF

    if docker exec rp-gym-nginx nginx -t; then
        docker exec rp-gym-nginx nginx -s reload
        echo "Nginx rollback success."
    else
        echo "Nginx rollback configuration test failed."
    fi

    exit 1
fi

echo "Nginx switched to gateway-${TARGET}"

echo "Verify target environment"

HTTP_CODE=$(curl \
    --max-time 5 \
    -s \
    -o /dev/null \
    -w "%{http_code}" \
    http://localhost/)

echo "Gateway HTTP status: ${HTTP_CODE}"

if [[ ! "${HTTP_CODE}" =~ ^[1-4][0-9][0-9]$ ]]; then
    echo "Target environment verification failed."

    if ! rollback; then
        echo "CRITICAL: Automatic rollback failed."
    fi

    exit 1
fi

echo "Target environment verification successful."

echo "Connection Draining: ${DRAIN_SECONDS}s"
sleep "${DRAIN_SECONDS}"

echo "Stop ${BEFORE} Environment"

docker compose -f "${COMPOSE_FILE}" stop "${BEFORE_SERVICES[@]}"

echo "Deploy Success"
echo "Active Environment : ${TARGET}"