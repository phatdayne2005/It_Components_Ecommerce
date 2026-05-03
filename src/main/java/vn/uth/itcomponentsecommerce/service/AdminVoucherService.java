package vn.uth.itcomponentsecommerce.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.uth.itcomponentsecommerce.dto.*;
import vn.uth.itcomponentsecommerce.entity.Voucher;
import vn.uth.itcomponentsecommerce.repository.VoucherRepository;

import java.util.List;

@Service
public class AdminVoucherService {

    private final VoucherRepository voucherRepository;

    public AdminVoucherService(VoucherRepository voucherRepository) {
        this.voucherRepository = voucherRepository;
    }

    public List<Voucher> list() {
        return voucherRepository.findAll();
    }

    public Voucher get(Long id) {
        return voucherRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Voucher không tồn tại"));
    }

    @Transactional
    public Voucher create(VoucherRequest req) {
        String code = normalizeCode(req.getCode());
        if (voucherRepository.existsByCode(code)) {
            throw new IllegalArgumentException("Mã voucher đã tồn tại");
        }
        return voucherRepository.save(mapToEntity(new Voucher(), req, code));
    }

    @Transactional
    public Voucher update(Long id, VoucherRequest req) {
        Voucher v = get(id);
        String code = normalizeCode(req.getCode());
        if (!code.equals(v.getCode()) && voucherRepository.existsByCode(code)) {
            throw new IllegalArgumentException("Mã voucher đã tồn tại");
        }
        return voucherRepository.save(mapToEntity(v, req, code));
    }

    @Transactional
    public void delete(Long id) {
        voucherRepository.deleteById(id);
    }

    private static String normalizeCode(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase();
    }

    private static Voucher mapToEntity(Voucher v, VoucherRequest req, String code) {
        v.setCode(code);
        v.setName(req.getName());
        v.setDiscountType(req.getDiscountType());
        v.setDiscountValue(req.getDiscountValue());
        v.setMinOrder(req.getMinOrder());
        v.setMaxDiscount(req.getMaxDiscount());
        v.setValidFrom(req.getValidFrom());
        v.setValidTo(req.getValidTo());
        v.setUsageLimit(req.getUsageLimit());
        v.setActive(req.isActive());
        if (v.getUsedCount() == null) {
            v.setUsedCount(0);
        }
        return v;
    }
}
