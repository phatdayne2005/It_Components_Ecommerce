package vn.uth.itcomponentsecommerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.uth.itcomponentsecommerce.entity.Voucher;

import java.util.Optional;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {
    Optional<Voucher> findByCode(String code);
    boolean existsByCode(String code);

    /**
     * Atomic increment used count, chỉ thành công nếu vẫn còn quota (NULL usageLimit = không giới hạn).
     * Trả về số dòng update được; 0 = đã hết lượt sử dụng.
     */
    @Modifying
    @Query("UPDATE Voucher v SET v.usedCount = COALESCE(v.usedCount, 0) + 1 " +
            "WHERE v.id = :id AND (v.usageLimit IS NULL OR COALESCE(v.usedCount, 0) < v.usageLimit)")
    int incrementUsedCountAtomic(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Voucher v SET v.usedCount = COALESCE(v.usedCount, 0) - 1 " +
            "WHERE v.id = :id AND COALESCE(v.usedCount, 0) > 0")
    int decrementUsedCountAtomic(@Param("id") Long id);
}
