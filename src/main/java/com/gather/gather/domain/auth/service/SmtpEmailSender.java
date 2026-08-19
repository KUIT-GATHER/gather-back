package com.gather.gather.domain.auth.service;

import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMultipart;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "gather.email.mode", havingValue = "smtp")
public class SmtpEmailSender implements EmailSender {

    private static final String SENDER_NAME = "Gather";
    private static final String SUBJECT = "[Gather] 이메일 인증 코드";
    private static final String MULTIPART_SUBTYPE_ALTERNATIVE = "alternative";
    private static final String HTML_CONTENT_TYPE =
            "text/html;charset=" + StandardCharsets.UTF_8.name();

    // 유효 시간 문구는 AuthService.EMAIL_VERIFICATION_EXPIRATION_MINUTES(10분)와 맞춰야 한다.
    // 메일 클라이언트는 외부 CSS를 지원하지 않으므로 인라인 스타일만 사용한다.
    private static final String BODY_TEMPLATE =
            """
            <div style="max-width: 480px; margin: 0 auto; font-family: 'Apple SD Gothic Neo', 'Malgun Gothic', sans-serif; border: 1px solid #e5e7eb; border-radius: 12px; overflow: hidden;">
                <div style="background-color: #1f2937; padding: 28px; text-align: center;">
                    <span style="color: #ffffff; font-size: 26px; font-weight: 700; letter-spacing: 1px;">Gather</span>
                </div>
                <div style="padding: 36px 28px; color: #374151;">
                    <p style="font-size: 18px; font-weight: 700; margin: 0 0 16px;">이메일 인증 코드</p>
                    <p style="font-size: 14px; line-height: 1.6; margin: 0;">안녕하세요.<br>아래 인증 코드를 입력하여 이메일 인증을 완료해 주세요.</p>
                    <div style="background-color: #f3f4f6; border-radius: 8px; text-align: center; padding: 20px; margin: 24px 0;">
                        <span style="font-size: 32px; font-weight: 700; letter-spacing: 8px; color: #111827;">%s</span>
                    </div>
                    <p style="font-size: 12px; color: #6b7280; line-height: 1.6; margin: 0;">인증 코드는 10분간 유효합니다.<br>본인이 요청하지 않았다면 이 메일을 무시해 주세요.</p>
                </div>
            </div>
            """;

    // BODY_TEMPLATE과 의미상 동일한 내용을 HTML을 지원하지 않는 클라이언트를 위해 plain text로도 제공한다.
    private static final String PLAIN_TEXT_TEMPLATE =
            """
            Gather 이메일 인증 코드

            인증 코드: %s

            인증 코드는 10분간 유효합니다.
            본인이 요청하지 않았다면 이 메일을 무시해 주세요.
            """;

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;

    @Override
    public void sendVerificationCode(String email, String code) {
        mailSender.send(
                mimeMessage -> {
                    MimeMessageHelper helper =
                            new MimeMessageHelper(mimeMessage, StandardCharsets.UTF_8.name());
                    helper.setFrom(mailProperties.getUsername(), SENDER_NAME);
                    helper.setTo(email);
                    helper.setSubject(SUBJECT);

                    // MimeMessageHelper.setText(plain, html)는 multipart 모드를 요구하는데,
                    // 모든 MULTIPART_MODE는 alternative를 mixed/related 안에 중첩시켜 첨부/inline이
                    // 없는 이 메일에는 불필요한 계층을 만든다. 최상위를 바로 multipart/alternative로
                    // 만들기 위해 MimeMultipart를 직접 구성한다.
                    MimeMultipart alternative = new MimeMultipart(MULTIPART_SUBTYPE_ALTERNATIVE);

                    MimeBodyPart plainTextPart = new MimeBodyPart();
                    plainTextPart.setText(
                            PLAIN_TEXT_TEMPLATE.formatted(code), StandardCharsets.UTF_8.name());
                    alternative.addBodyPart(plainTextPart);

                    MimeBodyPart htmlTextPart = new MimeBodyPart();
                    htmlTextPart.setContent(BODY_TEMPLATE.formatted(code), HTML_CONTENT_TYPE);
                    alternative.addBodyPart(htmlTextPart);

                    mimeMessage.setContent(alternative);
                });
    }
}
