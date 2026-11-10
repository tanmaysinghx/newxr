package com.tanmaysinghx.newxr.desk;

import com.tanmaysinghx.newxr.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "desk_desks")
public class ServiceDesk extends BaseEntity {
    @Column(nullable = false, unique = true, length = 10) private String deskKey;
    @Column(nullable = false, length = 120) private String name;
    @Column(length = 300) private String description;
    @Column(length = 9) private String color = "#2f6fde";
    @Column(length = 24) private String icon = "monitor";
    private int requestSeq;
}
