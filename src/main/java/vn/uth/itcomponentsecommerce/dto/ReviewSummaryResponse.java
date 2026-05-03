package vn.uth.itcomponentsecommerce.dto;

/**
 * Tóm tắt đánh giá khách hàng (số lượt, điểm trung bình).
 */
public class ReviewSummaryResponse {
    private long count;
    private double average;

    public ReviewSummaryResponse() {
    }

    public ReviewSummaryResponse(long count, double average) {
        this.count = count;
        this.average = average;
    }

    public long getCount() { return count; }
    public void setCount(long count) { this.count = count; }
    public double getAverage() { return average; }
    public void setAverage(double average) { this.average = average; }
}
