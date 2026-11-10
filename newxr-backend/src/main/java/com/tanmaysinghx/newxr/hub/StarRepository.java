package com.tanmaysinghx.newxr.hub;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StarRepository extends JpaRepository<Star, Long> {
    Optional<Star> findByUserIdAndLink(Long userId, String link);
    List<Star> findByUserIdOrderByCreatedAtDesc(Long userId);
    boolean existsByUserIdAndLink(Long userId, String link);
}
