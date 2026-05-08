package vn.uth.itcomponentsecommerce.service;

import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import vn.uth.itcomponentsecommerce.dto.OrderEmailContext;
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

    @Value("${app.web.base-url:http://localhost:8080}")
    private String webBaseUrl;

    @Value("${app.support.hotline:1900-1234}")
    private String supportHotline;

    @Value("${spring.mail.host:NOT_SET}")
    private String configuredHost;

    @Value("${spring.mail.port:0}")
    private int configuredPort;

    @Value("${spring.mail.username:NOT_SET}")
    private String configuredUsername;

    @Value("${spring.mail.password:NOT_SET}")
    private String configuredPassword;

    public NotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /** Log config trạng thái lúc khởi động — giúp xác minh .env có được load đúng hay không. */
    @PostConstruct
    private void logEmailConfig() {
        String maskedPwd = configuredPassword == null || configuredPassword.equals("NOT_SET")
                ? "(NOT_SET)"
                : "(set, " + configuredPassword.length() + " chars)";
        log.info("=== EMAIL CONFIG AT STARTUP ===");
        log.info("  app.mail.enabled = {}", mailEnabled);
        log.info("  spring.mail.host = {}", configuredHost);
        log.info("  spring.mail.port = {}", configuredPort);
        log.info("  spring.mail.username = {}", configuredUsername);
        log.info("  spring.mail.password = {}", maskedPwd);
        log.info("  app.mail.from = {}", fromEmail);
        log.info("  app.web.base-url = {}", webBaseUrl);
        if (!mailEnabled) {
            log.warn("  -> Mail is DISABLED. All sendHtmlMail calls will be skipped silently.");
        } else if (configuredUsername == null || configuredUsername.equals("NOT_SET") || configuredUsername.isBlank()) {
            log.error("  -> Mail enabled but spring.mail.username is empty. .env may not be loaded.");
        } else {
            log.info("  -> Mail looks ready. Attempting to send via {}.", configuredHost);
        }
        log.info("================================");
    }

    /** Gửi đồng bộ — dùng cho endpoint debug, không qua @Async để bắt exception trả về client. */
    public void sendTestMailSync(String to) throws Exception {
        if (!mailEnabled) {
            throw new IllegalStateException("app.mail.enabled = false. Bật trong .env hoặc env vars rồi restart.");
        }
        String body = wrapHtml(
                "<h2 style=\"color:#0d9488;\">Test email từ Spring Boot</h2>" +
                "<p>Nếu bạn đọc được email này, JavaMailSender đang hoạt động bình thường.</p>" +
                "<p style=\"color:#64748b;font-size:13px;\">Thời gian: " + LocalDateTime.now().format(DATE_FMT) + "</p>"
        );
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject("[TechParts] Test email synchronous");
        helper.setText(body, true);
        mailSender.send(message);
        log.info("Sync test email sent to {}", to);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  EMAIL TEMPLATES
    // ════════════════════════════════════════════════════════════════════════

    @Async("notificationTaskExecutor")
    public void sendOrderConfirmationEmail(OrderEmailContext ctx) {
        String subject = "[TechParts] Xác nhận đơn hàng " + ctx.orderCode;
        String paymentText = ctx.paymentMethod == PaymentMethod.SEPAY
                ? "Thanh toán SePay (vui lòng hoàn tất chuyển khoản trong vòng 30 phút)"
                : "Thanh toán khi nhận hàng (COD)";
        String body = wrapHtml(
                "<h2 style=\"color:#0f766e;\">Đặt hàng thành công!</h2>" +
                "<p>Cảm ơn bạn đã đặt hàng tại TechParts. Đơn hàng của bạn đã được ghi nhận.</p>" +
                renderOrderHeaderRows(ctx) +
                "<tr><td style=\"padding:4px 8px;color:#64748b;\">Hình thức thanh toán:</td><td style=\"padding:4px 8px;\">" + escape(paymentText) + "</td></tr>" +
                "</table>" +
                renderItemsTable(ctx) +
                renderTotalsBox(ctx) +
                renderRecipientBox(ctx) +
                renderViewOrderButton() +
                "<p style=\"margin-top:18px;color:#64748b;font-size:13px;\">Chúng tôi sẽ thông báo qua email khi đơn hàng có cập nhật mới.</p>"
        );
        sendHtmlMail(ctx.recipientEmail, subject, body);
    }

    @Async("notificationTaskExecutor")
    public void sendOrderStatusChangedEmail(OrderEmailContext ctx, OrderStatus oldStatus, OrderStatus newStatus) {
        // Bỏ qua các status đã có email chuyên biệt khác (tránh spam khách 2 email gần nhau)
        // - REFUND_COMPLETED có sendRefundCompletedEmail riêng
        // - SHIPPING có sendShippingNotificationEmail riêng
        String subject = "[TechParts] Đơn " + ctx.orderCode + ": " + statusLabel(newStatus);
        String body = wrapHtml(
                "<h2 style=\"color:#1d4ed8;\">Cập nhật trạng thái đơn hàng</h2>" +
                "<p>Trạng thái đơn <strong>" + escape(ctx.orderCode) + "</strong> vừa được thay đổi:</p>" +
                "<div style=\"display:inline-block;padding:8px 14px;background:#eff6ff;border:1px solid #bfdbfe;border-radius:6px;font-weight:600;color:#1e3a8a;\">" +
                escape(statusLabel(newStatus)) +
                (oldStatus != null ? " <span style=\"color:#94a3b8;font-weight:400;\">(trước đó: " + escape(statusLabel(oldStatus)) + ")</span>" : "") +
                "</div>" +
                renderOrderHeaderRows(ctx) +
                "</table>" +
                renderViewOrderButton()
        );
        sendHtmlMail(ctx.recipientEmail, subject, body);
    }

    @Async("notificationTaskExecutor")
    public void sendPaymentReminderEmail(OrderEmailContext ctx, LocalDateTime expireAt) {
        String subject = "[TechParts] Nhắc thanh toán đơn " + ctx.orderCode;
        String expireText = expireAt != null ? expireAt.format(DATE_FMT) : "trong ít phút nữa";
        String body = wrapHtml(
                "<h2 style=\"color:#b45309;\">Đơn hàng đang chờ thanh toán</h2>" +
                "<p>Đơn <strong>" + escape(ctx.orderCode) + "</strong> sẽ <strong>tự động hủy lúc " + escape(expireText) + "</strong> nếu bạn chưa hoàn tất thanh toán.</p>" +
                renderOrderHeaderRows(ctx) +
                "</table>" +
                renderItemsTable(ctx) +
                renderTotalsBox(ctx) +
                "<p>Vui lòng vào <em>Đơn hàng của tôi</em> và bấm <strong>Thanh toán với SePay</strong> để hoàn tất.</p>" +
                renderViewOrderButton()
        );
        sendHtmlMail(ctx.recipientEmail, subject, body);
    }

    @Async("notificationTaskExecutor")
    public void sendShippingNotificationEmail(OrderEmailContext ctx) {
        String subject = "[TechParts] Đơn " + ctx.orderCode + " đã giao đơn vị vận chuyển";
        String trackingHtml = (ctx.trackingNumber != null && !ctx.trackingNumber.isBlank())
                ? "<tr><td style=\"padding:4px 8px;color:#64748b;\">Mã vận đơn:</td><td style=\"padding:4px 8px;\"><strong>" + escape(ctx.trackingNumber) + "</strong></td></tr>"
                : "";
        String body = wrapHtml(
                "<h2 style=\"color:#7c3aed;\">Đơn hàng đang được giao</h2>" +
                "<p>Đơn <strong>" + escape(ctx.orderCode) + "</strong> đã được bàn giao cho đơn vị vận chuyển và đang trên đường đến địa chỉ của bạn.</p>" +
                renderOrderHeaderRows(ctx) +
                trackingHtml +
                "</table>" +
                renderItemsTable(ctx) +
                renderRecipientBox(ctx) +
                "<p>Khi nhận được hàng, vui lòng vào <em>Đơn hàng của tôi</em> và bấm <strong>Đã nhận hàng</strong> để xác nhận.</p>" +
                renderViewOrderButton()
        );
        sendHtmlMail(ctx.recipientEmail, subject, body);
    }

    @Async("notificationTaskExecutor")
    public void sendSepayPaymentConfirmedEmail(OrderEmailContext ctx) {
        String subject = "[TechParts] Đã ghi nhận thanh toán SePay - đơn " + ctx.orderCode;
        String body = wrapHtml(
                "<h2 style=\"color:#047857;\">Thanh toán thành công</h2>" +
                "<p>Hệ thống đã ghi nhận giao dịch chuyển khoản SePay cho đơn <strong>" + escape(ctx.orderCode) + "</strong>.</p>" +
                renderOrderHeaderRows(ctx) +
                "<tr><td style=\"padding:4px 8px;color:#64748b;\">Số tiền thanh toán:</td><td style=\"padding:4px 8px;\"><strong style=\"color:#047857;\">" + formatVnd(ctx.total) + "</strong></td></tr>" +
                "<tr><td style=\"padding:4px 8px;color:#64748b;\">Thời điểm xác nhận:</td><td style=\"padding:4px 8px;\">" + LocalDateTime.now().format(DATE_FMT) + "</td></tr>" +
                "</table>" +
                renderItemsTable(ctx) +
                "<p>Đơn hàng đang chuyển sang trạng thái <strong>Chờ xác nhận</strong>. Nhân viên sẽ xử lý trong thời gian sớm nhất.</p>" +
                renderViewOrderButton()
        );
        sendHtmlMail(ctx.recipientEmail, subject, body);
    }

    @Async("notificationTaskExecutor")
    public void sendRefundRejectedEmail(OrderEmailContext ctx, String rejectNote) {
        String subject = "[TechParts] Yêu cầu hoàn tiền đơn " + ctx.orderCode + " bị từ chối";
        String body = wrapHtml(
                "<h2 style=\"color:#b91c1c;\">Yêu cầu hoàn tiền bị từ chối</h2>" +
                "<p>Yêu cầu hoàn tiền cho đơn <strong>" + escape(ctx.orderCode) + "</strong> đã bị từ chối.</p>" +
                renderOrderHeaderRows(ctx) +
                "</table>" +
                "<div style=\"margin-top:14px;padding:12px;background:#fef2f2;border-left:3px solid #dc2626;border-radius:4px;\">" +
                "<div style=\"color:#7f1d1d;font-size:13px;margin-bottom:4px;\">Lý do từ chối:</div>" +
                "<div style=\"color:#991b1b;\">" + escape(rejectNote) + "</div>" +
                "</div>" +
                "<p style=\"margin-top:14px;\">Nếu chưa đồng ý với quyết định này, vui lòng liên hệ CSKH (" + escape(supportHotline) + ") để được hỗ trợ.</p>" +
                renderViewOrderButton()
        );
        sendHtmlMail(ctx.recipientEmail, subject, body);
    }

    @Async("notificationTaskExecutor")
    public void sendRefundCompletedEmail(OrderEmailContext ctx, String note) {
        String subject = "[TechParts] Đơn " + ctx.orderCode + " đã được hoàn tiền";
        String noteBlock = (note == null || note.isBlank())
                ? ""
                : "<div style=\"margin-top:14px;padding:12px;background:#ecfdf5;border-left:3px solid #059669;border-radius:4px;\">"
                + "<div style=\"color:#065f46;font-size:13px;margin-bottom:4px;\">Ghi chú từ nhân viên:</div>"
                + "<div style=\"color:#047857;\">" + escape(note) + "</div>"
                + "</div>";
        String body = wrapHtml(
                "<h2 style=\"color:#059669;\">Đã hoàn tiền thành công</h2>" +
                "<p>CSKH đã chuyển khoản hoàn tiền cho đơn <strong>" + escape(ctx.orderCode) + "</strong>.</p>" +
                renderOrderHeaderRows(ctx) +
                "<tr><td style=\"padding:4px 8px;color:#64748b;\">Số tiền hoàn:</td><td style=\"padding:4px 8px;\"><strong style=\"color:#059669;\">" + formatVnd(ctx.total) + "</strong></td></tr>" +
                "<tr><td style=\"padding:4px 8px;color:#64748b;\">Thời điểm hoàn:</td><td style=\"padding:4px 8px;\">" + LocalDateTime.now().format(DATE_FMT) + "</td></tr>" +
                "</table>" +
                noteBlock +
                "<p style=\"margin-top:14px;\">Vui lòng kiểm tra số dư tài khoản ngân hàng. Nếu sau 24h chưa nhận được, hãy liên hệ CSKH (" + escape(supportHotline) + ") để được hỗ trợ.</p>"
        );
        sendHtmlMail(ctx.recipientEmail, subject, body);
    }

    @Async("notificationTaskExecutor")
    public void sendReturnRefundFormEmail(OrderEmailContext ctx) {
        String subject = "[TechParts] Hướng dẫn hoàn tiền đơn " + ctx.orderCode;
        String ordersUrl = ordersUrl();
        String body = wrapHtml(
                "<h2 style=\"color:#0d9488;\">Yêu cầu hoàn tiền đã được duyệt</h2>" +
                "<p>Yêu cầu hoàn tiền cho đơn <strong>" + escape(ctx.orderCode) + "</strong> đã được chấp nhận.</p>" +
                renderOrderHeaderRows(ctx) +
                "<tr><td style=\"padding:4px 8px;color:#64748b;\">Số tiền sẽ hoàn:</td><td style=\"padding:4px 8px;\"><strong style=\"color:#0d9488;\">" + formatVnd(ctx.total) + "</strong></td></tr>" +
                "</table>" +
                "<p style=\"margin-top:14px;\">Để CSKH chuyển khoản, vui lòng cung cấp <strong>thông tin tài khoản ngân hàng</strong> ngay trên website:</p>" +
                "<ol style=\"padding-left:18px;color:#334155;\">" +
                "<li>Đăng nhập tài khoản TechParts</li>" +
                "<li>Vào <strong>Đơn hàng của tôi</strong> → tìm đơn <strong>" + escape(ctx.orderCode) + "</strong></li>" +
                "<li>Bấm nút <strong>Điền thông tin tài khoản</strong> trong khung xanh và nhập tên ngân hàng / số TK / chủ TK</li>" +
                "</ol>" +
                "<p><a href=\"" + escape(ordersUrl) + "\" style=\"display:inline-block;padding:10px 20px;background:#0d9488;color:#fff;border-radius:6px;text-decoration:none;font-weight:600;\">Mở Đơn hàng của tôi</a></p>" +
                "<p style=\"color:#64748b;font-size:13px;\">Sau khi bạn gửi thông tin, CSKH sẽ chuyển khoản trong thời gian sớm nhất. Mọi thắc mắc xin gọi " + escape(supportHotline) + ".</p>"
        );
        sendHtmlMail(ctx.recipientEmail, subject, body);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  HTML BUILDERS
    // ════════════════════════════════════════════════════════════════════════

    /** Mở thẻ table + một số dòng metadata cơ bản. Caller phải đóng </table> sau khi append rows. */
    private String renderOrderHeaderRows(OrderEmailContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table style=\"border-collapse:collapse;font-size:14px;margin-top:8px;\">")
          .append("<tr><td style=\"padding:4px 8px;color:#64748b;\">Mã đơn:</td><td style=\"padding:4px 8px;\"><strong>")
          .append(escape(ctx.orderCode)).append("</strong></td></tr>");
        if (ctx.createdAt != null) {
            sb.append("<tr><td style=\"padding:4px 8px;color:#64748b;\">Ngày đặt:</td><td style=\"padding:4px 8px;\">")
              .append(escape(ctx.createdAt.format(DATE_FMT))).append("</td></tr>");
        }
        return sb.toString();
    }

    private String renderItemsTable(OrderEmailContext ctx) {
        if (ctx.items == null || ctx.items.isEmpty()) {
            return "";
        }
        StringBuilder rows = new StringBuilder();
        for (OrderEmailContext.Item item : ctx.items) {
            rows.append("<tr>")
                .append("<td style=\"padding:8px 10px;border-bottom:1px solid #e2e8f0;\">").append(escape(item.name)).append("</td>")
                .append("<td style=\"padding:8px 10px;border-bottom:1px solid #e2e8f0;text-align:center;color:#64748b;\">x").append(item.quantity).append("</td>")
                .append("<td style=\"padding:8px 10px;border-bottom:1px solid #e2e8f0;text-align:right;color:#64748b;\">").append(formatVnd(item.unitPrice)).append("</td>")
                .append("<td style=\"padding:8px 10px;border-bottom:1px solid #e2e8f0;text-align:right;font-weight:600;\">").append(formatVnd(item.lineTotal)).append("</td>")
                .append("</tr>");
        }
        return "<div style=\"margin-top:18px;\"><div style=\"font-weight:600;color:#0f172a;margin-bottom:6px;\">Sản phẩm đã đặt</div>" +
               "<table style=\"width:100%;border-collapse:collapse;font-size:14px;border:1px solid #e2e8f0;border-radius:6px;overflow:hidden;\">" +
               "<thead><tr style=\"background:#f8fafc;\">" +
               "<th style=\"padding:8px 10px;text-align:left;font-size:12px;color:#64748b;text-transform:uppercase;\">Sản phẩm</th>" +
               "<th style=\"padding:8px 10px;text-align:center;font-size:12px;color:#64748b;text-transform:uppercase;\">SL</th>" +
               "<th style=\"padding:8px 10px;text-align:right;font-size:12px;color:#64748b;text-transform:uppercase;\">Đơn giá</th>" +
               "<th style=\"padding:8px 10px;text-align:right;font-size:12px;color:#64748b;text-transform:uppercase;\">Thành tiền</th>" +
               "</tr></thead><tbody>" + rows + "</tbody></table></div>";
    }

    private String renderTotalsBox(OrderEmailContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"margin-top:14px;padding:12px 16px;background:#f8fafc;border-radius:6px;font-size:14px;\">");
        if (ctx.subtotal != null) {
            sb.append("<div style=\"display:flex;justify-content:space-between;padding:3px 0;\"><span style=\"color:#64748b;\">Tạm tính</span><span>").append(formatVnd(ctx.subtotal)).append("</span></div>");
        }
        if (ctx.discount != null && ctx.discount.signum() > 0) {
            sb.append("<div style=\"display:flex;justify-content:space-between;padding:3px 0;color:#059669;\"><span>Giảm giá</span><span>−").append(formatVnd(ctx.discount)).append("</span></div>");
        }
        if (ctx.shippingFee != null && ctx.shippingFee.signum() > 0) {
            sb.append("<div style=\"display:flex;justify-content:space-between;padding:3px 0;\"><span style=\"color:#64748b;\">Phí vận chuyển</span><span>").append(formatVnd(ctx.shippingFee)).append("</span></div>");
        }
        sb.append("<div style=\"display:flex;justify-content:space-between;padding:8px 0 3px;border-top:1px solid #e2e8f0;margin-top:6px;\">")
          .append("<span style=\"font-weight:600;\">Tổng thanh toán</span>")
          .append("<span style=\"font-weight:700;color:#0f766e;font-size:16px;\">").append(formatVnd(ctx.total)).append("</span>")
          .append("</div></div>");
        return sb.toString();
    }

    private String renderRecipientBox(OrderEmailContext ctx) {
        boolean hasAny = (ctx.recipientName != null && !ctx.recipientName.isBlank())
                || (ctx.recipientPhone != null && !ctx.recipientPhone.isBlank())
                || (ctx.shippingAddress != null && !ctx.shippingAddress.isBlank());
        if (!hasAny) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"margin-top:14px;padding:12px 16px;background:#fff7ed;border:1px solid #fed7aa;border-radius:6px;font-size:14px;\">")
          .append("<div style=\"font-weight:600;color:#9a3412;margin-bottom:6px;\">📦 Địa chỉ giao hàng</div>");
        if (ctx.recipientName != null && !ctx.recipientName.isBlank()) {
            sb.append("<div><strong>").append(escape(ctx.recipientName)).append("</strong>");
            if (ctx.recipientPhone != null && !ctx.recipientPhone.isBlank()) {
                sb.append(" • ").append(escape(ctx.recipientPhone));
            }
            sb.append("</div>");
        }
        if (ctx.shippingAddress != null && !ctx.shippingAddress.isBlank()) {
            sb.append("<div style=\"color:#475569;\">").append(escape(ctx.shippingAddress)).append("</div>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private String renderViewOrderButton() {
        return "<div style=\"margin-top:18px;text-align:center;\">" +
               "<a href=\"" + escape(ordersUrl()) + "\" style=\"display:inline-block;padding:10px 24px;background:#1d4ed8;color:#fff;border-radius:6px;text-decoration:none;font-weight:600;\">" +
               "Xem chi tiết đơn hàng" +
               "</a></div>";
    }

    private String ordersUrl() {
        return webBaseUrl.replaceAll("/+$", "") + "/orders/my";
    }

    // ════════════════════════════════════════════════════════════════════════
    //  LOW LEVEL
    // ════════════════════════════════════════════════════════════════════════

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
                + "<div style=\"max-width:640px;margin:0 auto;background:#ffffff;padding:24px;border-radius:8px;border:1px solid #e2e8f0;\">"
                + inner
                + "<hr style=\"margin:24px 0;border:none;border-top:1px solid #e2e8f0;\"/>"
                + "<p style=\"color:#94a3b8;font-size:12px;margin:0;\">Email tự động từ TechParts. Vui lòng không phản hồi trực tiếp tới địa chỉ này. Cần hỗ trợ? Gọi " + escape(supportHotline) + ".</p>"
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
            case RETURN_REFUND -> "Đã duyệt hoàn tiền";
            case REFUND_COMPLETED -> "Đã hoàn tiền";
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
