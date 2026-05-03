package vn.uth.itcomponentsecommerce.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import vn.uth.itcomponentsecommerce.entity.OrderStatus;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final JavaMailSender mailSender;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.from:noreply@techparts.local}")
    private String fromEmail;

    @Value("${app.refund.google-form-url:}")
    private String refundGoogleFormUrl;

    public NotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async("notificationTaskExecutor")
    public void sendOrderStatusChangedEmail(String email, String orderCode, OrderStatus oldStatus, OrderStatus newStatus) {
        String subject = "[TechParts] Cap nhat trang thai don hang " + orderCode;
        String body = "Don hang " + orderCode + " da duoc cap nhat trang thai tu "
                + oldStatus + " sang " + newStatus + ".";
        sendMail(email, subject, body);
    }

    @Async("notificationTaskExecutor")
    public void sendSepayPaymentConfirmedEmail(String email, String orderCode) {
        String subject = "[TechParts] Xac nhan thanh toan SePay " + orderCode;
        String body = "Don hang " + orderCode + " da thanh toan SePay thanh cong va dang cho xac nhan.";
        sendMail(email, subject, body);
    }

    @Async("notificationTaskExecutor")
    public void sendReturnRefundFormEmail(String email, String orderCode) {
        String subject = "[TechParts] Huong dan hoan tien don " + orderCode;
        StringBuilder body = new StringBuilder();
        body.append("Yeu cau hoan tien cho don ").append(orderCode)
                .append(" da duoc chap nhan. Vui long dien thong tin hoan tien.");
        if (refundGoogleFormUrl != null && !refundGoogleFormUrl.isBlank()) {
            body.append("\n\nGoogle Form: ").append(refundGoogleFormUrl);
        } else {
            body.append("\n\nGoogle Form chua duoc cau hinh, vui long lien he CSKH.");
            log.warn("Refund Google Form URL is missing. orderCode={}", orderCode);
        }
        sendMail(email, subject, body.toString());
    }

    private void sendMail(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            log.warn("Skip email because recipient is empty. subject={}", subject);
            return;
        }
        if (!mailEnabled) {
            log.info("Email disabled. to={}, subject={}, body={}", to, subject, body);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Email sent. to={}, subject={}", to, subject);
        } catch (Exception ex) {
            log.error("Failed to send email. to={}, subject={}, error={}", to, subject, ex.getMessage(), ex);
        }
    }
}
