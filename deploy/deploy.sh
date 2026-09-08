#!/bin/bash

set -e

PROJECT_PATH="/home/ubuntu/rp-gym"
COMPOSE_FILE="${PROJECT_PATH}/docker-compose-prod.yml"
NGINX_CONF="${PROJECT_PATH}/nginx/service-url.inc"

cd "${PROJECT_PATH}"

echo "Blue-Green Deploy Start"

if [ ! -f "${NGINX_CONF}" ]; then
    echo "Nginx config not found: ${NGINX_CONF}"
    exit 1
fi

CURRENT=$(grep -E "server gateway-(blue|green):19001;" "${NGINX_CONF}" \
    | awk '{print $2}' \
    | cut -d':' -f1)

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
)

BEFORE_SERVICES=(
    "eureka-server-${BEFORE}"
    "gateway-${BEFORE}"
    "user-service-${BEFORE}"
    "health-service-${BEFORE}"
    "game-service-${BEFORE}"
)

echo "Build & Start ${TARGET}"

docker compose \
    -f "${COMPOSE_FILE}" \
    up -d --build "${TARGET_SERVICES[@]}"

echo "Checking ${TARGET} containers..."

for SERVICE in "${TARGET_SERVICES[@]}"
do
    CONTAINER=$(docker compose \
        -f "${COMPOSE_FILE}" \
        ps -q "${SERVICE}")

    if [ -z "${CONTAINER}" ]; then
        echo "Container not found: ${SERVICE}"
        exit 1
    fi

    STATUS=$(docker inspect \
        --format '{{.State.Status}}' \
        "${CONTAINER}")

    if [ "${STATUS}" != "running" ]; then
        echo "Container is not running: ${SERVICE}"
        exit 1
    fi

    echo "${SERVICE}: running"
done

check_health() {
    SERVICE_NAME="$1"
    CONTAINER_NAME="$2"
    PORT="$3"

    echo "${SERVICE_NAME} Health Check"

    SUCCESS=false

    for RETRY in {1..30}
    do
        if docker exec "${CONTAINER_NAME}" \
            curl -fs "http://localhost:${PORT}/actuator/health" > /dev/null
        then
            SUCCESS=true
            echo "${SERVICE_NAME} Health Check Success"
            break
        fi

        echo "Retry... (${RETRY}/30)"
        sleep 5
    done

    if [ "${SUCCESS}" = false ]; then
        echo "${SERVICE_NAME} Health Check Failed"

        docker compose \
            -f "${COMPOSE_FILE}" \
            logs --tail=100 "${SERVICE_NAME}"

        exit 1
    fi
}

echo "Health Check"

check_health \
    "eureka-server-${TARGET}" \
    "rp-gym-eureka-${TARGET}" \
    19000

check_health \
    "gateway-${TARGET}" \
    "rp-gym-gateway-${TARGET}" \
    19001

# TODO
# User Service Health Check
# check_health \
#     "user-service-${TARGET}" \
#     "rp-gym-user-${TARGET}" \
#     19010

# TODO
# Health Service Health Check
# check_health \
#     "health-service-${TARGET}" \
#     "rp-gym-health-${TARGET}" \
#     19011

# TODO
# Game Service Health Check
# check_health \
#     "game-service-${TARGET}" \
#     "rp-gym-game-${TARGET}" \
#     19012

echo "Switch Nginx"

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

echo "Stop ${BEFORE} Environment"

docker compose \
    -f "${COMPOSE_FILE}" \
    stop "${BEFORE_SERVICES[@]}"

echo "Deploy Success"
echo "Active Environment : ${TARGET}"