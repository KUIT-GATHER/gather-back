package com.gather.gather.domain.posting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.posting.dto.PostingSummaryResponse;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingParticipation;
import com.gather.gather.domain.posting.entity.PostingParticipationStatus;
import com.gather.gather.domain.posting.entity.PostingStatus;
import com.gather.gather.domain.posting.repository.PostingParticipationRepository;
import com.gather.gather.domain.posting.repository.PostingRepository;
import com.gather.gather.global.config.RecommendationProperties;
import com.gather.gather.global.util.CategoryDeadlineScoreCalculator;
import com.gather.gather.global.util.SecurityUtil;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PostingRecommendationServiceTest {

    private static final Long USER_ID = 1L;

    @Mock private PostingRepository postingRepository;
    @Mock private PostingParticipationRepository postingParticipationRepository;
    @Mock private UserRepository userRepository;
    @Mock private RegionNameResolver regionNameResolver;

    private PostingRecommendationService postingRecommendationService;

    @BeforeEach
    void setUp() {
        // 실제 RegionNameResolver.resolve()는 Collectors.toMap으로 HashMap을 반환하며 regionId가
        // null인 posting에 대해서도 안전하게 null을 조회할 수 있다(Map.of()는 null 키 조회 시 NPE를 던지므로 사용하지 않는다).
        when(regionNameResolver.resolve(any())).thenReturn(new HashMap<>());
        postingRecommendationService =
                new PostingRecommendationService(
                        postingRepository,
                        postingParticipationRepository,
                        userRepository,
                        regionNameResolver,
                        new CategoryDeadlineScoreCalculator(
                                new RecommendationProperties(0.7, 0.3, 30)));
    }

    @Test
    @DisplayName(
            "getRecommendedPostings ranks by category match then deadline proximity, excluding "
                    + "postings already applied to")
    void getRecommendedPostings_ranksByScoreAndExcludesApplied() {
        LocalDate today = LocalDate.now();
        Posting p1 = posting(1L, PostingCategory.ENVIRONMENT, today.plusDays(5));
        Posting p2 = posting(2L, PostingCategory.WELFARE, today.plusDays(1));
        Posting p3 = posting(3L, PostingCategory.ENVIRONMENT, today.plusDays(40));
        Posting p4 = posting(4L, PostingCategory.WELFARE, today.plusDays(20));
        Posting p5 = posting(5L, PostingCategory.EDUCATION, today.plusDays(2));
        Posting p6 = posting(6L, PostingCategory.ENVIRONMENT, today); // 이미 지원한 공고 → 제외되어야 함

        when(postingRepository.search(
                        eq(PostingStatus.RECRUITING),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p1, p2, p3, p4, p5, p6)));
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(userWithPreference(PostingCategory.ENVIRONMENT)));
        when(postingParticipationRepository.findByUserIdAndStatusIn(
                        eq(USER_ID), eq(List.of(PostingParticipationStatus.values()))))
                .thenReturn(List.of(PostingParticipation.create(USER_ID, 6L)));

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserIdOrNull).thenReturn(USER_ID);

            List<PostingSummaryResponse> recommended =
                    postingRecommendationService.getRecommendedPostings();

            // p1(cat+near) > p3(cat, far) > p2(no cat, near) > p5(no cat) > p4(no cat, far). p6 제외.
            assertThat(recommended)
                    .extracting(PostingSummaryResponse::id)
                    .containsExactly(1L, 3L, 2L, 5L, 4L);
        }
    }

    @Test
    @DisplayName(
            "getRecommendedPostings falls back to nearest-deadline order for a guest (no login)")
    void getRecommendedPostings_guestFallsBackToDeadlineOrder() {
        LocalDate today = LocalDate.now();
        Posting p1 = posting(1L, PostingCategory.ENVIRONMENT, today.plusDays(5));
        Posting p2 = posting(2L, PostingCategory.WELFARE, today.plusDays(1));
        Posting p3 = posting(3L, PostingCategory.ENVIRONMENT, today.plusDays(40));
        Posting p4 = posting(4L, PostingCategory.WELFARE, today.plusDays(20));
        Posting p5 = posting(5L, PostingCategory.EDUCATION, today.plusDays(2));

        when(postingRepository.search(
                        eq(PostingStatus.RECRUITING),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p1, p2, p3, p4, p5)));

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserIdOrNull).thenReturn(null);

            List<PostingSummaryResponse> recommended =
                    postingRecommendationService.getRecommendedPostings();

            assertThat(recommended)
                    .extracting(PostingSummaryResponse::id)
                    .containsExactly(2L, 5L, 1L, 4L, 3L);
        }
    }

    private Posting posting(Long id, PostingCategory category, LocalDate noticeEndDate) {
        Posting createdPosting =
                Posting.builder()
                        .title("테스트 공고 " + id)
                        .status(PostingStatus.RECRUITING)
                        .category(category)
                        .noticeEndDate(noticeEndDate)
                        .build();
        ReflectionTestUtils.setField(createdPosting, "id", id);
        return createdPosting;
    }

    private User userWithPreference(PostingCategory category) {
        User createdUser =
                User.create(
                        "홍길동",
                        LocalDate.of(2000, 1, 1),
                        Gender.MALE,
                        "01012345678",
                        "test@example.com",
                        "encoded-password",
                        "길동",
                        "소개",
                        true,
                        true,
                        false,
                        null,
                        List.of(category));
        ReflectionTestUtils.setField(createdUser, "id", USER_ID);
        return createdUser;
    }
}
