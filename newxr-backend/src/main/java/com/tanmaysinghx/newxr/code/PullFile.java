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

/** Before/after snapshot kept when a pull request is merged (the live diff is gone once the branches match). */
@Getter
@Setter
@Entity
@Table(name = "code_pull_files")
public class PullFile extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private PullRequest pull;
    @Column(nullable = false, length = 400) private String path;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) private String beforeContent;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) private String afterContent;
    private boolean added;
}
