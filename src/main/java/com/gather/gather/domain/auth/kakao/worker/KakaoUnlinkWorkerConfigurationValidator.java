package com.gather.gather.domain.auth.kakao.worker;

import com.gather.gather.domain.auth.kakao.admin.config.KakaoAdminProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KakaoUnlinkWorkerConfigurationValidator implements InitializingBean {

    private final KakaoAdminProperties adminProperties;
    private final KakaoUnlinkWorkerProperties workerProperties;

    @Override
    public void afterPropertiesSet() {
        if (workerProperties.enabled() && !adminProperties.enabled()) {
            throw new IllegalStateException(
                    "kakao.admin.unlink-worker.enabled=true requires kakao.admin.enabled=true");
        }
    }
}
