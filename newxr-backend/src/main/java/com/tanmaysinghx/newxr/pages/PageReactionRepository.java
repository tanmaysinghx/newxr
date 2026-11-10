package com.tanmaysinghx.newxr.pages;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PageReactionRepository extends JpaRepository<PageReaction, Long> {
    List<PageReaction> findByPage(Page page);
    Optional<PageReaction> findByPageAndUserIdAndEmoji(Page page, Long userId, String emoji);
    void deleteByPage(Page page);
}
