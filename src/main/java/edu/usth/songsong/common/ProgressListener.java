package edu.usth.songsong.common;

/**
 * Interface để theo dõi tiến trình tải xuống và gắn kết với UI (Swing Server).
 * Cho phép cập nhật Progress Bar theo thời gian thực và ghi nhật ký lên TextArea.
 */
public interface ProgressListener {

    /**
     * Cập nhật khi có dữ liệu vừa được đẩy vào file lưu tạm.
     * @param bytesRead Số byte dữ liệu vừa tải xuống thành công
     */
    void onProgress(int bytesRead);

    /**
     * Sự kiện tải xuống thành công toàn vẹn.
     * @param durationMs Thời gian thực thi
     * @param path Đường dẫn file đầu ra
     */
    void onComplete(long durationMs, String path);

    /**
     * Sự kiện lỗi nghiêm trọng khiến việc lấy file thất bại
     * @param message Mô tả nguyên do
     */
    void onError(String message);

    /**
     * Ghi tệp nhật trình bằng chữ thuần ra khung giao diện
     * @param message Lời nhắn báo cáo
     */
    void onLog(String message);
}
