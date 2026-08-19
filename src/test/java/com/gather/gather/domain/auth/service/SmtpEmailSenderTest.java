package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import jakarta.mail.Message;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessagePreparator;

class SmtpEmailSenderTest {

    private static final String FROM_ADDRESS = "gather.noreply@gmail.com";
    private static final String TO_ADDRESS = "person@example.com";
    private static final String CODE = "123456";

    private MimeMessage mimeMessage;

    @BeforeEach
    void setUp() throws Exception {
        // 실제 SMTP 연결 없이 SmtpEmailSender가 생성하는 MimeMessage를 그대로 검사하기 위해,
        // send(MimeMessagePreparator)를 가로채서 실제 MimeMessage에 prepare만 적용한다.
        mimeMessage = new JavaMailSenderImpl().createMimeMessage();

        JavaMailSender mailSender = mock(JavaMailSender.class);
        doAnswer(
                        invocation -> {
                            MimeMessagePreparator preparator = invocation.getArgument(0);
                            preparator.prepare(mimeMessage);
                            // Transport.send()가 실제 전송 직전에 호출하는 saveChanges()를 재현해,
                            // Content-Type 등 헤더가 실제 내용과 동기화된 상태에서 검증한다.
                            mimeMessage.saveChanges();
                            return null;
                        })
                .when(mailSender)
                .send(any(MimeMessagePreparator.class));

        MailProperties mailProperties = new MailProperties();
        mailProperties.setUsername(FROM_ADDRESS);

        new SmtpEmailSender(mailSender, mailProperties).sendVerificationCode(TO_ADDRESS, CODE);
    }

    @Test
    @DisplayName("최상위 MIME 구조가 plain/html 두 파트를 가진 multipart/alternative로 생성된다")
    void sendVerificationCode_createsTopLevelMultipartAlternativeWithTwoParts() throws Exception {
        ContentType contentType = new ContentType(mimeMessage.getContentType());
        assertThat(contentType.getBaseType()).isEqualToIgnoringCase("multipart/alternative");

        assertThat(mimeMessage.getContent()).isInstanceOf(MimeMultipart.class);
        MimeMultipart multipart = (MimeMultipart) mimeMessage.getContent();
        assertThat(multipart.getCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("첫 번째 파트는 인증코드와 10분 안내가 포함된 plain text이다")
    void sendVerificationCode_firstPartIsPlainTextWithCodeAndExpiration() throws Exception {
        MimeMultipart multipart = (MimeMultipart) mimeMessage.getContent();
        ContentType partContentType = new ContentType(multipart.getBodyPart(0).getContentType());
        assertThat(partContentType.getBaseType()).isEqualToIgnoringCase("text/plain");

        String plainText = (String) multipart.getBodyPart(0).getContent();
        assertThat(plainText).contains(CODE).contains("10분");
    }

    @Test
    @DisplayName("두 번째 파트는 인증코드와 10분 안내가 포함된 HTML이다")
    void sendVerificationCode_secondPartIsHtmlWithCodeAndExpiration() throws Exception {
        MimeMultipart multipart = (MimeMultipart) mimeMessage.getContent();
        ContentType partContentType = new ContentType(multipart.getBodyPart(1).getContentType());
        assertThat(partContentType.getBaseType()).isEqualToIgnoringCase("text/html");

        String html = (String) multipart.getBodyPart(1).getContent();
        assertThat(html).contains(CODE).contains("10분");
    }

    @Test
    @DisplayName("제목, 발신자 표시 이름, From/To 주소가 기존 계약대로 설정된다")
    void sendVerificationCode_keepsSubjectFromAndToContract() throws Exception {
        assertThat(mimeMessage.getSubject()).isEqualTo("[Gather] 이메일 인증 코드");

        InternetAddress from = (InternetAddress) mimeMessage.getFrom()[0];
        assertThat(from.getAddress()).isEqualTo(FROM_ADDRESS);
        assertThat(from.getPersonal()).isEqualTo("Gather");

        InternetAddress to =
                (InternetAddress) mimeMessage.getRecipients(Message.RecipientType.TO)[0];
        assertThat(to.getAddress()).isEqualTo(TO_ADDRESS);
    }
}
