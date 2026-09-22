package com.workoutdone.rpgym.game.party.outbox.adapter.out.http;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

// ── 알림 담당에게 ─────────────────────────────────────────────────────
// 파티가 알림 서비스를 호출하는 입구다. 이 엔드포인트를 만들어 주셔야 한다.
//
//   POST /api/v1/internal/notifications/events
//   헤더  eventType: PARTY_INVITED | PARTY_INVITATION_CLOSED | PARTY_MEMBER_JOINED
//                  | PARTY_MEMBER_LEFT | PARTY_ENDED
//   본문  { "eventId", "eventType", "occurredAt", "userId", "data": { ... } }
//         userId 가 곧 알림 대상이다. data 의 모양은 eventType 마다 다르다.
//
// 받으면 곧바로 2xx 를 돌려줄 것. 슬랙 발송까지 기다리면 안 된다.
// 릴레이가 순서를 지키려고 실패 시 라운드를 멈추기 때문에, 응답이 느리면 뒤 이벤트가 통째로 밀린다.
// 받아서 자기 쪽에 적재하고 200 을 주면 그다음은 알림 쪽 속도로 처리하면 된다.
//
// 같은 이벤트가 두 번 올 수 있다. 2xx 를 받기 전에 우리가 죽으면 다음 라운드에 다시 보낸다.
// eventId 로 걸러 주면 된다. 같은 이벤트는 몇 번을 보내도 eventId 가 같다.
//
// 2xx 가 아니면 우리 쪽은 발행 실패로 보고 PENDING 으로 남겨 다음 폴링에 재시도한다.
// 즉 알림 서비스가 잠시 죽어도 이벤트는 유실되지 않고 살아난 뒤에 밀려 들어간다.
// 반대로 말하면 4xx 를 주면 그 이벤트는 영원히 재시도되므로, 못 쓰는 본문이면 2xx 로 받고 버릴 것.
//
// 서비스 이름은 유레카에 등록된 notification-service 다. 주소를 하드코딩하지 않는다.
//
// 경로가 /api/v1/internal/ 로 시작하는 것은 이 레포의 관례다. 게이트웨이 SecurityConfig 가
// 그 접두사를 인증 없이 열어 두고, 대신 외부에는 노출하지 않는 서비스 간 전용 경로로 쓴다.
// health-service 가 user-service 를 부르는 UserServiceClient 도 같은 모양이다.
// 이 호출은 게이트웨이를 거치지 않고 유레카로 직접 가므로 X-User-Id 헤더도 붙지 않는다.
// 알림 대상은 헤더가 아니라 본문 envelope 의 userId 다.
// ──────────────────────────────────────────────────────────────────────
@FeignClient(name = "notification-service")
public interface NotificationClient {

    @PostMapping(value = "/api/v1/internal/notifications/events", consumes = MediaType.APPLICATION_JSON_VALUE)
    void send(@RequestHeader("eventType") String eventType, @RequestBody String envelopeJson);
}
