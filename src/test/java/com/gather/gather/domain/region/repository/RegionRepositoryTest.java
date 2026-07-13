package com.gather.gather.domain.region.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.gather.gather.domain.region.entity.Region;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link RegionRepository#findIdsIncludingChildren(Long)}의 실제 DB 동작 검증. 테스트 전용 code로 실제 region 시드
 * 데이터와 충돌을 피한다.
 */
@SpringBootTest
@Transactional
class RegionRepositoryTest {

    @Autowired private RegionRepository regionRepository;

    @Test
    void findIdsIncludingChildren_returnsParentAndChildIds_whenParentIdGiven() {
        Region parent = regionRepository.save(Region.create("테스트도", 1, "9990000", null));
        Region child = regionRepository.save(Region.create("테스트구", 3, "9990001", parent));

        var ids = regionRepository.findIdsIncludingChildren(parent.getId());

        assertThat(ids).containsExactlyInAnyOrder(parent.getId(), child.getId());
    }

    @Test
    void findIdsIncludingChildren_returnsSidoGuAndDongIds_whenSidoIdGiven() {
        Region sido = regionRepository.save(Region.create("테스트도5", 1, "9990011", null));
        Region gu = regionRepository.save(Region.create("테스트구5", 2, "9990012", sido));
        Region otherGu = regionRepository.save(Region.create("테스트구6", 2, "9990013", sido));
        Region dong1 = regionRepository.save(Region.create("테스트동5", 4, "9990014", gu));
        Region dong2 = regionRepository.save(Region.create("테스트동6", 4, "9990015", otherGu));

        var ids = regionRepository.findIdsIncludingChildren(sido.getId());

        assertThat(ids)
                .containsExactlyInAnyOrder(
                        sido.getId(), gu.getId(), otherGu.getId(), dong1.getId(), dong2.getId());
    }

    @Test
    void findIdsIncludingChildren_returnsOnlyItself_whenLeafIdGiven() {
        Region parent = regionRepository.save(Region.create("테스트도2", 1, "9990002", null));
        Region child = regionRepository.save(Region.create("테스트구2", 3, "9990003", parent));

        var ids = regionRepository.findIdsIncludingChildren(child.getId());

        assertThat(ids).containsExactly(child.getId());
    }

    @Test
    void findIdsIncludingChildren_returnsEmptyList_whenRegionIdDoesNotExist() {
        var ids = regionRepository.findIdsIncludingChildren(-1L);

        assertThat(ids).isEmpty();
    }

    @Test
    void findByParentId_returnsOnlyDirectChildren_notGrandchildrenOrSiblings() {
        Region sido = regionRepository.save(Region.create("테스트도3", 1, "9990004", null));
        Region gu = regionRepository.save(Region.create("테스트구3", 2, "9990005", sido));
        Region otherGu = regionRepository.save(Region.create("테스트구4", 2, "9990006", sido));
        Region dong1 = regionRepository.save(Region.create("테스트동1", 4, "9990007", gu));
        Region dong2 = regionRepository.save(Region.create("테스트동2", 4, "9990008", gu));
        regionRepository.save(Region.create("테스트동3", 4, "9990009", otherGu));

        var children = regionRepository.findByParentId(gu.getId());

        assertThat(children)
                .extracting(Region::getId)
                .containsExactlyInAnyOrder(dong1.getId(), dong2.getId());
    }

    @Test
    void findByParentId_returnsEmptyList_whenRegionHasNoChildren() {
        Region leaf = regionRepository.save(Region.create("테스트동4", 4, "9990010", null));

        var children = regionRepository.findByParentId(leaf.getId());

        assertThat(children).isEmpty();
    }
}
