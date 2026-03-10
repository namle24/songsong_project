# Hướng Dẫn Chạy Chương Trình (User Manual)

Dự án **SongSong Parallel Download** yêu cầu khởi chạy ba thành phần độc lập: **Directory**, **Daemon**, và **Download**.

Để mô phỏng hoàn cảnh thực tế theo đúng yêu cầu của bản mô tả dự án:
> - on the directory node, the Directory RMI server can be launched with : `java Directory`
> - on each client node, the Daemon socket server can be launched with : `java Daemon`
> - on one client node, a downloading can be started with : `java Download <filename>`

Chúng ta cần Biên dịch (Compile) mã nguồn trước tiên.

---

## Bước 1: Biên dịch mã nguồn (Compile)

Mở Terminal tại thư mục gốc của dự án (`/songsong_project`), sau đó chạy lệnh:
```bash
mkdir -p out
find src/main/java -name "*.java" > sources.txt
javac -d out @sources.txt
```
Lệnh này tóm gọn toàn bộ file mã nguồn Java và biên dịch (compile) chúng vào thư mục `/out`.

---

## Bước 2: Chạy Directory (Hoạt động như Server Trung Tâm)

Directory theo dõi vị trí các file và phải chạy đầu tiên để các máy con RMI có thể kết nối.

**Lệnh mẫu:**
```bash
java -cp out edu.usth.songsong.directory.DirectoryServer
```
*Lưu ý: Registry trên cổng `1099` sẽ tự động khởi tạo ngầm từ câu lệnh này.*

---

## Hướng Dẫn Vẽ Biểu Đồ So Sánh (Dành cho Báo Cáo & Demo)

Để thực hiện yêu cầu "chứng minh sự cải thiện hiệu suất" trong báo cáo và có số liệu vẽ biểu đồ, bạn **phải mở 4 terminal (cửa sổ dòng lệnh) riêng biệt**, tất cả đều `cd` vào thư mục dự án `/songsong_project`.

### Terminal 1: Chạy Server Trung Tâm (Directory)
Mở cửa sổ thứ nhất và khởi tạo sổ bạ hệ thống:
```bash
java -cp out edu.usth.songsong.directory.DirectoryServer
```
*Màn hình sẽ báo: "DirectoryServer bound in RMI registry..."*

### Terminal 2: Chạy Server Chia Sẻ 1 (Daemon 1)
Tạo dữ liệu ngẫu nhiên 5MB và chạy máy chủ thứ nhất ở cổng 5000:
```bash
mkdir -p data1
head -c 5M </dev/urandom > data1/testfile.bin
java -cp out edu.usth.songsong.daemon.DaemonServer localhost 1099 5000 ./data1
```
*Nhìn qua Terminal 1, bạn sẽ thấy Directory ghi nhận Server 1 vừa đăng ký file.*

### Terminal 3: Chạy Server Chia Sẻ 2 (Daemon 2)
Copy khối lượng file sang thư mục máy trạm thứ 2 và mở máy chủ ở cổng 5001:
```bash
mkdir -p data2
cp data1/testfile.bin data2/testfile.bin
java -cp out edu.usth.songsong.daemon.DaemonServer localhost 1099 5001 ./data2
```
*Directory (ở Terminal 1) sẽ ghi nhận nguồn tải thứ hai.*

### Terminal 4: Khởi chạy Trình Tải (Download Manager)
Đây là màn hình biểu diễn chính để quay video demo cho giáo sư. Mở cửa sổ 4 và nhập lệnh tải:
```bash
java -cp out edu.usth.songsong.download.DownloadManager testfile.bin localhost 1099
```

**Cách vẽ sơ đồ so sánh (Curve):**
1. **Trường hợp 1 nguồn:** Bạn hãy tắt Terminal 3 đi (ấn Ctrl+C), chỉ để lại 1 Daemon và đo kết quả thời gian tải (Duration) in ra trên Terminal 4.
2. **Trường hợp >1 nguồn:** Bật lại Terminal 3 hoặc mở thêm Terminal thứ 5, thứ 6 với các cổng 5002, 5003... và đo lại Duration để vẽ thành biểu đồ đường hiệu năng. Hơn nữa, nhìn song song vào Terminal 2, 3 lúc tải sẽ thấy chữ nhảy liên tục chứng tỏ file đang bị "xé nhỏ" kéo về cùng lúc cực kì trực quan!
