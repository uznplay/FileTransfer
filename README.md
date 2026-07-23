# File Transfer - Gửi file từ Máy tính sang Android

Công cụ truyền file cục bộ giữa máy tính (Windows/Linux) và điện thoại Android **qua mạng nội bộ** (Wi-Fi, Hotspot, LAN) **không cần Router Internet**.

## Hai lựa chọn cho điện thoại

| Phương thức | Mô tả | Phù hợp |
|------------|-------|---------|
| **APK** (Android App) | Cài app `.apk`, bật server HTTP, gửi file qua web | Người dùng phổ thông, không cần Termux |
| **Termux** (Script) | Dùng Termux + SSH/SCP, có mã hóa | Người dùng kỹ thuật, cần bảo mật cao |

---

# CÁCH 1: Dùng APK (Khuyên dùng)

## Yêu cầu

### Máy tính (Desktop)
- Python 3.6+ và pip
- Trình duyệt web (Chrome, Edge, Firefox...)

### Điện thoại Android
- Android 8.0 (API 26) trở lên
- File APK (cần build từ source hoặc tải file build sẵn)

## Build APK

### Cách 1: GitHub Actions (Khuyên dùng — không cần cài gì)

1. Push code lên GitHub (`main` branch)
2. Vào tab **Actions** trên GitHub
3. Chọn workflow **"Build APK"** → **"Run workflow"**
4. Đợi ~3 phút, tải file APK từ **Artifacts**

> Hoặc clone repo về rồi vào **Actions** bấm **"Run workflow"**.

### Cách 2: Script tự động (Windows)

```bash
cd android
build_apk.bat
```

Script sẽ tự động:
- Tải Android SDK command-line tools
- Cài SDK platform + build-tools
- Tải Gradle wrapper
- Build file APK

> Lần đầu chạy có thể mất 5-10 phút do tải SDK (~500MB).

### Cách 3: Script tự động (Linux/Mac)

```bash
cd android
chmod +x build_apk.sh
./build_apk.sh
```

### Cách 4: Build bằng Android Studio

1. Mở **Android Studio** → **File → Open** → chọn thư mục `android/`
2. Đợi Gradle sync xong
3. **Build → Build Bundle(s) / APK(s) → Build APK**
4. File APK nằm ở: `android/app/build/outputs/apk/debug/app-debug.apk`

### Cách 5: Build command line (có sẵn SDK)

```bash
cd android
./gradlew assembleDebug   # Linux/Mac
gradlew assembleDebug     # Windows
```

## Cách sử dụng

### Bước 1: Trên điện thoại

1. Cài file APK vừa build
2. Mở app **File Transfer**
3. Bấm **"Khởi động server"**
4. Cấp quyền bộ nhớ khi được yêu cầu
5. Xem địa chỉ IP hiển thị trên màn hình (VD: `http://192.168.1.x:8080`)

### Bước 2: Trên máy tính

1. Mở trình duyệt web
2. Nhập địa chỉ IP hiển thị trên điện thoại
3. Giao diện web upload hiện ra
4. Chọn file và bấm **"Gửi file lên điện thoại"**
5. File sẽ được lưu vào thư mục `Downloads/FileTransfer/` trên điện thoại

### Bước 3: Dùng CLI Python (gửi nhanh từ terminal)

```bash
pip install -r requirements.txt

# Gửi file nhanh qua HTTP tới app Android
python main.py --file "photo.jpg" --ip 192.168.1.x --port 8080 --http
```

> Gắn cờ `--http` để dùng HTTP upload thay vì SSH.

---

# CÁCH 2: Dùng Termux (SSH/SCP)

## Yêu cầu

- Termux trên điện thoại (tải từ F-Droid khuyến nghị)
- Quyền truy cập bộ nhớ

## Thiết lập Termux

```bash
# Trong Termux:
cd ~/storage/downloads
# Copy thư mục termux/ vào đây, sau đó:
cd termux
chmod +x setup.sh
bash setup.sh
```

## Gửi file từ máy tính

```bash
# Giao diện đồ họa
python main.py --gui

# Dòng lệnh
python main.py --file "data.zip" --ip 192.168.1.100
```

---

## Cấu trúc dự án

```
FileTransfer/
│
├── main.py              # Ứng dụng CLI + GUI (máy tính)
├── config.json          # Cấu hình kết nối SSH
├── requirements.txt     # Thư viện Python (paramiko, scp)
│
├── termux/
│   └── setup.sh         # Script thiết lập Termux (SSH)
│
├── android/             # Dự án Android Studio (APK)
│   ├── app/
│   │   └── src/main/
│   │       ├── java/com/filetransfer/
│   │       │   ├── MainActivity.kt          # Giao diện chính
│   │       │   ├── FileReceiverService.kt   # Service nền
│   │       │   └── FileUploadServer.kt      # HTTP server
│   │       ├── res/                         # Layout, theme, strings
│   │       └── AndroidManifest.xml
│   ├── build.gradle.kts
│   └── settings.gradle.kts
│
└── README.md            # Hướng dẫn (file này)
```

## Cấu hình config.json

| Trường | Mô tả | Mặc định |
|--------|-------|----------|
| `host` | IP Termux | `""` |
| `port` | Cổng SSH | `8022` |
| `username` | Username Termux | `u0_a123` |
| `password` | Mật khẩu SSH | `termux` |
| `remote_path` | Thư mục đích | `~/storage/downloads/FileTransfer` |

## Mẹo

1. **Không cần Internet**: Chỉ cần cùng mạng LAN/Wi-Fi (kể cả Hotspot)
2. **Hotspot Android**: Khi bật Hotspot, IP gateway thường là `192.168.43.1`
3. **APK chạy nền**: App Android chạy ngầm, không cần giữ màn hình sáng
4. **Tường lửa**: Đảm bảo không chặn port (mặc định 8080)

## Xử lý sự cố

| Vấn đề | Giải pháp |
|--------|-----------|
| Không kết nối được tới APK | Kiểm tra cùng mạng Wi-Fi, thử tắt tường lửa |
| File không lưu được | Cấp quyền "Manage all files" trong Settings |
| Server tự tắt | Vô hiệu hóa tối ưu pin cho app |
| Timeout SSH | Kiểm tra sshd đang chạy trong Termux |

## Giấy phép

MIT License
