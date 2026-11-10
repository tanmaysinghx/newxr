package com.tanmaysinghx.newxr.pages;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PageRepository extends JpaRepository<Page, Long> {
    List<Page> findBySpaceAndKindOrderByPositionAsc(Space space, String kind);
    List<Page> findBySpaceAndKindOrderByPublishedAtDesc(Space space, String kind);
    List<Page> findByParentOrderByPositionAsc(Page parent);
    List<Page> findBySpaceOrderByUpdatedAtDesc(Space space, Pageable page);
    List<Page> findBySpaceAndPinnedTrueOrderByPositionAsc(Space space);
    long countBySpace(Space space);

    @Query("select coalesce(max(p.position), 0) from Page p where p.space = :space")
    double maxPosition(Space space);

    @Query("""
        select p from Page p where p.status = 'PUBLISHED' and (lower(p.title) like lower(concat('%', :q, '%'))
          or lower(cast(p.body as String)) like lower(concat('%', :q, '%'))) order by p.updatedAt desc""")
    List<Page> search(String q, Pageable page);

    @Query("select distinct p.lastEditor.id from Page p where p.space = :space and p.lastEditor is not null")
    List<Long> contributorIds(Space space);
}
