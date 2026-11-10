package com.tanmaysinghx.newxr.desk;

import com.tanmaysinghx.newxr.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "desk_request_types")
public class RequestType extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private ServiceDesk desk;
    @Column(nullable = false, length = 80) private String name;
    @Column(length = 200) private String description;
    @Column(length = 24) private String icon = "help";
    /** Category shown in queues ("Network", "Hardware"...). */
    @Column(length = 40) private String category;
}
