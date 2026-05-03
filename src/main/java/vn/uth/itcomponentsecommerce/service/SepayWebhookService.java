package vn.uth.itcomponentsecommerce.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.uth.itcomponentsecommerce.entity.SepayTransaction;
import vn.uth.itcomponentsecommerce.repository.SepayTransactionRepository;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class SepayWebhookService {

    private final SepayTransactionRepository sepayTransactionRepository;
    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    public SepayWebhookService(SepayTransactionRepository sepayTransactionRepository,
                               OrderService orderService,
                               ObjectMapper objectMapper) {
        this.sepayTransactionRepository = sepayTransactionRepository;
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String processGatewayIpn(Map<String, Object> payload) {
        String notificationType = String.valueOf(payload.getOrDefault("notification_type", ""));
        if (!"ORDER_PAID".equals(notificationType)) {
            return "ignored_notification_type";
        }
        Map<String, Object> order = castMap(payload.get("order"));
        Map<String, Object> transaction = castMap(payload.get("transaction"));
        String orderStatus = stringValue(order.get("order_status"));
        String transactionStatus = stringValue(transaction.get("transaction_status"));
        if (!"CAPTURED".equalsIgnoreCase(orderStatus)) {
            return "ignored_order_not_captured";
        }
        if (!"APPROVED".equalsIgnoreCase(transactionStatus)) {
            return "ignored_transaction_not_approved";
        }
        String invoiceNumber = stringValue(order.get("order_invoice_number"));
        String transactionId = stringValue(transaction.get("transaction_id"));
        BigDecimal amount = decimalValue(transaction.get("transaction_amount"));

        if (invoiceNumber.isBlank() || transactionId.isBlank()) {
            throw new IllegalArgumentException("Missing invoice_number or transaction_id in IPN");
        }

        SepayTransaction tx = new SepayTransaction();
        tx.setTransactionId(transactionId);
        tx.setOrderCode(invoiceNumber);
        tx.setTransferAmount(amount);
        tx.setRawPayload(writeRawPayload(payload));
        sepayTransactionRepository.saveAndFlush(tx);

        OrderService.SepayOrderProcessResult result = orderService.markSepayPaidAndMoveToPendingConfirmation(
                invoiceNumber,
                transactionId,
                amount,
                tx.getRawPayload()
        );
        if (result == OrderService.SepayOrderProcessResult.IGNORED_STATUS) {
            return "ignored_order_status";
        }
        if (result == OrderService.SepayOrderProcessResult.IGNORED_AMOUNT) {
            return "ignored_insufficient_amount";
        }
        return "ok";
    }

    private String writeRawPayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private BigDecimal decimalValue(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }
}
