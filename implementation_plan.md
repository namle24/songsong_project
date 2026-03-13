# Mục Tiêu

Thêm một giao diện người dùng (GUI) Desktop đơn giản và nhẹ nhàng cho dự án **SongSong Parallel Download**.

Hiện tại, dự án chỉ chạy hoàn toàn trên giao diện dòng lệnh bash. Việc thêm một GUI sẽ làm cho phần mềm trở nên trực quan và dễ hình dung hơn rất nhiều khi làm báo cáo hoặc quay video demo. 

Nhằm đáp ứng yêu cầu khắt khe của dự án đồ án là **chỉ được phép sử dụng các thư viện chuẩn đi kèm trong Java** mà không cài cắm bất cứ Framework của bên thứ 3 nào, chúng ta sẽ bắt tay vào sử dụng **Java Swing**. Điều này đảm bảo dự án vẫn "nhẹ tựa lông hồng", giữ nguyên file [pom.xml](file:///c:/Users/Admin/songsong_project/pom.xml), và UI sẽ chạy hoàn hảo trên tất cả các máy tính có cài đặt Java 17.

## Đề Xuất Thay Đổi

### 1. Thành phần Thiết Kế Giao Diện (UI) Mới
*   **[NEW] `src/main/java/edu/usth/songsong/ui/DownloadUI.java`**
    *   Tạo một khung cửa sổ màn hình `JFrame` đơn giản bao gồm:
        *   Một ô văn bản nhập **Tên File (Filename)** cần tải.
        *   Các ô thiết lập **IP Máy Tổng Đài (Directory Host)** và **Cổng (Port)** (có sẵn giá trị mặc định).
        *   Một nút bấm cỡ lớn **"Bắt đầu tải về" (Start Download)**.
        *   Một thanh tiến trình **`JProgressBar`** trải dài màn hình để xem file đang tải được bao nhiêu % theo thời gian thực.
        *   Một khung màn hình đen **`JTextArea`** (đóng vai trò thông báo nhật ký hành động) nhằm xuất ra các chữ như báo lỗi đứt cáp mạng, phát hiện bao nhiêu máy chủ Seeder,...
    *   Lớp UI này sẽ tự động khởi tạo kết nối backend ẩn ở một luồng nền tách biệt (Background Worker Thread) khi người dùng bấm nút tải, để **ngăn chặn rủi ro làm đơ treo (freeze) bảng GUI màn hình**.

### 2. Cầu nối Theo Dõi Tiến Trình
*   **[NEW] `src/main/java/edu/usth/songsong/common/ProgressListener.java`**
    *   Đây là một "giao thức" (Interface) ép cho hệ thống backend phải báo cáo tình hình làm việc lên cho Giao diện UI:
        *   `void onProgress(int bytesRead);` (Cập nhật khi có dữ liệu vừa chạy về)
        *   `void onComplete(long durationMs, String path);` (Khi tải xong)
        *   `void onError(String message);` (Khi dán nhãn lỗi)
        *   `void onLog(String message);` (Khi in báo cáo chữ)

### 3. Cập nhật Backend Lõi Để Hỗ Trợ UI
*   **[MODIFY] [src/main/java/edu/usth/songsong/download/DownloadManager.java](file:///c:/Users/Admin/songsong_project/src/main/java/edu/usth/songsong/download/DownloadManager.java)**
    *   Chèn thêm khả năng kết nối cầu nối `ProgressListener`.
    *   Thay vì chỉ in Console bằng hàm `LOG.info()` như cũ, các biến cố như "Không tìm thấy file" sẽ được tự động báo `listener.onLog()` để bay ngược lên in vào khung UI.
    *   Ủy thác (Pass-down) nhiệm vụ theo dõi này cho các máy con tải xuống.
*   **[MODIFY] [src/main/java/edu/usth/songsong/download/DownloadWorker.java](file:///c:/Users/Admin/songsong_project/src/main/java/edu/usth/songsong/download/DownloadWorker.java)**
    *   Nạp interface `ProgressListener`.
    *   Bên trong cái vòng lặp chép file byte-by-byte (`byte[] buffer = new byte[8192]`), cứ mỗi khi có mảng ký tự ghi thành công vào ổ cứng `RandomAccessFile`, lập tức hô to (trigger) `listener.onProgress()`. Bằng cách này, thanh Tiến Trình ngoài màn hình UI mới nhích nhảy % liên tục và vô cùng đã mắt.

## Kế Hoạch Đánh Giá Xoay Vòng (Verification Plan)

### Kiểm Thử Môi Trường Kín (Automated Tests)
*   Sử dụng lại lệnh bash test ngầm [test_download.sh](file:///c:/Users/Admin/songsong_project/test_download.sh) có sẵn. Mục đích để chắc chắn một điều rằng dù chúng ta khoác thêm cái áo UI, nó không hề bẻ gãy lõi code cũ. Phần mềm vẫn có thể chạy Console 100% nếu người dùng gõ lệnh như sách giáo khoa!

### Kiểm Thử Nhãn Quan Thực Tế (Manual / Visual UI)
1.  **Dựng Tổng đài:** Chạy `java -cp out edu.usth.songsong.directory.DirectoryServer`
2.  **Khởi động 2 Người gieo hạt (Daemon):** Cắt một file phim rác nặng cỡ 50MB, dựng 2 ông Daemon tại cổng nhà 5000 và 5001.
3.  **Khởi động Giao diện người dùng (UI):** Chạy `java -cp out edu.usth.songsong.ui.DownloadUI`
4.  **Kịch bản thao tác:**
    *   Nhập tên bộ phim giả trên vào bảng GUI.
    *   Bấm mạnh nút "Bắt đầu tải".
    *   Thấy tận mắt bảng màn hình hiện console chữ "TìM THẤY 2 NGƯỜI GIỮ FILE".
    *   Quan sát dải Progress Bar xanh trượt từ 0 đến 100%.
    *   Lôi cái file 50MB mới tải về kia vào đối chiếu kích thuớc so với bản gốc của người gieo hạt.
