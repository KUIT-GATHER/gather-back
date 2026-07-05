package com.gather.gather.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger(OpenAPI)에 JWT Bearer 인증을 전역으로 등록한다.
 *
 * <p>전역 적용을 선택한 이유: 앞으로 추가될 도메인 API 대부분이 인증을 필요로 하므로 전역이 기본값으로 맞고, per-API 어노테이션 방식은 새 컨트롤러에서 누락되면
 * Swagger에서 인증 테스트가 불가능해지는 함정이 있다.
 *
 * <p>트레이드오프(수용됨): 공개 API(auth/regions/categories)도 Swagger에서 잠금 아이콘이 표시되고 Authorize 시 불필요한
 * Authorization 헤더가 붙는다. 실제 동작에는 무해하다.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        SecurityScheme bearerScheme =
                new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT");

        return new OpenAPI()
                .components(new Components().addSecuritySchemes(BEARER_SCHEME_NAME, bearerScheme))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME));
    }
}
