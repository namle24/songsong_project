# Hướng dẫn chạy Thử nghiệm: With LAN vs. Without LAN (Internet)

Tài liệu này hướng dẫn cách thiết lập và chạy hệ thống SongSong trong hai môi trường mạng khác nhau: **Mạng cục bộ (LAN)** tốc độ cao và **Mạng Internet** tốc độ chậm, nhằm chứng minh sự cải thiện hiệu năng khi tải song song theo yêu cầu của dự án.

---

## Môi trường 1: "With LAN" (Mạng nội bộ tốc độ cao)

**Mục đích**: Đo đạc hiệu năng cơ bản, loại bỏ các biến số về độ trễ Internet. Thích hợp để thấy ranh giới giới hạn của phần cứng (Disk I/O, Card mạng).

**Chuẩn bị**: Cả máy chạy Server (Directory, Daemon) và máy chạy Client (Download) phải kết nối chung vào một Router/Switch (cùng một mạng Wifi hoặc dây LAN).

### Các bước thực hiện:

1.  **Lấy IP của máy Server**:
    - Mở Terminal/CMD trên máy chạy Server.
    - Chạy lệnh `ip address` (Linux) hoặc `ipconfig` (Windows).
    - Ghi lại địa chỉ IPv4 nội bộ (Ví dụ: `192.168.2.59`).

2.  **Khởi động thư mục chứa Code**:
    - Đảm bảo cả 2 máy đều đã có mã nguồn mới nhất và đã được biên dịch (`mvn compile`).
    - Tạo thư mục `./data` trên máy Server và bỏ các file cần test (ví dụ: `file_64MB.mp4`) vào đó.

3.  **Khởi động Directory Server (trên máy Server)**:

    ```bash
    java -Djava.rmi.server.hostname=192.168.2.59 -cp target/classes edu.usth.songsong.directory.DirectoryServer
    ```

4.  **Khởi động các Daemon Server (trên máy Server)**:
    - Mở nhiều cửa sổ Terminal để chạy nhiều nguồn song song.
    - Daemon 1 (Port 5000):
      ```bash
      java -cp target/classes edu.usth.songsong.daemon.DaemonServer localhost 1099 5000 ./data 192.168.2.59
      ```
    - Daemon 2 (Port 5001):
      ```bash
      java -cp target/classes edu.usth.songsong.daemon.DaemonServer localhost 1099 5001 ./data 192.168.2.59
      ```
    - _(Mở thêm nhiều Daemon với các port 5002, 5003... nếu muốn)_

5.  **Bắt đầu Download (trên máy Client)**:
    - Mở Terminal trên máy khác cùng mạng LAN.
    ```bash
    java -cp target/classes edu.usth.songsong.download.DownloadManager file_64MB.mp4 192.168.2.59 1099
    ```

    - **Ghi nhận**: So sánh thời gian tải khi Server chỉ bật 1 Daemon so với khi Server bật 10 Daemon.

---

## Môi trường 2: "Without LAN" / Across the Internet (Mạng chậm)

**Mục đích**: Mô phỏng môi trường phân tán thực tế, độ trễ và băng thông mạng là yếu điểm. Đây là lúc cơ chế tải song song phát huy tối đa sức mạnh.

**Chuẩn bị**: Cài đặt **Tailscale** trên cả 2 máy. Tailscale sẽ cấp một địa chỉ IP ảo (VPN) cố định dạng `100.x.y.z` cho mỗi máy, giúp chúng có thể giao tiếp với nhau qua Internet dù ở hai mạng hoàn toàn khác nhau (VD: Server ở trường USTH, Client ở nhà).

### Các bước thực hiện:

1.  **Lấy IP Tailscale của máy Server**:
    - Mở ứng dụng Tailscale trên máy Server hoặc chạy lệnh `tailscale ip -4` (Linux).
    - Ghi lại địa chỉ IP Tailscale (Ví dụ: `100.71.169.126`).

2.  **Khởi động Directory Server (trên máy Server)**:

    ```bash
    java -Djava.rmi.server.hostname=100.71.169.126 -cp target/classes edu.usth.songsong.directory.DirectoryServer
    ```

3.  **Khởi động các Daemon Server (trên máy Server)**:
    - Lưu ý: Phải dùng **IP Tailscale** trong lệnh khai báo của Daemon.
    - Daemon 1:
      ```bash
      java -cp target/classes edu.usth.songsong.daemon.DaemonServer localhost 1099 5000 ./data 100.71.169.126
      ```
    - Daemon 2:
      ```bash
      java -cp target/classes edu.usth.songsong.daemon.DaemonServer localhost 1099 5001 ./data 100.71.169.126
      ```

4.  **Bắt đầu Download (trên máy Client - ở một mạng khác)**:
    - Máy Client (ví dụ: đang dùng mạng 4G hoặc Wifi ở quán cafe) bật Tailscale.
    - Chạy lệnh Download trỏ về IP Tailscale của Server.
    ```bash
    java -cp target/classes edu.usth.songsong.download.DownloadManager file_64MB.mp4 100.71.169.126 1099
    ```

### Phân tích và Giải thích kết quả

Khi thử nghiệm trong môi trường "Without LAN":

- Bạn sẽ thấy thời gian tải (Duration) cao hơn hẳn so với LAN, vì dữ liệu phải đi qua hạ tầng mạng công cộng (Internet).
- Đôi khi sẽ xuất hiện cảnh báo `WARNING: Connection timed out` hoặc `SocketException`. Đây là chuyện bình thường trên mạng không dây biến động.
- Theo dõi Log của [DownloadWorker](file:///d:/songsong_project/songsong-project/src/main/java/edu/usth/songsong/download/DownloadWorker.java#22-127), bạn sẽ thấy cơ chế **Failover** hoạt động: Các mảnh dữ liệu (Fragment) tải lỗi sẽ được trả lại hàng đợi (Queue) để các Worker khác tải lại từ đầu, đảm bảo tính toàn vẹn 100% của file đích.
