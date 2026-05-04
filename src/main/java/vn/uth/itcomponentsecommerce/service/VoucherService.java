package vn.uth.itcomponentsecommerce.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.uth.itcomponentsecommerce.entity.Voucher;
import vn.uth.itcomponentsecommerce.repository.VoucherRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class VoucherService {

    private final VoucherRepository voucherRepository;

    public VoucherService(VoucherRepository voucherRepository) {
        this.voucherRepository = voucherRepository;
    }

    public Optional<Voucher> findByCodeNormalized(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return Optional.empty();
        }
        return voucherRepository.findByCode(rawCode.trim().toUpperCase());
    }

    /**
     * Tính số tiền giảm (không âm, không vượt subtotal).
     */
    public BigDecimal computeDiscount(Voucher v, BigDecimal subtotal) {
        if (v == null || subtotal == null || subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal discount;
        if (v.getDiscountType() == Voucher.DiscountType.PERCENT) {
            discount = subtotal.multiply(v.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else {
            discount = v.getDiscountValue();
        }
        if (v.getMaxDiscount() != null && discount.compareTo(v.getMaxDiscount()) > 0) {
            discount = v.getMaxDiscount();
        }
        if (discount.compareTo(subtotal) > 0) {
            discount = subtotal;
        }
        if (discount.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return discount.setScale(2, RoundingMode.HALF_UP);
    }

    public void assertVoucherApplicable(Voucher v, BigDecimal subtotal) {
        if (v == null) {
            throw new IllegalArgumentException("Voucher không tồn tại");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!v.isActive()) {
            throw new IllegalArgumentException("Voucher không còn hiệu lực");
        }
        if (v.getValidFrom() != null && now.isBefore(v.getValidFrom())) {
            throw new IllegalArgumentException("Voucher chưa đến thời gian áp dụng");
        }
        if (v.getValidTo() != null && now.isAfter(v.getValidTo())) {
            throw new IllegalArgumentException("Voucher đã hết hạn");
        }
        if (v.getMinOrder() != null && subtotal.compareTo(v.getMinOrder()) < 0) {
            throw new IllegalArgumentException("Đơn hàng chưa đạt giá trị tối thiểu để dùng voucher");
        }
        if (v.getUsageLimit() != null && v.getUsedCount() != null && v.getUsedCount() >= v.getUsageLimit()) {
            throw new IllegalArgumentException("Voucher đã hết lượt sử dụng");
        }
    }

    @Transactional
    public void incrementUsageIfPresent(Voucher voucher) {
        if (voucher == null || voucher.getId() == null) {
            return;
        }
        // Atomic update để tránh race condition khi nhiều request cùng tăng usedCount
        int updated = voucherRepository.incrementUsedCountAtomic(voucher.getId());
        if (updated == 0) {
            throw new IllegalStateException("Voucher đã hết lượt sử dụng");
        }
    }

    @Transactional
    public void rollbackUsageIfPresent(Voucher voucher) {
        // Rollback voucher usage when order is cancelled (ARCHITECTURE §8.5)
        if (voucher == null || voucher.getId() == null) {
            return;
        }
        voucherRepository.decrementUsedCountAtomic(voucher.getId());
    }
}
