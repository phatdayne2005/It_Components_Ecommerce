package vn.uth.itcomponentsecommerce.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
public class CronjobService {

    private static final Logger log = LoggerFactory.getLogger(CronjobService.class);

    private final OrderService orderService;
    private final SepayPollingService sepayPollingService;

    public CronjobService(OrderService orderService, SepayPollingService sepayPollingService) {
        this.orderService = orderService;
        this.sepayPollingService = sepayPollingService;
    }

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void cancelExpiredPendingPaymentOrders() {
        int affected = orderService.autoCancelExpiredPendingPaymentOrders();
        if (affected > 0) {
            log.info("Auto cancelled {} pending-payment orders and released reserved stock", affected);
        }
    }

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void sendPaymentReminderForExpiringOrders() {
        int reminded = orderService.sendPaymentReminderForExpiringOrders();
        if (reminded > 0) {
            log.info("Sent payment reminder for {} pending-payment orders close to expiry", reminded);
        }
    }

    /**
     * Polling SePay khi không có domain public để nhận webhook.
     * Tự bỏ qua nếu {@code app.sepay.polling.enabled=false} hoặc thiếu API token.
     * Interval cố định {@code app.sepay.polling.interval-seconds} (default 30s).
     */
    @Scheduled(fixedDelayString = "${app.sepay.polling.interval-seconds:30}",
               initialDelayString = "${app.sepay.polling.initial-delay-seconds:10}",
               timeUnit = TimeUnit.SECONDS)
    public void pollSepayTransactions() {
        if (!sepayPollingService.isEnabled()) {
            return;
        }
        sepayPollingService.pollAndProcessNewTransactions();
    }
}
