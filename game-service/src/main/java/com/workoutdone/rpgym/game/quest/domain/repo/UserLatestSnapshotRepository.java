package com.workoutdone.rpgym.game.quest.domain.repo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.workoutdone.rpgym.game.quest.domain.aggregate.UserLatestSnapshot;

public interface UserLatestSnapshotRepository {
	Optional<UserLatestSnapshot> findByUserId(UUID userId);
	UserLatestSnapshot save(UserLatestSnapshot snapshot);

	// 파티 퀘스트를 만들 때 멤버 전원의 기준값을 한 번에 가져온다.
	// 이 테이블은 원래 개인 퀘스트의 기준값을 대려고 만든 것인데, 유저 단위로 저장하기 때문에
	// 파티 멤버들의 현재 누적값도 그대로 들어 있다.
	// 예전에는 파티 퀘스트를 만들 때 기준값을 못 구해서 멤버마다 첫 이벤트가 올 때까지
	// 미뤄두는 방식을 생각했는데, 그러면 생성부터 첫 동기화까지 최대 한 주기의 활동이 인정되지 않는다.
	// 하루짜리 퀘스트에서는 손실이 클수도있다. 저녁에 만들면 남은 시간의 큰 비중을 차지한다.
	// 한 번도 동기화한 적 없는 유저는 여기 결과에 없다. 그 멤버만 기준값을 비워두고
	// 첫 이벤트에서 확정한다.
	List<UserLatestSnapshot> findAllByUserIds(Collection<UUID> userIds);
}
