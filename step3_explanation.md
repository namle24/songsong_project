# Step 3 — Giải thích code: `download` package

## Tổng quan

```text
edu.usth.songsong.download
├── FragmentInfo.java       ← DTO chứa thông tin về 1 phần của file cần tải (offset, length)
├── DownloadWorker.java     ← Luồng (Thread) thực hiện tải 1 fragment từ 1 Daemon
└── DownloadManager.java    ← Lớp quản lý chính: tìm resources, chia chunks, và gộp file
```

Download component chịu trách nhiệm chính trong việc **Parallel Downloading** (tải song song) và **Failure Handling** (xử lý lỗi khi rớt mạng).

---

## 1. `FragmentInfo.java` (File: `src/main/java/edu/usth/songsong/download/FragmentInfo.java`)

**Là gì?** Một `record` (tính năng của Java cao cấp) chứa metadata đơn giản nhất để biểu diễn 1 đoạn file cần tải.

```java
public record FragmentInfo(String filename, long offset, int length) {}
```
- `filename`: Tên file.
- `offset`: Byte bắt đầu cần tải ở trên file đích.
- `length`: Độ dài (số lượng bytes) cần lấy.

---

## 2. `DownloadManager.java` (File: `src/main/java/edu/usth/songsong/download/DownloadManager.java`)

**Là gì?** Lớp điều phối (Orchestrator). Nhận vào tên file và bắt tay vào lên kế hoạch tải.

### Quy trình hoạt động (`downloadFile`):

**Bước 1: RMI Lookup (dòng 48-55)**
- Kết nối tới `DirectoryService` qua RMI.
- Gọi hàm `lookup(filename)` để lấy danh sách các Daemons (`ClientInfo`) đang chứa file này.
- Nếu không tìm thấy ai, báo lỗi và dừng chạy.

**Bước 2: Lấy kích thước tổng của file (dòng 58-63)**
- Gọi method `getFileSizeFromDaemon(...)`. Method này mở một Socket TCP siêu tốc tới Daemon đầu tiên trong danh sách, gửi lệnh `SIZE <filename>`.
- Server trả về một String kích thước.

**Bước 3: Tiền cấp phát tệp tin (Pre-allocating) (dòng 66-70)**
- Ghi đè rỗng toàn bộ nội dung file gốc bằng đoạn code:
  ```java
  try (RandomAccessFile raf = new RandomAccessFile(outputFile, "rw")) {
      raf.setLength(totalSize);
  }
  ```
- **Tại sao?** Khi tải nhiều luồng, chúng ta sẽ viết trực tiếp bytes vào các vị trí khác nhau trong ổ cứng. Nếu file chưa được khởi tạo với kích thước chuẩn xác định `totalSize`, việc `raf.seek(offset)` tới những vị trí xa sẽ gây lỗi hoặc không hợp lệ. Điều kiện để làm việc này là RandomAccessFile dạng `"rw"` (read-write).

**Bước 4: Chia Fragment (Fragmentation) (dòng 73-80)**
- Khởi tạo `ConcurrentLinkedQueue<FragmentInfo> queue`.
- Vòng lặp tính toán chia nhỏ `totalSize` thành các mảnh (mặc định 1MB = `1024 * 1024` bytes).
- Đẩy toàn bộ các gói FragmentInfo này vào queue (`queue.offer()`).
- **Tại sao dùng ConcurrentLinkedQueue?** Vì hàng đợi này sẽ được hàng tá luồng (DownloadWorkers) lôi ra tiêu thụ đồng thời. `ConcurrentLinkedQueue` cung cấp cơ chế `poll()` hoàn toàn Thread-Safe không khóa cứng lock mutex.

**Bước 5: Kích hoạt Thread Pool (dòng 83-93)**
- Tạo `ExecutorService` có `threadCount` (số luồng) đúng bằng với tổng số Daemons cung cấp file đó.
- Ném class `DownloadWorker` vào `ThreadPool` để chúng bắt đầu rút Queue ra làm việc liên tục.
- Lệnh `executor.awaitTermination(1, TimeUnit.HOURS)` block thread Main lại chờ cho tới khi Worker tải xong hoàn toàn.

---

## 3. `DownloadWorker.java` (File: `src/main/java/edu/usth/songsong/download/DownloadWorker.java`)

**Là gì?** Chó săn đa luồng. Mỗi Worker Thread sẽ liên tục rút các Fragment trong Queue ra để kết nối TCP kéo dữ liệu về máy lưu tự động.

### Vòng lặp tải dữ liệu (`call()` từ dòng 39-59)
- Hái Fragment từ trên ngọn Hàng đợi (Queue) xuống thông qua hàm lấy `poll()`.
- Chọn một Daemon dựa vào vòng chia tài nguyên Round-Robin (trải đều).
- Mở liên kết `downloadFragment()`.
- Nếu lỗi xả ra ở TCP Socket (`SocketTimeoutException`, `SocketException`), worker sẽ in ra cảnh báo.

### Giao thức TCP Cốt lõi (`downloadFragment()` từ dòng 62-101)
- Request lệnh `GET <filename> <offset> <length>`.
- Kết nối ổ đĩa qua `RandomAccessFile` chế độ `"rw"`.
- Định vị đầu con trỏ tại khu vực của fragment hiện trường: `raf.seek(fragment.offset());`.
- Nhận luồng dữ liệu thô `InputStream` chia nhỏ 8KB từ byte mảng TCP và nhồi vào đĩa bằng lệnh `raf.write()`.

### Cơ chế Fallback an toàn Failover (Chống gián đoạn) : `handleFailure()` (dòng 103-114)
Đây là đặc điểm xịn xò nhất phục vụ kỳ vọng của thầy hiệu trường. Giả dụ fragment 1MB đã tải được 200KB thì máy chủ Daemon cúp điện đột ngột.

- `catch` exception an toàn.
- Ngay lập tức tính toán: `remainingBytes = fragment.length() - downloadedBytes`.
- Đẩy số dở dang vào queue lại với vị trí offset mới:
  ```java
  long newOffset = fragment.offset() + downloadedBytes;
  FragmentInfo remainingFragment = new FragmentInfo(fragment.filename(), newOffset, remainingBytes);
  fragmentQueue.offer(remainingFragment);
  ```
- Nhờ việc đưa lại Queue, luồng tải tới sẽ rút phần còn lại lên và nhắm bắn vào Daemon khác (fail-over handling) để đắp miếng ghép vô khoảng trống bị thọt, đảm bảo file 100% không dính bụi corrupt. Mất kết nối không có nghĩa là hỏng file!
