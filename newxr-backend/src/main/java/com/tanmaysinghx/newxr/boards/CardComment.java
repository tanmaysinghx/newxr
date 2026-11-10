package com.tanmaysinghx.newxr.boards;

import com.tanmaysinghx.newxr.common.BaseEntity;
import com.tanmaysinghx.newxr.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "boards_comments")
public class CardComment extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Card card;
    @ManyToOne(fetch = FetchType.LAZY) private User author;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) @Column(nullable = false) private String body;
}
