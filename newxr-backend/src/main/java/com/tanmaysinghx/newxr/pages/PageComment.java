package com.tanmaysinghx.newxr.pages;

import com.tanmaysinghx.newxr.common.BaseEntity;
import com.tanmaysinghx.newxr.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "pages_comments")
public class PageComment extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Page page;
    @ManyToOne(fetch = FetchType.LAZY) private User author;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) @Column(nullable = false) private String body;
}
