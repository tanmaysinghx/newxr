package com.tanmaysinghx.newxr.hub;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecentViewRepository extends JpaRepository<RecentView, Long> {
    Optional<RecentView> findByUserIdAndLink(Long userId, String link);
    List<RecentView> findByUserIdOrderByViewedAtDesc(Long userId, Pageable page);
}
