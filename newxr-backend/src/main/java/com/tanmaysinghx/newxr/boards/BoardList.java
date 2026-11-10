package com.tanmaysinghx.newxr.boards;

import com.tanmaysinghx.newxr.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "boards_lists")
public class BoardList extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Board board;
    @Column(nullable = false, length = 80) private String name;
    private double position;
    private boolean archived;
}
