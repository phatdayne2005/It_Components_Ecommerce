package vn.uth.itcomponentsecommerce.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.uth.itcomponentsecommerce.entity.Order;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SepayGatewayCheckoutService {

    @Value("${app.sepay.merchant-id:}")
    private String sepayMerchantId;
    @Value("${app.sepay.secret-key:}")
    private String sepaySecretKey;
    @Value("${app.sepay.checkout-url:https://pay-sandbox.sepay.vn/v1/checkout/init}")
    private String sepayCheckoutUrl;
    @Value("${app.sepay.callback-base-url:http://localhost:8080}")
    private String sepayCallbackBaseUrl;

    public void enrichCheckoutData(Order order) {
        String transferContent = "Thanh toan " + order.getOrderCode();
        order.setSepayTransferContent(transferContent);
        order.setSepayCheckoutActionUrl(blankToNull(sepayCheckoutUrl));
        order.setSepayCheckoutFields(buildCheckoutFields(order));
    }

    /** True nếu đã cấu hình đủ merchant-id + secret-key để render gateway form. */
    public boolean isGatewayConfigured() {
        return blankToNull(sepayMerchantId) != null && blankToNull(sepaySecretKey) != null;
    }

    private Map<String, String> buildCheckoutFields(Order order) {
        if (blankToNull(sepayMerchantId) == null || blankToNull(sepaySecretKey) == null) {
            return Map.of();
        }
        String callbackBase = blankToEmpty(sepayCallbackBaseUrl).replaceAll("/+$", "");
        String invoice = order.getOrderCode();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("merchant", sepayMerchantId);
        fields.put("operation", "PURCHASE");
        fields.put("payment_method", "BANK_TRANSFER");
        fields.put("order_amount", order.getTotal().toBigInteger().toString());
        fields.put("currency", "VND");
        fields.put("order_invoice_number", invoice);
        fields.put("order_description", "Thanh toan don hang " + invoice);
        fields.put("success_url", callbackBase + "/payment/success?orderCode=" + invoice);
        fields.put("error_url", callbackBase + "/payment/error?orderCode=" + invoice);
        fields.put("cancel_url", callbackBase + "/payment/cancel?orderCode=" + invoice);
        fields.put("signature", signSepayFields(fields));
        return fields;
    }

    private String signSepayFields(Map<String, String> fields) {
        String[] signedFieldOrder = {
                "merchant", "operation", "payment_method", "order_amount", "currency",
                "order_invoice_number", "order_description", "customer_id",
                "success_url", "error_url", "cancel_url"
        };
        List<String> signedPairs = new ArrayList<>();
        for (String key : signedFieldOrder) {
            if (fields.containsKey(key)) {
                signedPairs.add(key + "=" + fields.get(key));
            }
        }
        String payload = String.join(",", signedPairs);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(blankToEmpty(sepaySecretKey).getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot sign SePay checkout fields");
        }
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToNull(String value) {
        String normalized = blankToEmpty(value);
        return normalized.isEmpty() ? null : normalized;
    }
}
