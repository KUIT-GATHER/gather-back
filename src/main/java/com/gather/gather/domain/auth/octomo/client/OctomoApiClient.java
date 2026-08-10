package com.gather.gather.domain.auth.octomo.client;

import com.gather.gather.domain.auth.octomo.config.OctomoProperties;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.Duration;
import java.util.Base64;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class OctomoApiClient {

    private static final String MESSAGE_EXISTS_PATH = "/octomo/v1/public/message/exists";
    private static final String QR_CODE_PATH = "/octomo/v1/public/message/qr-code";
    private static final String AUTHORIZATION_PREFIX = "Octomo ";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);
    private static final String PNG_DATA_URL_PREFIX = "data:image/png;base64,";
    private static final byte[] PNG_SIGNATURE = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private final RestClient restClient;
    private final String apiKey;

    @Autowired
    public OctomoApiClient(RestClient.Builder restClientBuilder, OctomoProperties properties) {
        this(
                restClientBuilder
                        .clone()
                        .baseUrl(properties.baseUrl())
                        .requestFactory(timeoutRequestFactory())
                        .build(),
                properties.apiKey());
    }

    OctomoApiClient(RestClient restClient, String apiKey) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    public boolean existsMessage(String mobileNumber, String text, int withinMinutes) {
        requireApiKey();
        MessageExistsResponse response =
                call(
                        () ->
                                restClient
                                        .post()
                                        .uri(MESSAGE_EXISTS_PATH)
                                        .header(
                                                HttpHeaders.AUTHORIZATION,
                                                AUTHORIZATION_PREFIX + apiKey)
                                        .accept(MediaType.APPLICATION_JSON)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(
                                                new MessageExistsRequest(
                                                        mobileNumber, text, withinMinutes))
                                        .retrieve()
                                        .onStatus(
                                                status ->
                                                        status.value()
                                                                == HttpStatus.TOO_MANY_REQUESTS
                                                                        .value(),
                                                (request, ignored) -> {
                                                    throw new BusinessException(
                                                            ErrorCode
                                                                    .PHONE_VERIFICATION_RATE_LIMITED);
                                                })
                                        .onStatus(
                                                HttpStatusCode::isError,
                                                (request, responseError) -> {
                                                    log.warn(
                                                            "OCTOMO 문자 조회가 실패했습니다. status={}, path={}",
                                                            responseError.getStatusCode(),
                                                            request.getURI().getPath());
                                                    throw new BusinessException(
                                                            ErrorCode
                                                                    .PHONE_VERIFICATION_PROVIDER_UNAVAILABLE);
                                                })
                                        .body(MessageExistsResponse.class),
                        "문자 조회");
        if (response == null || response.exists() == null) {
            throw new BusinessException(ErrorCode.PHONE_VERIFICATION_PROVIDER_UNAVAILABLE);
        }
        return response.exists();
    }

    public String createQrCode(String text) {
        requireApiKey();
        QrCodeResponse response =
                call(
                        () ->
                                restClient
                                        .post()
                                        .uri(QR_CODE_PATH)
                                        .header(
                                                HttpHeaders.AUTHORIZATION,
                                                AUTHORIZATION_PREFIX + apiKey)
                                        .accept(MediaType.APPLICATION_JSON)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(new QrCodeRequest(text))
                                        .retrieve()
                                        .onStatus(
                                                status ->
                                                        status.value()
                                                                == HttpStatus.TOO_MANY_REQUESTS
                                                                        .value(),
                                                (request, ignored) -> {
                                                    throw new BusinessException(
                                                            ErrorCode
                                                                    .PHONE_VERIFICATION_RATE_LIMITED);
                                                })
                                        .onStatus(
                                                HttpStatusCode::isError,
                                                (request, responseError) -> {
                                                    log.warn(
                                                            "OCTOMO QR 발급이 실패했습니다. status={}, path={}",
                                                            responseError.getStatusCode(),
                                                            request.getURI().getPath());
                                                    throw new BusinessException(
                                                            ErrorCode
                                                                    .PHONE_VERIFICATION_PROVIDER_UNAVAILABLE);
                                                })
                                        .body(QrCodeResponse.class),
                        "QR 발급");
        if (response == null || !isValidPngDataUrl(response.qrCode())) {
            throw new BusinessException(ErrorCode.PHONE_VERIFICATION_PROVIDER_UNAVAILABLE);
        }
        return response.qrCode();
    }

    private static ClientHttpRequestFactory timeoutRequestFactory() {
        return ClientHttpRequestFactoryBuilder.simple()
                .build(
                        ClientHttpRequestFactorySettings.defaults()
                                .withConnectTimeout(CONNECT_TIMEOUT)
                                .withReadTimeout(READ_TIMEOUT));
    }

    private <T> T call(Supplier<T> apiCall, String operation) {
        try {
            return apiCall.get();
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            log.error("OCTOMO {} 호출에 실패했습니다.", operation, exception);
            throw new BusinessException(ErrorCode.PHONE_VERIFICATION_PROVIDER_UNAVAILABLE);
        }
    }

    private void requireApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("OCTOMO API 키가 설정되지 않았습니다.");
            throw new BusinessException(ErrorCode.PHONE_VERIFICATION_PROVIDER_UNAVAILABLE);
        }
    }

    private boolean isValidPngDataUrl(String qrCode) {
        if (qrCode == null || !qrCode.startsWith(PNG_DATA_URL_PREFIX)) {
            return false;
        }
        String payload = qrCode.substring(PNG_DATA_URL_PREFIX.length());
        if (payload.isEmpty()) {
            return false;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(payload);
            if (decoded.length < PNG_SIGNATURE.length) {
                return false;
            }
            for (int index = 0; index < PNG_SIGNATURE.length; index++) {
                if (decoded[index] != PNG_SIGNATURE[index]) {
                    return false;
                }
            }
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private record MessageExistsRequest(String mobileNum, String text, int withinMinutes) {}

    private record MessageExistsResponse(Boolean exists) {}

    private record QrCodeRequest(String text) {}

    private record QrCodeResponse(String qrCode) {}
}
