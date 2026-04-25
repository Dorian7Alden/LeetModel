package leet.model.leetmodelbackend.common.util;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import leet.model.leetmodelbackend.property.QQMailProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 邮件工具类，统一封装 QQ 邮箱相关的发信逻辑。
 */
@Component
@RequiredArgsConstructor
public class MailUtil {

    private final JavaMailSender javaMailSender;

    private final QQMailProperties qqMailProperties;

    /**
     * 发送纯文本邮件。
     *
     * @param to 收件人地址。
     * @param subject 邮件主题。
     * @param content 邮件正文内容。
     */
    public void sendTextMail(String to, String subject, String content) {
        sendMail(to, subject, content, false);
    }

    /**
     * 发送 HTML 邮件。
     *
     * @param to 收件人地址。
     * @param subject 邮件主题。
     * @param content HTML 格式的邮件正文。
     */
    public void sendHtmlMail(String to, String subject, String content) {
        sendMail(to, subject, content, true);
    }

    /**
     * 统一构建邮件并交给 Spring Mail 发送。
     *
     * @param to 收件人地址。
     * @param subject 邮件主题。
     * @param content 邮件正文内容。
     * @param html true 表示按 HTML 邮件发送，false 表示按纯文本发送。
     */
    private void sendMail(String to, String subject, String content, boolean html) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, html, StandardCharsets.UTF_8.name());
            helper.setFrom(qqMailProperties.getUsername());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, html);
            javaMailSender.send(mimeMessage);
        } catch (MessagingException exception) {
            throw new IllegalStateException("邮件发送失败", exception);
        }
    }
}