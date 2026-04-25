package vn.uth.itcomponentsecommerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.uth.itcomponentsecommerce.entity.Voucher;

import java.util.Optional;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {
    Optional<Voucher> findByCode(String code);
}
