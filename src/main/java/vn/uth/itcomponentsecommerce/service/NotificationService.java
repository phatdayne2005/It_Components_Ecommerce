package vn.uth.itcomponentsecommerce.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import vn.uth.itcomponentsecommerce.entity.OrderStatus;
import vn.uth.itcomponentsecommerce.entity.PaymentMethod;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    private final JavaMailSender mailSender;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.from:noreply@techparts.local}")
    private String fromEmail;

    @Value("${app.refund.google-form-url:}")
    private String refundGoogleFormUrl;

    @Value("${app.support.hotline:1900-1234}")
    private String supportHotline;

    public NotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async("notificationTaskExecutor")
    public void sendOrderStatusChangedEmail(String email, String orderCode, OrderStatus oldStatus, OrderStatus newStatus) {
        String subject = "[TechParts] Cập nhật trạng thái đơn hàng " + orderCode;
        String body = wrapHtml(
                "<h2 style=\"color:#1d4ed8;\">Đơn hàng <span style=\"color:#0f172a;\">" + escape(orderCode) + "</span> đã thay đổi trạng thái</h2>" +
                "<p>Trạng thái mới: <strong>" + escape(statusLabel(newStatus)) + "</strong>" +
                (oldStatus != null ? " <span style=\"color:#94a3b8;\">(trước đó: " + escape(statusLabel(oldStatus)) + ")</span>" : "") +
                "</p>" +
                "<p>Bạn có thể vào mục <em>Đơn hàng của tôi</em> để xem chi tiết.</p>"
        );
        sendHtmlMail(email, subject, body);
    }

    @Async("notificationTaskExecutor")
    public void sendOrderConfirmationEmail(String email, String orderCode, BigDecimal total, PaymentMethod paymentMethod) {
        String subject = "[TechParts] Xác nhận đơn hàng " + orderCode;
        String paymentText = paymentMethod == PaymentMethod.SEPAY
                ? "Thanh toán SePay (vui lòng hoàn tất chuyển khoản trong vòng 30 phút)"
                : "Thanh toán khi nhận hàng (COD)";
        String body = wrapHtml(
                "<h2 style=\"color:#0f766e;\">Đặt hàng thành công!</h2>" +
                "<p>Cảm ơn bạn đã đặt hàng tại TechParts.</p>" +
                "<table style=\"border-collapse:collapse;font-size:14px;\">" +
                "<tr><td style=\"padding:4px 8px;color:#64748b;\">Mã đơn:</td><td style=\"padding:4px 8px;\"><strong>" + escape(orderCode) + "</strong></td></tr>" +
                "<tr><td style=\"padding:4px 8px;color:#64748b;\">Tổng tiền:</td><td style=\"padding:4px 8px;\"><strong>" + formatVnd(total) + "</strong></td></tr>" +
                "<tr><td style=\"padding:4px 8px;color:#64748b;\">Thanh toán:</td><td style=\"padding:4px 8px;\">" + escape(paymentText) + "</td></tr>" +
                "</table>" +
                "<p style=\"margin-top:12px;\">Chúng tôi sẽ thông báo khi đơn hàng có cập nhật mới.</p>"
        );
        sendHtmlMail(email, subject, body);
    }

    @Async("notificationTaskExecutor")
    public void sendPaymentReminderEmail(String email, String orderCode, LocalDateTime expireAt) {
        String subject = "[TechParts] Nhắc thanh toán đơn hàng " + orderCode;
        String expireText = expireAt != null ? expireAt.format(DATE_FMT) : "trong ít phút nữa";
        String body = wrapHtml(
                "<h2 style=\"color:#b45309;\">Đơn hàng " + escape(orderCode) + " đang chờ thanh toán</h2>" +
                "<p>Đơn hàng sẽ tự động hủy lúc <strong>" + escape(expireText) + "</strong> nếu bạn chưa hoàn tất thanh toán.</p>" +
                "<p>Vui lòng vào mục <em>Đơn hàng của tôi</em> và bấm <strong>Thanh toán với SePay</strong> để hoàn tất.</p>"
        );
        sendHtmlMail(email, subject, body);
    }

    @Async("notificationTaskExecutor")
    public void sendShippingNotificationEmail(String email, String orderCode, String trackingNumber) {
        String subject = "[TechParts] Đơn hàng " + orderCode + " đã được giao cho đơn vị vận chuyển";
        String trackingHtml = (trackingNumber != null && !trackingNumber.isBlank())
                ? "<p>Mã vận đơn: <strong>" + escape(trackingNumber) + "</strong></p>"
                : "";
        String body = wrapHtml(
                "<h2 style=\"color:#7c3aed;\">Đơn hàng đang được giao</h2>" +
                "<p>Đơn <strong>" + escape(orderCode) + "</strong> đã được bàn giao cho đơn vị vận chuyển.</p>" +
                trackingHtml +
                "<p>Khi nhận được hàng, vui lòng bấm <strong>Đã nhận hàng</strong> trong mục Đơn hàng của tôi để xác nhận.</p>"
        );
        sendHtmlMail(email, subject, body);
    }

    @Async("notificationTaskExecutor")
    public void sendSepayPaymentConfirmedEmail(String email, String orderCode) {
        String subject = "[TechParts] Xác nhận thanh toán SePay - đơn " + orderCode;
        String body = wrapHtml(
                "<h2 style=\"color:#047857;\">Thanh toán thành công</h2>" +
                "<p>Đơn hàng <strong>" + escape(orderCode) + "</strong> đã thanh toán SePay thành công và đang chờ nhân viên xác nhận.</p>"
        );
        sendHtmlMail(email, subject, body);
    }

    @Async("notificationTaskExecutor")
    public void sendRefundRejectedEmail(String email, String orderCode, String rejectNote) {
        String subject = "[TechParts] Yêu cầu hoàn tiền đơn " + orderCode + " bị từ chối";
        String body = wrapHtml(
                "<h2 style=\"color:#b91c1c;\">Yêu cầu hoàn tiền bị từ chối</h2>" +
                "<p>Yêu cầu hoàn tiền cho đơn <strong>" + escape(orderCode) + "</strong> đã bị từ chối.</p>" +
                "<div style=\"padding:12px;background:#fef2f2;border-left:3px solid #dc2626;border-radius:4px;\">" +
                "<div style=\"color:#7f1d1d;font-size:13px;margin-bottom:4px;\">Lý do từ chối:</div>" +
                "<div style=\"color:#991b1b;\">" + escape(rejectNote) + "</div>" +
                "</div>" +
                "<p style=\"margin-top:12px;\">Nếu chưa đồng ý với quyết định này, vui lòng liên hệ CSKH (" + escape(supportHotline) + ") để được hỗ trợ.</p>"
        );
        sendHtmlMail(email, subject, body);
    }

    @Async("notificationTaskExecutor")
    public void sendReturnRefundFormEmail(String email, String orderCode) {
        String subject = "[TechParts] Hướng dẫn hoàn tiền đơn " + orderCode;
        String formBlock;
        if (refundGoogleFormUrl != null && !refundGoogleFormUrl.isBlank()) {
            formBlock = "<p>Vui lòng điền thông tin hoàn tiền theo link bên dưới để bộ phận CSKH liên hệ và hoàn trả số tiền:</p>" +
                    "<p><a href=\"" + escape(refundGoogleFormUrl) + "\" style=\"display:inline-block;padding:8px 16px;background:#0d9488;color:#fff;border-radius:6px;text-decoration:none;\">Mở form hoàn tiền</a></p>";
        } else {
            formBlock = "<p style=\"color:#b45309;\">Form hoàn tiền hiện chưa được cấu hình, vui lòng liên hệ CSKH (" + escape(supportHotline) + ") để được hỗ trợ.</p>";
            log.warn("Refund Google Form URL is missing. orderCode={}", orderCode);
        }
        String body = wrapHtml(
                "<h2 style=\"color:#0d9488;\">Yêu cầu hoàn tiền đã được duyệt</h2>" +
                "<p>Yêu cầu hoàn tiền cho đơn <strong>" + escape(orderCode) + "</strong> đã được chấp nhận.</p>" +
                formBlock
        );
        sendHtmlMail(email, subject, body);
    }

    private void sendHtmlMail(String to, String subject, String htmlBody) {
        if (to == null || to.isBlank()) {
            log.warn("Skip email because recipient is empty. subject={}", subject);
            return;
        }
        if (!mailEnabled) {
            log.info("Email disabled. to={}, subject={}", to, subject);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Email sent. to={}, subject={}", to, subject);
        } catch (MessagingException ex) {
            log.error("Failed to send email. to={}, subject={}, error={}", to, subject, ex.getMessage(), ex);
        } catch (Exception ex) {
            log.error("Unexpected error sending email. to={}, subject={}, error={}", to, subject, ex.getMessage(), ex);
        }
    }

    private String wrapHtml(String inner) {
        return "<!DOCTYPE html><html><body style=\"font-family:Arial,sans-serif;color:#0f172a;line-height:1.55;background:#f8fafc;padding:20px;\">"
                + "<div style=\"max-width:560px;margin:0 auto;background:#ffffff;padding:24px;border-radius:8px;border:1px solid #e2e8f0;\">"
                + inner
                + "<hr style=\"margin:24px 0;border:none;border-top:1px solid #e2e8f0;\"/>"
                + "<p style=\"color:#94a3b8;font-size:12px;\">Email từ TechParts. Vui lòng không phản hồi trực tiếp tới địa chỉ này.</p>"
                + "</div></body></html>";
    }

    private String statusLabel(OrderStatus status) {
        if (status == null) return "";
        return switch (status) {
            case PENDING_PAYMENT -> "Chờ thanh toán";
            case PENDING_CONFIRMATION -> "Chờ xác nhận";
            case PROCESSING -> "Đang xử lý";
            case SHIPPING -> "Đang giao hàng";
            case DELIVERED -> "Đã giao hàng";
            case REFUND_REQUESTED -> "Yêu cầu hoàn tiền";
            case REFUND_REJECTED -> "Từ chối hoàn tiền";
            case RETURN_REFUND -> "Trả hàng / Hoàn tiền";
            case CANCELLED -> "Đã hủy";
        };
    }

    private String formatVnd(BigDecimal amount) {
        if (amount == null) return "0đ";
        return String.format("%,.0f", amount.doubleValue()) + "đ";
    }

    private String escape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
