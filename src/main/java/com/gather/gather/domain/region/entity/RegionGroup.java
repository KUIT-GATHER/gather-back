package com.gather.gather.domain.region.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "region_group")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegionGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 1365 행정구역 코드가 아닌 서비스 내부 코드 (예: GRP_GYEONGSANG) */
    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 20)
    private String name;

    @Column(nullable = false)
    private Integer sortOrder;

    private RegionGroup(String code, String name, Integer sortOrder) {
        this.code = code;
        this.name = name;
        this.sortOrder = sortOrder;
    }

    public static RegionGroup create(String code, String name, Integer sortOrder) {
        return new RegionGroup(code, name, sortOrder);
    }
}
