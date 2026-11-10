package com.tanmaysinghx.newxr.boards;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardListRepository extends JpaRepository<BoardList, Long> {
    List<BoardList> findByBoardAndArchivedFalseOrderByPositionAsc(Board board);
}
