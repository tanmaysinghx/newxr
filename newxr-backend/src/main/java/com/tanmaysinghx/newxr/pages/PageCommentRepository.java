package com.tanmaysinghx.newxr.pages;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PageCommentRepository extends JpaRepository<PageComment, Long> {
    List<PageComment> findByPageOrderByCreatedAtAsc(Page page);
    void deleteByPage(Page page);
}
