# Step 1 — Giải thích code: `common` + `directory`

## Tổng quan kiến trúc

```text
edu.usth.songsong
├── common/                 ← Shared types, dùng chung giữa tất cả components
│   ├── DirectoryService.java   ← RMI interface
│   └── ClientInfo.java         ← DTO (Data Transfer Object)
│
└── directory/              ← Directory server
    └── DirectoryServer.java    ← RMI server implementation
```

---

## 1. `DirectoryService.java` — RMI Interface

_(File: `src/main/java/edu/usth/songsong/common/DirectoryService.java`, dòng 11-21)_

**Là gì?** Hợp đồng (contract) giữa Directory server và các client (Daemon, Download).

**Tại sao dùng RMI?** Đề bài yêu cầu Directory phải accessible via RMI. Extends `java.rmi.Remote` = Java có thể gọi method này từ xa qua mạng.

| Method                      | Ai gọi?  | Mục đích                                                                                                   |
| --------------------------- | -------- | ---------------------------------------------------------------------------------------------------------- |
| `register(ip, port, files)` | Daemon   | Lúc Daemon khởi động, nó gọi method này để báo cho Directory biết "tôi ở IP:port này, tôi có các file này" |
| `unregister(ip, port)`      | Daemon   | Lúc Daemon tắt, xóa mình khỏi Directory                                                                    |
| `lookup(filename)`          | Download | "File X nằm ở đâu?" → trả về set các client có file đó                                                     |

---

## 2. `ClientInfo.java` — Serializable DTO

_(File: `src/main/java/edu/usth/songsong/common/ClientInfo.java`, dòng 8-36)_

**Là gì?** Object đơn giản chứa `ip` + `port`, đại diện cho 1 client (Daemon).

**Tại sao `Serializable`?** Vì nó được truyền qua RMI (qua mạng), Java cần serialize nó thành bytes.

**Tại sao cần `equals()` + `hashCode()`?** _(dòng 24-34)_ Vì ta lưu `ClientInfo` trong `Set`. Nếu không override, Java so sánh theo địa chỉ bộ nhớ → 2 object cùng ip:port sẽ bị coi là khác nhau.

---

## 3. `DirectoryServer.java` — RMI Server

**Là gì?** Trái tim của hệ thống. Lưu trữ mapping: file nào nằm ở client nào.

### Data structures

_(File: `src/main/java/edu/usth/songsong/directory/DirectoryServer.java`, dòng 28-35)_

```text
fileToClientsMap:  "song.mp3" → {ClientInfo(192.168.1.2:5000), ClientInfo(10.0.0.5:5000)}
                   "video.mp4" → {ClientInfo(10.0.0.5:5000)}

clientToFilesMap:  ClientInfo(192.168.1.2:5000) → {"song.mp3"}          ← reverse index
                   ClientInfo(10.0.0.5:5000)    → {"song.mp3", "video.mp4"}
```

**Tại sao `ConcurrentHashMap`?** Nhiều Daemon có thể gọi `register()` / `unregister()` cùng lúc. `ConcurrentHashMap` cho phép đọc/ghi đồng thời mà không lock toàn bộ map.

**Tại sao `Collections.synchronizedSet()`?** _(sử dụng tại dòng 42, 57, 63)_ Mỗi value trong map là 1 `Set`. `ConcurrentHashMap` chỉ bảo vệ map-level operations, không bảo vệ khi ta modify value bên trong. `synchronizedSet` bảo vệ set đó.

**Tại sao có `clientToFilesMap` (reverse index)?** Khi `unregister()` _(dòng 72-94)_, ta cần biết client đó sở hữu những file nào để xóa nó khỏi từng file's set. Không có reverse index → phải duyệt toàn bộ map = rất chậm.

### Health Check (Dynamic Adaptation)

_(File: `src/main/java/edu/usth/songsong/directory/DirectoryServer.java`, dòng 114-146)_

```text
Mỗi 30 giây:
  1. Lấy snapshot danh sách clients (dòng 118-121)
  2. Với mỗi client, thử TCP connect đến ip:port (dòng 138-146, hàm isAlive)
  3. Nếu connect thất bại → client đã chết → gọi unregister() tự động (dòng 127)
```

**Tại sao dùng `ScheduledExecutorService`?** _(dòng 37, 44-48)_ Thay vì tự quản lý Thread + sleep, `ScheduledExecutorService` là cách chuẩn trong Java để chạy task định kỳ. Thread được set `daemon=true` để không chặn JVM khi shutdown.

**Tại sao ping bằng TCP Socket?** Vì Daemon lắng nghe trên TCP socket (theo đề bài). Nếu connect được → Daemon còn sống.

### `main()` flow

_(File: `src/main/java/edu/usth/songsong/directory/DirectoryServer.java`, dòng 148-165)_

```java
1. new DirectoryServer()                        // tạo RMI object (dòng 150)
2. LocateRegistry.createRegistry(1099)          // tạo RMI registry tại port 1099 (dòng 152)
3. registry.rebind("DirectoryService", server)  // đăng ký object với tên "DirectoryService" (dòng 153)
4. startHealthCheck()                           // bật thread ping clients (dòng 155)
5. Chờ vô hạn (RMI tự giữ thread)
```
