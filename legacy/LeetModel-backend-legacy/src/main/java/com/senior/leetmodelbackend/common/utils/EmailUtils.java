package com.senior.leetmodelbackend.common.utils;

import com.senior.leetmodelbackend.common.property.MailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class EmailUtils {

    private final JavaMailSender javaMailSender;
    private final MailProperties mailProperties;

    public void sendTextMail(String to, String subject, String content) {
        sendMail(to, subject, content, false);
    }

    public void sendHtmlMail(String to, String subject, String content) {
        sendMail(to, subject, content, true);
    }

    private void sendMail(String to, String subject, String content, boolean html) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, html, StandardCharsets.UTF_8.name());
            helper.setFrom(mailProperties.getUsername());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, html);
            javaMailSender.send(mimeMessage);
        } catch (MessagingException | RuntimeException exception) {
            throw new IllegalStateException("邮件发送失败", exception);
        }
    }
}
