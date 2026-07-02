package com.gather.gather.domain.posting.entity;

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

    /** 1=도, 2=시, 3=구, 4=동 */
    private Integer level;

    /** 1365 API의 sidoCd/gugunCd와 매핑되는 행정구역 코드 */
    @Column(unique = true)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Region parent;

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
