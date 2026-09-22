#!/bin/sh
# ZIPKIN_UI_USER / ZIPKIN_UI_PASSWORD_HASH (.env, git 미포함)로 /zipkin Basic Auth용
# htpasswd 파일을 컨테이너 기동 시 생성한다. 비밀번호는 평문이 아니라
# openssl passwd -apr1 로 미리 해시한 값을 ZIPKIN_UI_PASSWORD_HASH에 넣어 사용한다.
set -e

if [ -n "$ZIPKIN_UI_USER" ] && [ -n "$ZIPKIN_UI_PASSWORD_HASH" ]; then
  echo "${ZIPKIN_UI_USER}:${ZIPKIN_UI_PASSWORD_HASH}" > /etc/nginx/.htpasswd
  echo "[zipkin-htpasswd] /etc/nginx/.htpasswd generated for user '${ZIPKIN_UI_USER}'"
else
  echo "[zipkin-htpasswd] WARN: ZIPKIN_UI_USER / ZIPKIN_UI_PASSWORD_HASH not set - /zipkin basic auth will reject all requests" >&2
fi
