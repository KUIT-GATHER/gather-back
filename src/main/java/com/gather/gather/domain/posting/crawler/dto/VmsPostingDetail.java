package com.gather.gather.domain.posting.crawler.dto;

/**
 * VMS 상세페이지(recruitView.do) 1건. actStartTime/actEndTime/postAddress/latitude/longitude는 구조화된 필드로
 * 존재하지 않아(본문 자유텍스트에만 섞여 있음) 애초에 담지 않는다 — 1차 구현에서 파싱을 시도하지 않기로 한 결정에 따름.
 */
public record VmsPostingDetail(
        String seq,
        String title,
        String statusText,
        String categoryText,
        String actPeriodText,
        String noticePeriodText,
        String org,
        String countText,
        String actPlace,
        String regionText,
        String managerName,
        String managerEmail,
        String managerTel,
        String content) {}
