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

/** A branch. Files forked from {@link #baseBranch} remember the content hash they started from (three-way merge base). */
@Getter
@Setter
@Entity
@Table(name = "code_branches", uniqueConstraints = @UniqueConstraint(columnNames = {"repo_id", "name"}))
public class Branch extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private CodeRepo repo;
    @Column(nullable = false, length = 100) private String name;
    @Column(length = 100) private String baseBranch;
    @Column(length = 40) private String headSha;
    @ManyToOne(fetch = FetchType.LAZY) private User createdBy;
}
