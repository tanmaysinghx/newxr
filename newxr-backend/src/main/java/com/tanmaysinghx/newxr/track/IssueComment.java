package com.tanmaysinghx.newxr.track;

import com.tanmaysinghx.newxr.common.BaseEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.tanmaysinghx.newxr.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "track_comments")
public class IssueComment extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Issue issue;
    @ManyToOne(fetch = FetchType.LAZY) private User author;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) @Column(nullable = false) private String body;
    private boolean edited;
}
