package com.gather.gather.domain.region.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "region")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Region {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** 1=도, 2=시/군/구, 4=읍/면/동 (3은 1365 gugunCd가 시/군/구를 구분하지 않아 사용하지 않음) */
    private Integer level;

    /** 1365 API의 sidoCd/gugunCd와 매핑되는 행정구역 코드 */
    @Column(unique = true)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Region parent;

    /** 소속 권역(9버튼). 시도(level=1) 행에만 설정되며, 시군구(level=2) 행은 항상 null이다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_group_id")
    private RegionGroup regionGroup;

    private Region(String name, Integer level, String code, Region parent) {
        this.name = name;
        this.level = level;
        this.code = code;
        this.parent = parent;
    }

    public static Region create(String name, Integer level, String code, Region parent) {
        return new Region(name, level, code, parent);
    }
}
