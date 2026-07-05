package com.gather.gather.support;

import com.gather.gather.global.common.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 보호 경로 통합 테스트를 위한 테스트 전용 컨트롤러. production source를 오염시키지 않기 위해 test source에만 둔다.
 *
 * <p>{@code @SpringBootTest} 컴포넌트 스캔(base package com.gather.gather)에 의해 등록되며, 인증된 요청의 principal
 * 타입과 권한을 그대로 응답해 SecurityContext 상태를 검증할 수 있게 한다.
 */
@RestController
public class TestSecuredController {

    @GetMapping("/test/secured")
    public ApiResponse<Map<String, Object>> secured(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        List<String> authorities =
                authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList();
        return ApiResponse.success(
                Map.of(
                        "principal", principal,
                        "principalType", principal.getClass().getSimpleName(),
                        "authorities", authorities));
    }
}
