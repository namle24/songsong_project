# Step 2 — Giải thích code: `daemon` package

## Tổng quan

```text
edu.usth.songsong.daemon
├── DaemonServer.java       ← TCP server + RMI client
└── FileTransferTask.java   ← Xử lý 1 request từ downloading client
```

Daemon chạy trên mỗi client node. Nó làm 2 việc:

1. **RMI Client**: Gọi `DirectoryService.register()` để báo cho Directory biết "tôi có file gì"
2. **TCP Server**: Lắng nghe và phục vụ fragment file cho các client khác download

---

## 1. `DaemonServer.java`

_(File: `src/main/java/edu/usth/songsong/daemon/DaemonServer.java`)_

### Flow khi khởi động

_(Trong hàm `main()`, dòng 111-126)_

```java
1. scanFiles()           // quét thư mục ./data, lấy danh sách filename (dòng 117, code ở dòng 43-58)
2. registerWithDirectory() // RMI call đến Directory: register(ip, port, files) (dòng 122, code ở dòng 60-74)
3. addShutdownHook()     // đăng ký hook: khi Ctrl+C sẽ tự gọi unregister() (dòng 123, code ở dòng 76-90)
4. startTcpServer()      // ServerSocket lắng nghe port 5000, vòng lặp accept() (dòng 124, code ở dòng 92-105)
```

### Tại sao dùng `ExecutorService` (Thread Pool)?

_(Định nghĩa ở dòng 34, khởi tạo ở dòng 40)_

Khi nhiều client download cùng lúc, mỗi connection cần 1 thread. Thay vì `new Thread()` cho mỗi request (tốn tài nguyên, không giới hạn), thread pool:

- Giới hạn tối đa 10 thread đồng thời (`THREAD_POOL_SIZE = 10`, dòng 29)
- Tái sử dụng thread đã hoàn thành → hiệu quả hơn khi client connect/disconnect liên tục để lấy file fragment.
- Dispatches task ở dòng 100: `threadPool.submit(new FileTransferTask(...))`

### Tại sao có Shutdown Hook?

_(Hàm `addShutdownHook()`, dòng 76-90)_

Khi Daemon bị tắt (ví dụ ấn Ctrl+C), nó phải tự gọi `unregister()` để Directory tức thời xóa nó khỏi danh sách. Không có hook → Directory sẽ giữ client lỗi này cho tới khi vòng health check (sau 30 giây) quét qua và tự loại bỏ.

---

## 2. `FileTransferTask.java`

_(File: `src/main/java/edu/usth/songsong/daemon/FileTransferTask.java`)_

### Protocol (giao thức tự định nghĩa qua TCP)

_(Hàm `run()`, xử lý chuỗi lệnh ở dòng 26-54)_

| Lệnh   | Format                  | Xử lý tại                     | Response                                        |
| ------ | ----------------------- | ----------------------------- | ----------------------------------------------- |
| `SIZE` | `SIZE song.mp3`         | `handleSize()` (dòng 105-125) | `1048576\n` (file size dạng text)               |
| `GET`  | `GET song.mp3 0 524288` | `handleGet()` (dòng 56-103)   | Raw bytes (đọc tối đa 524288 bytes từ offset 0) |

### Tại sao dùng `RandomAccessFile`?

_(Trong `handleGet()`, dòng 75-79)_

Đề bài bắt buộc. `RandomAccessFile` cho phép:

- Mở file dạng "read-only" (`"r"`, dòng 77).
- `seek(offset)` (dòng 78) → nhảy chuột đến vị trí byte bất kỳ trong file.
- Nhiều thread có thể mở cùng 1 file đồng thời (do mỗi thread tạo 1 obj `RandomAccessFile` độc lập phục vụ fragment độc lập).

Đây là nguyên lý sinh ra **parallel download**: nhiều Downloader cùng xin các offset khác nhau của cùng 1 file từ các TCP Socket khác nhau trên cùng 1 Daemon, và Daemon có thể seek+đọc trả về chuẩn xác.

### Tại sao đọc theo chunk 8KB thay vì 1 lần?

_(Dòng 80-90)_

```java
byte[] buffer = new byte[8192];  // 8KB buffer (dòng 80)
while (remaining > 0) {
    int toRead = Math.min(buffer.length, remaining); // dòng 84
    int bytesRead = raf.read(buffer, 0, toRead);     // dòng 85
    ...
}
```

Nếu fragment cần trả về là `100MB`, dùng 1 lần `read(new byte[100MB])` sẽ cắn thẳng tắp 100MB RAM server. Nếu 10 luồng tải chạy cùng lúc thì hết sạch bộ nhớ.
Đọc theo buffer 8KB thì dù fragment to đến mấy, server cũng chỉ tốn 8KB x (số thread) RAM mà thôi.

### Resource cleanup

_(Khối `finally` ở dòng 51-53 và 95-102)_

- `socket` được đóng trong finally (dòng 51 gọi hàm `closeSocket()` dòng 127).
- `raf` được đóng trong finally của `try` block mở file (dòng 95-102).
  Đảm bảo nếu mạng lỗi đang truyền dở, server vẫn release file handler và release socket không gây rò rỉ (memory/resource leak).
