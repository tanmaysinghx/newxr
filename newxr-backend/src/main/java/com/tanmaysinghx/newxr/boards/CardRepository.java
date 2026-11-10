package com.tanmaysinghx.newxr.boards;

import com.tanmaysinghx.newxr.user.User;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CardRepository extends JpaRepository<Card, Long> {
    List<Card> findByListAndArchivedFalseOrderByPositionAsc(BoardList list);
    long countByList_Board(Board board);

    @Query("select coalesce(max(c.position), 0) from Card c where c.list = :list")
    double maxPosition(BoardList list);

    @Query("select c from Card c join c.members m where m = :user and c.archived = false order by c.updatedAt desc")
    List<Card> assignedTo(User user, Pageable page);

    @Query("select c from Card c where c.archived = false and lower(c.title) like lower(concat('%', :q, '%')) order by c.updatedAt desc")
    List<Card> search(String q, Pageable page);
}
