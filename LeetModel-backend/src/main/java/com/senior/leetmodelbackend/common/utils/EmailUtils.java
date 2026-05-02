package com.senior.leetmodelbackend.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EmailUtils {

    private static JavaMailSender mailSender;
    private static String mailFrom;

    @Autowired
    public void setMailSender(JavaMailSender mailSender) {
        EmailUtils.mailSender = mailSender;
    }

    @Value("${spring.mail.username}")
    public void setMailFrom(String mailFrom) {
        EmailUtils.mailFrom = mailFrom;
    }

    /**
     * 发送邮件
     * 
     * @param email   收件人邮箱
     * @param subject 邮件主题
     * @param content 邮件内容
     */
    public static void sendEmail(String email, String subject, String content) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setSubject(subject);
        message.setTo(email);
        message.setText(content);
        mailSender.send(message);
    }
}
