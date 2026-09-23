package com.workoutdone.rpgym.game.quest.adapter.out.persistence;

import com.workoutdone.rpgym.game.quest.domain.aggregate.UserLatestSnapshot;
import com.workoutdone.rpgym.game.quest.domain.repo.UserLatestSnapshotRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserLatestSnapshotRepositoryImpl implements UserLatestSnapshotRepository {

    private final UserLatestSnapshotJpaRepository userLatestSnapshotJpaRepository;

    @Override
    public Optional<UserLatestSnapshot> findByUserId(UUID userId) {
        return userLatestSnapshotJpaRepository.findById(userId);
    }

    @Override
    public UserLatestSnapshot save(UserLatestSnapshot snapshot) {
        return userLatestSnapshotJpaRepository.save(snapshot);
    }

    // userId 가 이 테이블의 기본키라서 Spring Data 가 주는 것을 그대로 쓴다.
    // 한 번도 동기화한 적 없는 유저는 결과에 빠져서 돌아온다. 요청한 수보다 적을 수 있다.
    @Override
    public List<UserLatestSnapshot> findAllByUserIds(Collection<UUID> userIds) {
        return userLatestSnapshotJpaRepository.findAllById(userIds);
    }
}
