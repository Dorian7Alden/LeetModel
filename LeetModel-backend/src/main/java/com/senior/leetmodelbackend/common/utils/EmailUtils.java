package com.senior.leetmodelbackend.common.utils;

import com.senior.leetmodelbackend.common.property.MailProperties;

import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EmailUtils {

    private static JavaMailSender mailSender;
    private static String mailFrom;

    public EmailUtils(JavaMailSender mailSender, MailProperties mailProperties) {
        EmailUtils.mailSender = mailSender;
        EmailUtils.mailFrom = mailProperties.getUsername();
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
