package vn.uth.itcomponentsecommerce.service;

import org.springframework.stereotype.Service;
import vn.uth.itcomponentsecommerce.dto.RevenueReportResponse;
import vn.uth.itcomponentsecommerce.repository.OrderRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdminReportService {

    private final OrderRepository orderRepository;

    public AdminReportService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public RevenueReportResponse revenueLastDays(int days) {
        int d = Math.min(Math.max(days, 1), 365);
        LocalDateTime from = LocalDateTime.now().minusDays(d);
        RevenueReportResponse res = new RevenueReportResponse();
        BigDecimal total = orderRepository.sumRevenueSince(from);
        res.setTotalRevenue(total != null ? total : BigDecimal.ZERO);

        List<Object[]> rows = orderRepository.dailyRevenueRows(from);
        long orderCount = 0;
        for (Object[] row : rows) {
            String dateStr = row[0] != null ? row[0].toString() : "";
            BigDecimal rev = toBd(row[1]);
            long cnt = row[2] instanceof Number ? ((Number) row[2]).longValue() : 0L;
            orderCount += cnt;
            res.getSeries().add(new RevenueReportResponse.Daily(dateStr, rev, cnt));
        }
        res.setTotalOrders(orderCount);
        return res;
    }

    private static BigDecimal toBd(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        return BigDecimal.valueOf(((Number) o).doubleValue());
    }
}
