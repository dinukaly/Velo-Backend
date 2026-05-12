package com.dinukaly.velo.service.custom;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.base-url}")
    private String backendBaseUrl;

    @Value("${spring.mail.username}")
    private String fromAddress;

    @Async
    public void sendVerificationEmail(String toEmail, String userName, String rawToken) {
        String verifyUrl = backendBaseUrl + "/api/v1/auth/verify-email?token=" + rawToken;

        String html = buildHtml(userName, verifyUrl);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject("Verify your Velo account");
            helper.setText(html, true); // true = isHtml

            mailSender.send(message);
            log.info("[Email] Verification email sent to: {}", toEmail);
        } catch (MessagingException e) {
            log.error("[Email] Failed to send verification email to {}: {}", toEmail, e.getMessage());
           // user can request a resend
        }
    }

    private String buildHtml(String userName, String verifyUrl) {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8"/>
              <style>
                body { font-family: 'Segoe UI', Arial, sans-serif; background:#0f0f0f; color:#e0e0e0; margin:0; padding:0; }
                .wrapper { max-width:560px; margin:40px auto; background:#1a1a1a; border-radius:12px; overflow:hidden; border:1px solid #2a2a2a; }
                .header { background:linear-gradient(135deg,#6366f1,#8b5cf6); padding:36px 40px; text-align:center; }
                .header h1 { margin:0; color:#fff; font-size:26px; letter-spacing:1px; }
                .header p  { margin:6px 0 0; color:rgba(255,255,255,0.75); font-size:13px; }
                .body { padding:36px 40px; }
                .body p { line-height:1.7; color:#c0c0c0; margin:0 0 16px; }
                .btn { display:inline-block; margin:8px 0 24px; padding:14px 32px; background:linear-gradient(135deg,#6366f1,#8b5cf6); color:#fff!important; text-decoration:none; border-radius:8px; font-size:15px; font-weight:600; }
                .note { font-size:12px; color:#666; }
                .footer { padding:20px 40px; border-top:1px solid #2a2a2a; text-align:center; font-size:12px; color:#444; }
              </style>
            </head>
            <body>
              <div class="wrapper">
                <div class="header">
                  <h1>👨🏻‍💻 Velo IDE</h1>
                  <p>Your cloud development environment</p>
                </div>
                <div class="body">
                  <p>Hi <strong>%s</strong>,</p>
                  <p>Thanks for signing up! Click the button below to verify your email address and activate your account.</p>
                  <p style="text-align:center">
                    <a class="btn" href="%s">Verify my email</a>
                  </p>
                  <p class="note">This link expires in <strong>24 hours</strong> and can only be used once.<br/>
                  If you didn't create a Velo account, you can safely ignore this email.</p>
                </div>
                <div class="footer">© 2025 Velo IDE · All rights reserved</div>
              </div>
            </body>
            </html>
            """.formatted(userName, verifyUrl);
    }
}
