package com.tanmaysinghx.newxr.boards;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardCommentRepository extends JpaRepository<CardComment, Long> {
    List<CardComment> findByCardOrderByCreatedAtDesc(Card card);
    long countByCard(Card card);
}
