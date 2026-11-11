package com.tanmaysinghx.newxr.code;

import com.tanmaysinghx.newxr.common.BaseEntity;
import com.tanmaysinghx.newxr.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A comment on the pull request, or on one line of a file when path and line are set. */
@Getter
@Setter
@Entity
@Table(name = "code_pull_comments")
public class PullComment extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private PullRequest pull;
    @ManyToOne(fetch = FetchType.LAZY) private User author;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) @Column(nullable = false) private String body;
    @Column(length = 400) private String path;
    private Integer line;
}
