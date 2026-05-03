package vn.uth.itcomponentsecommerce.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class RevenueReportResponse {
    private BigDecimal totalRevenue = BigDecimal.ZERO;
    private long totalOrders;
    private List<Daily> series = new ArrayList<>();

    public BigDecimal getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }
    public long getTotalOrders() { return totalOrders; }
    public void setTotalOrders(long totalOrders) { this.totalOrders = totalOrders; }
    public List<Daily> getSeries() { return series; }
    public void setSeries(List<Daily> series) { this.series = series; }

    public static class Daily {
        private String date;
        private BigDecimal revenue;
        private long orders;

        public Daily() {}
        public Daily(String date, BigDecimal revenue, long orders) {
            this.date = date;
            this.revenue = revenue;
            this.orders = orders;
        }
        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public BigDecimal getRevenue() { return revenue; }
        public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }
        public long getOrders() { return orders; }
        public void setOrders(long orders) { this.orders = orders; }
    }
}
