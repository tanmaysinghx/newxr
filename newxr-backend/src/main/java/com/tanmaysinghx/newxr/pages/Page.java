package com.tanmaysinghx.newxr.pages;

import com.tanmaysinghx.newxr.common.BaseEntity;
import com.tanmaysinghx.newxr.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "pages_pages", indexes = @Index(name = "ix_page_space", columnList = "space_id,parent_id"))
public class Page extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Space space;
    @ManyToOne(fetch = FetchType.LAZY) private Page parent;
    @Column(nullable = false, length = 255) private String title;
    /** Published content (Markdown). */
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) private String body;
    /** Unpublished edits; null when the draft equals the published version. */
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) private String draftBody;
    @Column(length = 255) private String draftTitle;
    /** PAGE or BLOG. */
    @Column(nullable = false, length = 8) private String kind = "PAGE";
    /** DRAFT (never published) or PUBLISHED. */
    @Column(nullable = false, length = 12) private String status = "DRAFT";
    @ManyToOne(fetch = FetchType.LAZY) private User author;
    @ManyToOne(fetch = FetchType.LAZY) private User lastEditor;
    private int version;
    private double position;
    private boolean pinned;
    @Column(length = 400) private String banner;
    private Instant publishedAt;
}
