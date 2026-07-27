package com.gather.gather.domain.auth.service;

/**
 * 계정 종료 후 각 도메인이 자기 데이터를 정리하도록 알리는 이벤트. 탈퇴 API와 연결 해제 웹훅 양쪽에서 같은 이벤트가 나간다.
 *
 * <p>구독자는 {@code AFTER_COMMIT}으로 받되 두 가지를 지켜야 한다. 프로젝트에 {@code @EnableAsync}가 없어 리스너가 요청 스레드에서 동기
 * 실행되기 때문이다.
 *
 * <ul>
 *   <li>리스너 안에서 외부 API를 호출하지 않는다 — 웹훅 응답 3초 제한에 그대로 포함된다.
 *   <li>예외를 밖으로 던지지 않는다 — 이미 커밋된 탈퇴인데 응답만 실패하고, 웹훅 경로에서는 카카오가 실패로 인식한다.
 * </ul>
 */
public record UserWithdrawnEvent(Long userId) {}
