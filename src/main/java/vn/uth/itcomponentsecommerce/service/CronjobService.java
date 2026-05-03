package vn.uth.itcomponentsecommerce.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CronjobService {

    private static final Logger log = LoggerFactory.getLogger(CronjobService.class);

    private final OrderService orderService;

    public CronjobService(OrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void cancelExpiredPendingPaymentOrders() {
        int affected = orderService.autoCancelExpiredPendingPaymentOrders();
        if (affected > 0) {
            log.info("Auto cancelled {} pending-payment orders and released reserved stock", affected);
        }
    }
}
