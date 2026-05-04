package vn.uth.itcomponentsecommerce.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import vn.uth.itcomponentsecommerce.entity.SepayTransaction;
import vn.uth.itcomponentsecommerce.repository.SepayTransactionRepository;

import java.math.BigDecimal;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Polling SePay transactions API thay cho IPN webhook.
 *
 * Dùng khi chạy ở localhost / không có public domain để nhận webhook callback.
 * Cronjob trong CronjobService gọi {@link #pollAndProcessNewTransactions()} định kỳ.
 *
 * SePay API: GET https://my.sepay.vn/userapi/transactions/list
 *  - Authorization: Bearer {API_TOKEN}
 *  - Rate limit: 3 req/s (chỉ poll mỗi vài chục giây nên rất an toàn)
 */
@Service
public class SepayPollingService {

    private static final Logger log = LoggerFactory.getLogger(SepayPollingService.class);
    // Order code dạng "ORD-yyyyMMddHHmmss-XXXXXX" — match trong content/code field
    private static final Pattern ORDER_CODE_PATTERN = Pattern.compile("ORD-\\d{14}-[A-Z0-9]{6}");

    private final SepayTransactionRepository sepayTransactionRepository;
    private final OrderService orderService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    @Value("${app.sepay.polling.enabled:false}")
    private boolean pollingEnabled;

    @Value("${app.sepay.api-base-url:https://my.sepay.vn}")
    private String apiBaseUrl;

    @Value("${app.sepay.api-token:}")
    private String apiToken;

    @Value("${app.sepay.account-number:}")
    private String accountNumber;

    @Value("${app.sepay.polling.page-size:50}")
    private int pageSize;

    public SepayPollingService(SepayTransactionRepository sepayTransactionRepository,
                               OrderService orderService,
                               ObjectMapper objectMapper) {
        this.sepayTransactionRepository = sepayTransactionRepository;
        this.orderService = orderService;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
    }

    /** Cron polling — chỉ chạy khi có cả flag enabled + token */
    public boolean isEnabled() {
        return pollingEnabled && apiToken != null && !apiToken.isBlank();
    }

    /** On-demand polling (success page, manual retry) — chỉ cần có API token */
    public boolean canPollOnDemand() {
        return apiToken != null && !apiToken.isBlank();
    }

    /**
     * Poll danh sách giao dịch mới từ SePay và xử lý từng giao dịch.
     * @return số giao dịch đã xử lý thành công ở lần poll này
     */
    public int pollAndProcessNewTransactions() {
        if (!canPollOnDemand()) {
            return 0;
        }
        JsonNode transactions;
        try {
            transactions = fetchLatestTransactions();
        } catch (Exception ex) {
            log.warn("SePay polling failed: {}", ex.getMessage());
            return 0;
        }
        if (transactions == null || !transactions.isArray() || transactions.isEmpty()) {
            return 0;
        }

        int processed = 0;
        // Iterate từ cũ đến mới: SePay trả mới nhất trước, đảo lại để giữ thứ tự xử lý
        for (int i = transactions.size() - 1; i >= 0; i--) {
            JsonNode tx = transactions.get(i);
            try {
                if (processTransaction(tx)) {
                    processed++;
                }
            } catch (Exception ex) {
                log.error("Failed to process SePay transaction id={}: {}",
                        tx.path("id").asText("?"), ex.getMessage(), ex);
            }
        }
        if (processed > 0) {
            log.info("SePay polling: processed {} new transactions", processed);
        }
        return processed;
    }

    private JsonNode fetchLatestTransactions() {
        URI uri = UriComponentsBuilder.fromUriString(apiBaseUrl)
                .path("/userapi/transactions/list")
                .queryParam("limit", Math.max(1, Math.min(pageSize, 200)))
                .queryParamIfPresent("account_number",
                        accountNumber != null && !accountNumber.isBlank()
                                ? java.util.Optional.of(accountNumber)
                                : java.util.Optional.empty())
                .build()
                .toUri();

        String body = restClient.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .body(String.class);
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            return root.get("transactions");
        } catch (JsonProcessingException ex) {
            log.warn("Failed to parse SePay response: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * Xử lý 1 giao dịch: nếu chưa từng lưu trong DB và content/code có chứa orderCode, mark order paid.
     * @return true nếu đã trigger {@code markSepayPaidAndMoveToPendingConfirmation} cho 1 đơn hàng mới.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processTransaction(JsonNode tx) {
        String transactionId = textOrEmpty(tx.get("id"));
        if (transactionId.isBlank()) {
            return false;
        }
        // Idempotent: nếu đã xử lý transaction này thì bỏ qua
        if (sepayTransactionRepository.existsByTransactionId(transactionId)) {
            return false;
        }

        BigDecimal amountIn = decimalOrZero(tx.get("amount_in"));
        if (amountIn.signum() <= 0) {
            return false; // chỉ quan tâm tiền vào
        }

        String code = textOrEmpty(tx.get("code"));
        String content = textOrEmpty(tx.get("transaction_content"));
        String orderCode = extractOrderCode(code, content);
        if (orderCode == null) {
            log.debug("SePay tx {} không chứa order code, bỏ qua. content='{}'", transactionId, content);
            return false;
        }

        // Lưu raw payload trước (idempotent guard)
        SepayTransaction record = new SepayTransaction();
        record.setTransactionId(transactionId);
        record.setOrderCode(orderCode);
        record.setTransferAmount(amountIn);
        record.setRawPayload(safeWriteJson(tx));
        sepayTransactionRepository.saveAndFlush(record);

        OrderService.SepayOrderProcessResult result = orderService.markSepayPaidAndMoveToPendingConfirmation(
                orderCode, transactionId, amountIn, record.getRawPayload());
        switch (result) {
            case MOVED_TO_PENDING_CONFIRMATION -> log.info(
                    "SePay polling: order {} đã được xác nhận thanh toán (txId={}, amount={})",
                    orderCode, transactionId, amountIn);
            case IGNORED_STATUS -> log.info(
                    "SePay polling: tx {} cho order {} bị bỏ qua do trạng thái không phù hợp",
                    transactionId, orderCode);
            case IGNORED_AMOUNT -> log.warn(
                    "SePay polling: tx {} cho order {} thanh toán thiếu (amount={})",
                    transactionId, orderCode, amountIn);
        }
        return result == OrderService.SepayOrderProcessResult.MOVED_TO_PENDING_CONFIRMATION;
    }

    private String extractOrderCode(String code, String content) {
        // 1. SePay tự extract sẵn vào field "code" nếu config "Tiền tố mã đơn hàng" trong dashboard
        if (code != null && !code.isBlank()) {
            Matcher m = ORDER_CODE_PATTERN.matcher(code.toUpperCase());
            if (m.find()) return m.group();
            // hoặc code là full orderCode nguyên xi
            if (code.startsWith("ORD-")) return code.toUpperCase();
        }
        // 2. Fallback: scan transaction_content tìm pattern ORD-XXXX
        if (content != null && !content.isBlank()) {
            Matcher m = ORDER_CODE_PATTERN.matcher(content.toUpperCase());
            if (m.find()) return m.group();
        }
        return null;
    }

    private String textOrEmpty(JsonNode node) {
        return node == null || node.isNull() ? "" : node.asText("");
    }

    private BigDecimal decimalOrZero(JsonNode node) {
        if (node == null || node.isNull()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(node.asText("0"));
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private String safeWriteJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}
