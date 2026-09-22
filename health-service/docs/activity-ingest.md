# 건강 활동 수집 채널 설계

## 구조
수집 채널이 달라도 저장·멱등 처리·Outbox 적재·일일 목표 갱신은 하나의 유스케이스가 담당한다.

```
[Synthetic 수집기] SyntheticActivityScheduler
        │ 내부 호출
        ▼
HealthActivitySyncUseCase (저장 / 멱등 / Outbox / 진행도)
        ▲
        │ 검증·변환
POST /api/v1/health-activities/sync (HealthActivityController)
        ▲
Gateway (JWT 검증, X-User-Id 재주입)
        ▲
Android 앱 (사용자가 권한을 허용하면 Health Connect에서 읽어 전송)
        ▲
[Health Connect] 기기 내 건강 데이터 보관함 (삼성 헬스 등이 기록)
```

- 인바운드 포트: `HealthActivitySyncUseCase`
- 어댑터: `SyntheticActivityScheduler`(스케줄러), `HealthActivityController`(웹)
- 새 수집 채널이 생겨도 어댑터만 추가하고 유스케이스는 바뀌지 않는다.

## 데이터 출처 (`ActivitySource`)
| 값 | 경로 | 외부 요청 허용 |
|---|---|---|
| `SYNTHETIC` | 서버 내부 수집기 | ❌ (400) |
| `HEALTH_CONNECT` | 앱이 기기의 Health Connect에서 읽어 서버로 전송 | ✅ 기본 외부 채널 |
| `SAMSUNG_HEALTH` | Samsung Health Data SDK 직접 연동 (예약, 미연동) | ❌ (400) |

### Health Connect를 기본 채널로 정한 이유
- 제조사 중립: 삼성 헬스·Fitbit 등 여러 앱의 데이터를 한 곳에서 읽는다. 수집 채널을 추상화한 설계 의도와 맞는다.
- 별도 파트너 승인 없이 연동할 수 있다.
- 걸음 수·활동 시간·칼로리는 Health Connect 표준 데이터 타입으로 충분하다.
- 기존 Samsung Health SDK for Android는 2025-07 지원 중단(2028 종료)되었고, 삼성은 Samsung Health Data SDK로 이전을 안내하고 있다.

## 앱 → 서버 인증
1. 앱은 로그인으로 받은 JWT를 `Authorization: Bearer` 헤더로 보낸다.
2. 게이트웨이는 클라이언트가 보낸 `X-User-Id`/`X-User-Role`을 제거하고, 검증된 JWT의 claim으로 다시 넣는다 (`AuthenticatedUserHeaderFilter`).
3. health-service는 `X-User-Id`만 신뢰한다. 서비스 포트는 외부에 노출하지 않는다는 전제다.

health-service에 앱 전용 인증 코드를 추가할 필요가 없다.

## 요청 규약 (`POST /api/v1/health-activities/sync`)
| 항목 | 규칙 |
|---|---|
| 지표 값 | 증분이 아니라 `measuredAt` 시점까지의 **당일 누적값** |
| 일자 경계 | `measuredAt`을 Asia/Seoul 기준으로 변환한 날짜 |
| `measuredAt` 정밀도 | 초 단위로 절삭해서 저장 (같은 측정이 `01:30:00.789Z`, `01:30:00Z`처럼 다른 정밀도로 와도 같은 행으로 처리) |
| 미래 시점 | 서버 시각 + 5분을 넘으면 400 |
| 재전송 | 같은 `(userId, measuredAt)`이면 값이 같으면 무시, 다르면 갱신 (200) |
| 재시도 | 실패한 요청을 **본문 그대로**(`measuredAt`과 값 모두) 다시 보낸다. 현재 시각으로 `measuredAt`을 새로 만들지 않는다 |
| 재집계 | 값을 다시 집계했다면 **새 `measuredAt`**으로 보낸다. 같은 `measuredAt`에 값만 바꿔 보내지 않는다 |
| 신규 | 201 |

### 재시도와 재집계를 구분하는 이유
같은 `measuredAt`에 다른 값이 오면 서버는 스냅샷을 갱신(UPDATE)하지만 `HealthActivitySynced` 이벤트는 다시 발행하지 않는다.
Outbox `dedup_key`가 `(userId, measuredAt)` 단위이고, Game Service도 `measuredAt <= 마지막 반영 시각`인 이벤트를 무시하기 때문이다.
그래서 값이 바뀐 요청을 같은 `measuredAt`으로 보내면 Game Service는 다음 동기화가 올 때까지 옛 값을 유지하고, 자정 직전이면 그 값이 영구히 남는다.

| 클라이언트 동작 | 서버 처리 | 이벤트 |
|---|---|---|
| 재시도 (본문 그대로) | 동일 스냅샷, 변경 없음 (200) | 없음 (이미 발행됨) |
| 재집계 (새 `measuredAt`) | 새 스냅샷 저장 (201) | 발행 |
| 같은 `measuredAt`에 값만 변경 (규약 위반) | 스냅샷 갱신 (200) | 없음 → Game 반영 지연 |

- 현재는 이 규약을 클라이언트 계약으로만 두고 서버에서 강제하지 않는다. 데이터 출처가 Synthetic뿐이라 규약 위반이 일어나지 않는다.
- 앱을 구현할 때 "같은 `measuredAt`에 다른 값이면 409"로 서버에서 강제하는 방안을 함께 검토한다. 테이블 명세의 "존재하면 UPDATE" 규칙과 클라이언트 재전송 처리가 같이 바뀌어야 한다.
- 값이 줄어드는 정정은 이 규약으로도 이미 완료된 퀘스트와 지급된 XP를 되돌리지 못하므로 별도 논의가 필요하다.

## 범위 밖 (의도적으로 제외)
| 항목 | 제외 이유 |
|---|---|
| Android 앱 (Kotlin, Health Connect 권한·집계 쿼리) | 백엔드 담당 범위 밖, 예상 공수 2~3일 이상 |
| WorkManager 주기 동기화, 배터리 최적화 예외 | 공수 5일 이상. 주기 수집은 Synthetic 수집기로 대체 시연 |
| 백그라운드 읽기 권한, 과거 데이터 백필 | 앱 구현 시 함께 결정 |
| 오프라인 큐·재시도 | 앱 구현 시 함께 결정. 위 재시도·재집계 규약을 지키면 서버는 멱등 처리로 재전송을 받아낼 수 있다 |

## 시연
- Synthetic 수집기: 주기적으로 누적값이 쌓이고 목표 달성 시 퀘스트가 생성되는 흐름
- Postman: 로그인 토큰으로 `HEALTH_CONNECT` 요청을 게이트웨이를 거쳐 보내 같은 흐름으로 이어지는 것(04, 06), `SYNTHETIC` 요청이 400으로 거부되는 것(04-1)
- Swagger UI: 같은 요청을 브라우저에서 직접 보내 확인한다. 게이트웨이를 거치므로 로그인으로 받은 JWT를 Authorize에 입력한 뒤 호출한다
- Postman·Swagger 시연용 사용자는 `rpgym.synthetic.user-ids`에서 제외한다 (누적값이 섞이지 않도록)