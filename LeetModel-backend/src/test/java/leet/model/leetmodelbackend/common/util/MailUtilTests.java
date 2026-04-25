package leet.model.leetmodelbackend.common.util;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import leet.model.leetmodelbackend.property.QQMailProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 邮件工具类测试，覆盖邮件构建和发送逻辑。
 */
@SpringBootTest(properties = {
        "QQ_MAIL_USER=test-user@example.com",
        "QQ_MAIL_AUTH_CODE=test-auth-code",
        "REDIS_HOST=127.0.0.1",
        "REDIS_PASSWORD=test-password"
})
class MailUtilTests {

    @Autowired
    private MailUtil mailUtil;

    @Autowired
    private CapturingJavaMailSender capturingJavaMailSender;

    @Autowired
    private QQMailProperties qqMailProperties;

    /**
     * 验证纯文本邮件会被正确构建并交给邮件发送器。
     */
    @Test
    void shouldBuildAndSendTextMail() throws Exception {
        mailUtil.sendTextMail("receiver@example.com", "Test Subject", "Test Content");

        assertEquals(1, capturingJavaMailSender.sentMessages.size());
        MimeMessage message = capturingJavaMailSender.sentMessages.get(0);
        assertEquals("Test Subject", message.getSubject());
        assertEquals(qqMailProperties.getUsername(), ((InternetAddress) message.getFrom()[0]).getAddress());
        assertEquals("receiver@example.com", ((InternetAddress) message.getAllRecipients()[0]).getAddress());
        assertTrue(message.getContent().toString().contains("Test Content"));
    }

    /**
     * 测试场景下替换真实邮件发送器，避免触发外部网络发送。
     */
    @TestConfiguration
    static class TestMailConfiguration {

        /**
         * 注册一个可捕获邮件内容的测试版 JavaMailSender。
         */
        @Bean
        @Primary
        CapturingJavaMailSender capturingJavaMailSender() {
            return new CapturingJavaMailSender();
        }
    }

    /**
     * 测试邮件发送器，仅记录被发送的 MimeMessage，不执行真实发送。
     */
    static class CapturingJavaMailSender implements JavaMailSender {

        private final List<MimeMessage> sentMessages = new ArrayList<>();

        @Override
        public MimeMessage createMimeMessage() {
            return new MimeMessage(Session.getInstance(new Properties()));
        }

        @Override
        public MimeMessage createMimeMessage(java.io.InputStream contentStream) throws MailException {
            throw new UnsupportedOperationException("Not required for this test");
        }

        @Override
        public void send(MimeMessage mimeMessage) throws MailException {
            sentMessages.add(mimeMessage);
        }

        @Override
        public void send(MimeMessage... mimeMessages) throws MailException {
            for (MimeMessage mimeMessage : mimeMessages) {
                sentMessages.add(mimeMessage);
            }
        }

        @Override
        public void send(org.springframework.mail.SimpleMailMessage simpleMessage) throws MailException {
            throw new UnsupportedOperationException("Not required for this test");
        }

        @Override
        public void send(org.springframework.mail.SimpleMailMessage... simpleMessages) throws MailException {
            throw new UnsupportedOperationException("Not required for this test");
        }

        @Override
        public void send(MimeMessagePreparator mimeMessagePreparator) throws MailException {
            throw new UnsupportedOperationException("Not required for this test");
        }

        @Override
        public void send(MimeMessagePreparator... mimeMessagePreparators) throws MailException {
            throw new UnsupportedOperationException("Not required for this test");
        }
    }
}