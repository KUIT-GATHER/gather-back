package com.gather.gather.domain.posting.crawler.dto;

/**
 * VMS 목록페이지 카드 1건. 1365 목록조회(VolunteerApiSearchItemDto)와 달리 category/actPlace/regionId를 주지 않는다 — 신규
 * 판별 및 가벼운 기존공고 갱신에만 쓰인다.
 */
public record VmsPostingListItem(
        String seq, String title, String org, String actPeriodText, String statusText) {}
