package com.senior.leetmodelbackend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
public class VerificationCodeService {
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private JavaMailSender mailSender;

    private static final String CODE_PREFIX = "LeetModel:";
    private static final long EXPIRATION_MINUTES = 5;

    /**
     * 生成并发送验证码
     */
    public void generateAndSendCode(String email) {
        // TODO: 发送频率校验。相同的邮箱验证码不能重复发送

        // 生成 6 位随机验证码
        String code = String.format("%06d", new Random().nextInt(1000000));

        // 存入 Redis，设置 5 分钟过期
        redisTemplate.opsForValue().set(CODE_PREFIX + email, code, EXPIRATION_MINUTES, TimeUnit.MINUTES);

        // 发送邮件
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("2042175308@qq.com");
        message.setSubject("LeetModel 注册验证码");
        message.setTo(email);
        String mailContent = "LeetModel 网站提醒您，您当前正在通过邮箱注册账号，您的验证码是：" + code + "\n5分钟内有效。";
        message.setText(mailContent);
        mailSender.send(message);
    }

    /**
     * 校验验证码
     */
    public boolean verifyCode(String email, String code) {
        String storedCode = redisTemplate.opsForValue().get(CODE_PREFIX + email);
        return code.equals(storedCode);
    }
}