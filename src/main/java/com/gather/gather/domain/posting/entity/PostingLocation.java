package com.gather.gather.domain.posting.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 한 공고가 2·3번째로 갖는 추가 활동장소. 1번째 장소는 {@link Posting}의 postAddress/latitude/longitude로
 * 표현된다(docs/devplan.md 섹션 8.2).
 */
@Entity
@Getter
@Table(name = "volunteer_posting_location")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostingLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** volunteer_posting.id 참조, ID만 보관(연관관계 없음, region_id/category_id와 동일 컨벤션). */
    @Column(name = "posting_id", nullable = false)
    private Long postingId;

    /** 2 또는 3 (1번째 장소는 Posting 자신의 컬럼). */
    @Column(name = "location_seq", nullable = false)
    private Integer locationSeq;

    @Column(nullable = false)
    private String address;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 11, scale = 7)
    private BigDecimal longitude;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private PostingLocation(
            Long postingId,
            Integer locationSeq,
            String address,
            BigDecimal latitude,
            BigDecimal longitude) {
        this.postingId = postingId;
        this.locationSeq = locationSeq;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.createdAt = LocalDateTime.now();
    }

    public static PostingLocation create(
            Long postingId,
            Integer locationSeq,
            String address,
            BigDecimal latitude,
            BigDecimal longitude) {
        return new PostingLocation(postingId, locationSeq, address, latitude, longitude);
    }
}
