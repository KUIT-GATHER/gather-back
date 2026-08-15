package com.gather.gather.global.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ActivityRegionResolverTest {

    private static final Long USER_ID = 1L;
    private static final Long REGION_ID = 2L;

    @Mock private UserRepository userRepository;
    @Mock private RegionRepository regionRepository;

    private ActivityRegionResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ActivityRegionResolver(userRepository, regionRepository);
    }

    @Test
    @DisplayName("resolveFilterRegionIds returns null (no filter) for a guest (no login)")
    void resolveFilterRegionIds_guest_returnsNull() {
        List<Long> result = resolver.resolveFilterRegionIds(null);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("resolveFilterRegionIds returns null when the authenticated user no longer exists")
    void resolveFilterRegionIds_userNotFound_returnsNull() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        List<Long> result = resolver.resolveFilterRegionIds(USER_ID);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("resolveFilterRegionIds returns null when the user hasn't set an activity region")
    void resolveFilterRegionIds_noActivityRegion_returnsNull() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWithRegion(null)));

        List<Long> result = resolver.resolveFilterRegionIds(USER_ID);

        assertThat(result).isNull();
        verify(regionRepository, never()).findIdsIncludingChildren(eq(REGION_ID));
    }

    @Test
    @DisplayName(
            "resolveFilterRegionIds returns the region and its descendant ids when the user has"
                    + " set an activity region")
    void resolveFilterRegionIds_hasActivityRegion_returnsRegionAndDescendantIds() {
        Region region = region(REGION_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWithRegion(region)));
        when(regionRepository.findIdsIncludingChildren(REGION_ID))
                .thenReturn(List.of(REGION_ID, 20L, 21L));

        List<Long> result = resolver.resolveFilterRegionIds(USER_ID);

        assertThat(result).containsExactly(REGION_ID, 20L, 21L);
    }

    private Region region(Long id) {
        Region createdRegion = Region.create("서울특별시 강남구", 2, "1168000000", null);
        ReflectionTestUtils.setField(createdRegion, "id", id);
        return createdRegion;
    }

    private User userWithRegion(Region activityRegion) {
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
                        activityRegion,
                        List.of());
        ReflectionTestUtils.setField(createdUser, "id", USER_ID);
        return createdUser;
    }
}
