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
import vn.uth.itcomponentsecommerce.entity.Order;
import vn.uth.itcomponentsecommerce.entity.SepayTransaction;
import vn.uth.itcomponentsecommerce.repository.OrderRepository;
import vn.uth.itcomponentsecommerce.repository.SepayTransactionRepository;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
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
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    /** Cửa sổ thời gian fuzzy-match: 5 phút (đơn vừa đặt + chuyển khoản ngay). */
    private static final int FUZZY_MATCH_WINDOW_MINUTES = 5;

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
                               OrderRepository orderRepository,
                               OrderService orderService,
                               ObjectMapper objectMapper) {
        this.sepayTransactionRepository = sepayTransactionRepository;
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
    }

    @jakarta.annotation.PostConstruct
    private void logPollingConfig() {
        boolean tokenSet = apiToken != null && !apiToken.isBlank();
        log.info("=== SEPAY POLLING CONFIG ===");
        log.info("  app.sepay.polling.enabled = {}", pollingEnabled);
        log.info("  api-token = {}", tokenSet ? "(set, " + apiToken.length() + " chars)" : "(NOT SET)");
        log.info("  api-base-url = {}", apiBaseUrl);
        log.info("  interval-seconds = {} (cron mode)", "30 (default)");
        if (pollingEnabled && !tokenSet) {
            log.warn("  -> Polling enabled NHUNG SEPAY_API_TOKEN trong .env trong. Cron khong chay.");
            log.warn("  -> Tao token o my.sepay.vn -> Cau hinh cong ty -> Truy cap API.");
        } else if (!pollingEnabled && tokenSet) {
            log.info("  -> On-demand polling (khi user vao /payment/success) van hoat dong.");
            log.info("  -> Cron polling tat. Set SEPAY_POLLING_ENABLED=true de bat cron 30s/lan.");
        } else if (pollingEnabled && tokenSet) {
            log.info("  -> Cron polling SE chay moi 30s + on-demand polling cung hoat dong.");
        } else {
            log.warn("  -> Polling tat hoan toan. Don thanh toan SePay se ket o PENDING_PAYMENT,");
            log.warn("  -> staff phai xac nhan thu cong qua /staff page.");
        }
        log.info("============================");
    }

    /** Cron polling — chỉ chạy khi có cả flag enabled + token */
    public boolean isEnabled() {
        return pollingEnabled && apiToken != null && !apiToken.isBlank();
    }

    /** On-demand polling (success page, manual retry) — chỉ cần có API token */
    public boolean canPollOnDemand() {
        return apiToken != null && !apiToken.isBlank();
    }

    /** Diagnostic snapshot — không expose giá trị token, chỉ length + flags. */
    public java.util.Map<String, Object> healthSnapshot() {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("apiTokenSet", apiToken != null && !apiToken.isBlank());
        m.put("apiTokenLength", apiToken == null ? 0 : apiToken.length());
        m.put("apiBaseUrl", apiBaseUrl);
        m.put("accountNumberFilter", accountNumber == null || accountNumber.isBlank() ? "(empty - all accounts)" : "set");
        m.put("cronPollingEnabled", pollingEnabled);
        m.put("canPollOnDemand", canPollOnDemand());
        return m;
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
            // SePay gateway không đẩy order_invoice_number vào content (cần config "Tiền tố mã đơn"
            // trên dashboard hoặc dùng webhook IPN). Fallback: fuzzy-match theo amount + time window.
            orderCode = fuzzyMatchByAmount(amountIn, transactionId);
            if (orderCode == null) {
                return false;
            }
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

    /**
     * Fallback khi SePay không đẩy order_invoice_number vào transaction_content:
     * tìm đơn PENDING_PAYMENT (SePay) cùng số tiền và còn trong cửa sổ {@value FUZZY_MATCH_WINDOW_MINUTES} phút.
     * Nếu nhiều candidate → match đơn cũ nhất (đặt trước thường thanh toán trước).
     */
    private String fuzzyMatchByAmount(BigDecimal amountIn, String transactionId) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(FUZZY_MATCH_WINDOW_MINUTES);
        List<Order> candidates = orderRepository.findPendingSepayCandidatesByAmount(amountIn, since);
        if (candidates.isEmpty()) {
            log.debug("SePay tx {} không có order code và không có đơn PENDING_PAYMENT cùng amount={} trong {}p, bỏ qua.",
                    transactionId, amountIn, FUZZY_MATCH_WINDOW_MINUTES);
            return null;
        }
        Order matched = candidates.get(0); // query đã ORDER BY createdAt ASC → đơn cũ nhất
        if (candidates.size() > 1) {
            String codes = candidates.stream().map(Order::getOrderCode).reduce((a, b) -> a + "," + b).orElse("");
            log.warn("SePay tx {} fuzzy-match amount={} có {} candidates ({}) — chọn đơn cũ nhất {} (created {}).",
                    transactionId, amountIn, candidates.size(), codes, matched.getOrderCode(), matched.getCreatedAt());
        } else {
            log.info("SePay tx {} fuzzy-matched theo amount={} với đơn {} (created {})",
                    transactionId, amountIn, matched.getOrderCode(), matched.getCreatedAt());
        }
        return matched.getOrderCode();
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
