#!/usr/bin/env python3
# ==============================================================
# main.py - Ứng dụng gửi file từ Máy tính tới Termux (Android)
# ==============================================================
# Hỗ trợ:
#   - CLI: gửi file nhanh bằng dòng lệnh
#   - GUI (Tkinter): giao diện đồ họa trực quan
#   - Tự động lưu cấu hình (IP, port, user, password)
#   - Truyền file qua SSH/SCP dùng thư viện paramiko
# ==============================================================

import json
import os
import sys
import argparse
import tkinter as tk
from tkinter import filedialog, messagebox, ttk
from pathlib import Path

# --- Cấu hình mặc định ---
CONFIG_FILE = Path(__file__).parent / "config.json"
DEFAULT_CONFIG = {
    "host": "",
    "port": 8022,
    "username": "u0_a123",
    "password": "termux",
    "remote_path": "~/storage/downloads/FileTransfer",
    "use_key": False,
    "key_path": "",
}

# --- Hàm đọc/ghi cấu hình ---

def load_config():
    """Đọc cấu hình từ file config.json"""
    if CONFIG_FILE.exists():
        try:
            with open(CONFIG_FILE, "r", encoding="utf-8") as f:
                data = json.load(f)
            # Loại bỏ comment bắt đầu bằng _
            return {k: v for k, v in data.items() if not k.startswith("_")}
        except (json.JSONDecodeError, IOError) as e:
            print(f"[!] Lỗi đọc config.json: {e}")
    return dict(DEFAULT_CONFIG)


def save_config(config):
    """Ghi cấu hình vào file config.json"""
    try:
        with open(CONFIG_FILE, "w", encoding="utf-8") as f:
            json.dump(config, f, indent=4, ensure_ascii=False)
        print(f"[+] Đã lưu cấu hình vào {CONFIG_FILE}")
    except IOError as e:
        print(f"[!] Lỗi ghi config.json: {e}")


# --- Hàm truyền file qua SCP ---

def send_file_via_scp(local_path, config, progress_callback=None):
    """
    Gửi file tới Termux qua SCP sử dụng paramiko.
    
    Args:
        local_path: Đường dẫn file cục bộ
        config: Dict chứa host, port, username, password
        progress_callback: Hàm callback nhận (sent, total) để cập nhật tiến trình
    
    Returns:
        True nếu thành công, False nếu thất bại
    """
    import paramiko
    from scp import SCPClient

    host = config.get("host", "")
    port = int(config.get("port", 8022))
    username = config.get("username", "")
    password = config.get("password", "")
    remote_path = config.get("remote_path", "~/storage/downloads/FileTransfer")
    use_key = config.get("use_key", False)
    key_path = config.get("key_path", "")

    # Kiểm tra thông tin kết nối
    if not host:
        print("[!] Lỗi: Chưa nhập địa chỉ IP của Termux.")
        return False
    if not username:
        print("[!] Lỗi: Chưa nhập Username.")
        return False

    local_path = Path(local_path)
    if not local_path.exists():
        print(f"[!] Lỗi: File không tồn tại: {local_path}")
        return False

    file_size = local_path.stat().st_size
    file_name = local_path.name

    try:
        # Tạo kết nối SSH
        print(f"[*] Đang kết nối tới {username}@{host}:{port} ...")
        ssh = paramiko.SSHClient()
        ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())

        if use_key and key_path:
            key_path_expanded = os.path.expanduser(key_path)
            if not os.path.exists(key_path_expanded):
                print(f"[!] Lỗi: File key không tồn tại: {key_path_expanded}")
                return False
            pkey = paramiko.RSAKey.from_private_key_file(key_path_expanded)
            ssh.connect(host, port=port, username=username, pkey=pkey, timeout=15)
        else:
            ssh.connect(host, port=port, username=username, password=password, timeout=15)

        print(f"[+] Đã kết nối thành công tới {host}")

        # Tạo thư mục đích từ xa nếu chưa tồn tại
        ssh.exec_command(f"mkdir -p {remote_path}")

        # Hàm callback theo dõi tiến trình
        def _progress(filename, sent, total):
            if progress_callback:
                progress_callback(sent, total)
            percent = int(sent / total * 100) if total > 0 else 0
            bar = "#" * (percent // 2) + "." * (50 - percent // 2)
            print(f"\r  [{bar}] {percent}% ({sent/1024/1024:.1f}MB / {total/1024/1024:.1f}MB)", end="")

        # Truyền file qua SCP
        print(f"[*] Đang gửi: {file_name} ({file_size/1024/1024:.1f} MB)")
        with SCPClient(ssh.get_transport(), progress=_progress) as scp:
            scp.put(str(local_path), f"{remote_path}/{file_name}")

        print(f"\n[+] Gửi file thành công: {file_name}")
        print(f"    => {remote_path}/{file_name}")

        ssh.close()
        return True

    except paramiko.AuthenticationException:
        print(f"\n[!] Lỗi xác thực: Sai username hoặc mật khẩu.")
        return False
    except paramiko.SSHException as e:
        print(f"\n[!] Lỗi SSH: {e}")
        return False
    except (TimeoutError, OSError) as e:
        print(f"\n[!] Lỗi kết nối: {e}")
        return False
    except Exception as e:
        print(f"\n[!] Lỗi không xác định: {e}")
        return False


# --- Hàm truyền file qua HTTP (dùng cho Android APK) ---

def send_file_via_http(local_path, config, progress_callback=None):
    """
    Gửi file tới Android APK qua HTTP POST upload.
    
    Args:
        local_path: Đường dẫn file cục bộ
        config: Dict chứa host, port
        progress_callback: Hàm callback nhận (sent, total)
    
    Returns:
        True nếu thành công, False nếu thất bại
    """
    import requests

    host = config.get("host", "")
    port = int(config.get("port", 8080))

    if not host:
        print("[!] Lỗi: Chưa nhập địa chỉ IP.")
        return False

    local_path = Path(local_path)
    if not local_path.exists():
        print(f"[!] Lỗi: File không tồn tại: {local_path}")
        return False

    file_size = local_path.stat().st_size
    file_name = local_path.name
    url = f"http://{host}:{port}/upload"

    try:
        print(f"[*] Đang gửi tới {url} ...")

        with open(local_path, "rb") as f:
            files = {"file": (file_name, f, "application/octet-stream")}
            
            # Dùng stream để theo dõi tiến trình
            class ProgressFileWrapper:
                def __init__(self, file_obj, total_size):
                    self.file_obj = file_obj
                    self.total_size = total_size
                    self.sent = 0
                
                def __len__(self):
                    return self.total_size

            response = requests.post(
                url,
                files=files,
                timeout=300,
            )

        if response.status_code == 200:
            print(f"[+] Thành công: {response.text}")
            return True
        else:
            print(f"[!] Lỗi server: {response.status_code} - {response.text}")
            return False

    except requests.exceptions.ConnectionError:
        print(f"\n[!] Lỗi kết nối: Không thể kết nối tới {host}:{port}")
        print("    Kiểm tra: (1) Điện thoại đã bật server? (2) Cùng mạng Wi-Fi?")
        return False
    except requests.exceptions.Timeout:
        print(f"\n[!] Lỗi: Quá thời gian chờ ({host}:{port})")
        return False
    except Exception as e:
        print(f"\n[!] Lỗi: {e}")
        return False


# --- CLI Mode ---

def cli_mode(args):
    """Chạy ở chế độ dòng lệnh"""
    config = load_config()

    # Ghi đè cấu hình từ tham số dòng lệnh nếu có
    if args.ip:
        config["host"] = args.ip
    if args.port:
        config["port"] = args.port
    if args.user:
        config["username"] = args.user
    if args.password:
        config["password"] = args.password
    if args.remote:
        config["remote_path"] = args.remote

    # Nếu không có file được chỉ định, hiển thị hướng dẫn
    if not args.file:
        print("=" * 50)
        print("  FILE TRANSFER - CLI MODE")
        print("=" * 50)
        print(f"\n  Cấu hình hiện tại:")
        print(f"    IP Termux:     {config['host'] or '(chưa đặt)'}")
        print(f"    Port:          {config['port']}")
        print(f"    Username:      {config['username']}")
        print(f"    Remote path:   {config['remote_path']}")
        print(f"\n  Cách dùng:")
        print(f"    python main.py --file <đường_dẫn_file> [--ip <IP>]")
        print(f"\n  Ví dụ:")
        print(f'    python main.py --file "C:\\Users\\Public\\photo.jpg" --ip 192.168.1.100')
        print(f"    python main.py --file mydata.zip")
        print(f"    python main.py --gui  (mở giao diện đồ họa)")
        print("=" * 50)
        return

    # Chọn phương thức gửi
    if args.http:
        print(f"[*] Gửi qua HTTP tới http://{config['host']}:{config['port']}/upload")
        success = send_file_via_http(args.file, config)
    else:
        print(f"[*] Bắt đầu gửi file qua SCP: {args.file}")
        success = send_file_via_scp(args.file, config)

    # Tự động lưu cấu hình nếu gửi thành công và có IP
    if success and config["host"]:
        save_config(config)

    sys.exit(0 if success else 1)


# --- GUI Mode (Tkinter) ---

class FileTransferApp:
    """Giao diện đồ họa Tkinter cho File Transfer"""

    def __init__(self, root):
        self.root = root
        self.root.title("File Transfer - Gửi file tới Termux")
        self.root.geometry("620x520")
        self.root.resizable(False, False)

        # Đặt icon nếu có
        try:
            self.root.iconbitmap(default="")
        except Exception:
            pass

        self.config = load_config()
        self.selected_file = None
        self.transferring = False

        # Tạo giao diện
        self._build_ui()

        # Đóng kết nối khi thoát
        self.root.protocol("WM_DELETE_WINDOW", self._on_close)

    def _build_ui(self):
        """Xây dựng giao diện người dùng"""
        main_frame = ttk.Frame(self.root, padding="15")
        main_frame.pack(fill="both", expand=True)

        # --- Tiêu đề ---
        title_label = ttk.Label(
            main_frame,
            text="Truyền file qua mạng nội bộ (Wi-Fi Hotspot / LAN)",
            font=("Arial", 11, "bold"),
        )
        title_label.pack(pady=(0, 10))

        # --- Khung cấu hình SSH ---
        config_frame = ttk.LabelFrame(main_frame, text="Cấu hình kết nối Termux", padding="10")
        config_frame.pack(fill="x", pady=5)

        grid = ttk.Frame(config_frame)
        grid.pack(fill="x")

        # Hàng 1: IP và Port
        ttk.Label(grid, text="IP Termux:").grid(row=0, column=0, sticky="w", padx=(0, 5), pady=3)
        self.ip_var = tk.StringVar(value=self.config.get("host", ""))
        ttk.Entry(grid, textvariable=self.ip_var, width=22).grid(row=0, column=1, padx=5, pady=3)

        ttk.Label(grid, text="Port:").grid(row=0, column=2, sticky="w", padx=(10, 5), pady=3)
        self.port_var = tk.StringVar(value=str(self.config.get("port", 8022)))
        ttk.Entry(grid, textvariable=self.port_var, width=8).grid(row=0, column=3, padx=5, pady=3)

        # Hàng 2: Username và Password
        ttk.Label(grid, text="Username:").grid(row=1, column=0, sticky="w", padx=(0, 5), pady=3)
        self.user_var = tk.StringVar(value=self.config.get("username", ""))
        ttk.Entry(grid, textvariable=self.user_var, width=22).grid(row=1, column=1, padx=5, pady=3)

        ttk.Label(grid, text="Password:").grid(row=1, column=2, sticky="w", padx=(10, 5), pady=3)
        self.pass_var = tk.StringVar(value=self.config.get("password", ""))
        pass_entry = ttk.Entry(grid, textvariable=self.pass_var, width=12, show="*")
        pass_entry.grid(row=1, column=3, padx=5, pady=3)

        # Hàng 3: Remote path
        ttk.Label(grid, text="Thư mục đích:").grid(row=2, column=0, sticky="w", padx=(0, 5), pady=3)
        self.remote_var = tk.StringVar(
            value=self.config.get("remote_path", "~/storage/downloads/FileTransfer")
        )
        ttk.Entry(grid, textvariable=self.remote_var, width=40).grid(
            row=2, column=1, columnspan=3, sticky="ew", padx=5, pady=3
        )

        # --- Khung chọn file ---
        file_frame = ttk.LabelFrame(main_frame, text="Chọn file để gửi", padding="10")
        file_frame.pack(fill="x", pady=5)

        file_grid = ttk.Frame(file_frame)
        file_grid.pack(fill="x")

        self.file_path_var = tk.StringVar(value="(chưa chọn file)")
        ttk.Label(file_grid, textvariable=self.file_path_var, foreground="gray").pack(
            side="left", fill="x", expand=True, padx=(0, 10)
        )
        ttk.Button(file_grid, text="Chọn file...", command=self._browse_file).pack(side="right")

        # --- Thanh tiến trình ---
        progress_frame = ttk.LabelFrame(main_frame, text="Tiến trình gửi", padding="10")
        progress_frame.pack(fill="x", pady=5)

        self.progress_bar = ttk.Progressbar(progress_frame, mode="determinate")
        self.progress_bar.pack(fill="x")

        self.status_var = tk.StringVar(value="Sẵn sàng")
        ttk.Label(progress_frame, textvariable=self.status_var, font=("Arial", 9)).pack(
            anchor="w", pady=(5, 0)
        )

        # --- Nút gửi ---
        btn_frame = ttk.Frame(main_frame)
        btn_frame.pack(fill="x", pady=(10, 0))

        self.send_btn = ttk.Button(
            btn_frame,
            text="GỬI FILE",
            command=self._send_file,
            style="Accent.TButton",
        )
        self.send_btn.pack(side="left", padx=(0, 10))

        self.save_btn = ttk.Button(
            btn_frame, text="Lưu cấu hình", command=self._save_config
        )
        self.save_btn.pack(side="left", padx=5)

        ttk.Button(btn_frame, text="Thoát", command=self._on_close).pack(side="right")

        # Style cho nút chính
        style = ttk.Style()
        style.configure("Accent.TButton", font=("Arial", 10, "bold"))

    def _browse_file(self):
        """Mở hộp thoại chọn file"""
        file_path = filedialog.askopenfilename(
            title="Chọn file để gửi tới Termux",
            filetypes=[
                ("Tất cả file", "*.*"),
                ("Hình ảnh", "*.jpg *.jpeg *.png *.gif *.bmp"),
                ("File nén", "*.zip *.rar *.7z *.tar.gz"),
                ("Tài liệu", "*.pdf *.doc *.docx *.xlsx *.txt"),
            ],
        )
        if file_path:
            self.selected_file = file_path
            file_name = os.path.basename(file_path)
            file_size = os.path.getsize(file_path)
            size_str = self._format_size(file_size)
            self.file_path_var.set(f"{file_name} ({size_str})")
            self.status_var.set(f"Đã chọn: {file_name}")

    def _format_size(self, size_bytes):
        """Định dạng kích thước file"""
        if size_bytes < 1024:
            return f"{size_bytes} B"
        elif size_bytes < 1024 * 1024:
            return f"{size_bytes / 1024:.1f} KB"
        elif size_bytes < 1024 * 1024 * 1024:
            return f"{size_bytes / 1024 / 1024:.1f} MB"
        else:
            return f"{size_bytes / 1024 / 1024 / 1024:.2f} GB"

    def _update_progress(self, sent, total):
        """Callback cập nhật thanh tiến trình"""
        percent = int(sent / total * 100) if total > 0 else 0
        self.progress_bar["value"] = percent
        sent_str = self._format_size(sent)
        total_str = self._format_size(total)
        self.status_var.set(f"Đang gửi... {percent}% ({sent_str} / {total_str})")
        self.root.update_idletasks()

    def _save_config(self):
        """Lưu cấu hình hiện tại"""
        self.config["host"] = self.ip_var.get().strip()
        self.config["port"] = int(self.port_var.get().strip() or 8022)
        self.config["username"] = self.user_var.get().strip()
        self.config["password"] = self.pass_var.get()
        self.config["remote_path"] = self.remote_var.get().strip()
        save_config(self.config)
        messagebox.showinfo("Đã lưu", "Cấu hình đã được lưu thành công!")

    def _send_file(self):
        """Xử lý gửi file"""
        if self.transferring:
            return

        # Kiểm tra file đã chọn chưa
        if not self.selected_file or not os.path.exists(self.selected_file):
            messagebox.showerror("Lỗi", "Vui lòng chọn file trước khi gửi.")
            return

        # Lấy cấu hình từ giao diện
        config = {
            "host": self.ip_var.get().strip(),
            "port": int(self.port_var.get().strip() or 8022),
            "username": self.user_var.get().strip(),
            "password": self.pass_var.get(),
            "remote_path": self.remote_var.get().strip(),
            "use_key": False,
            "key_path": "",
        }

        if not config["host"]:
            messagebox.showerror("Lỗi", "Vui lòng nhập địa chỉ IP của Termux.")
            return

        if not config["username"]:
            messagebox.showerror("Lỗi", "Vui lòng nhập Username Termux.")
            return

        # Vô hiệu hóa nút gửi
        self.transferring = True
        self.send_btn.config(state="disabled")
        self.progress_bar["value"] = 0
        self.status_var.set("Đang kết nối...")
        self.root.update_idletasks()

        # Gửi file trong luồng chính (có callback)
        try:
            success = send_file_via_scp(self.selected_file, config, self._update_progress)
            if success:
                self.status_var.set("Hoàn tất! File đã được gửi thành công.")
                messagebox.showinfo("Thành công", "File đã được gửi tới Termux!")
                # Tự động lưu cấu hình
                self._save_config()
            else:
                self.status_var.set("Lỗi: Gửi file thất bại.")
        except Exception as e:
            self.status_var.set(f"Lỗi: {str(e)}")
            messagebox.showerror("Lỗi", f"Gửi file thất bại:\n{str(e)}")
        finally:
            self.transferring = False
            self.send_btn.config(state="normal")

    def _on_close(self):
        """Xử lý khi đóng cửa sổ"""
        self.root.destroy()


# --- Entry Point ---

def main():
    parser = argparse.ArgumentParser(
        description="File Transfer - Gửi file từ Máy tính tới Termux (Android)",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Ví dụ:
  python main.py --gui                          # Mở giao diện đồ họa
  python main.py --file photo.jpg               # Gửi file với cấu hình đã lưu
  python main.py --file data.zip --ip 192.168.1.100  # Gửi file tới IP cụ thể
  python main.py --file doc.pdf --ip 10.0.2.15 --port 8022 --user u0_a123
  python main.py --file video.mp4 --ip 192.168.1.x --port 8080 --http  # Gửi qua HTTP tới APK
        """,
    )
    parser.add_argument("--gui", action="store_true", help="Mở giao diện đồ họa (Tkinter)")
    parser.add_argument("--file", "-f", type=str, help="Đường dẫn file cần gửi")
    parser.add_argument("--ip", type=str, help="Địa chỉ IP của Termux hoặc APK")
    parser.add_argument("--port", type=int, help="Cổng SSH (mặc định: 8022) hoặc HTTP (mặc định: 8080)")
    parser.add_argument("--user", "-u", type=str, help="Username Termux")
    parser.add_argument("--password", "-p", type=str, help="Mật khẩu SSH")
    parser.add_argument("--remote", "-r", type=str, help="Thư mục đích trên Termux")
    parser.add_argument("--http", action="store_true", help="Dùng HTTP thay vì SSH/SCP (dùng với APK)")

    args = parser.parse_args()

    # Ưu tiên GUI nếu được yêu cầu hoặc không có đối số
    if args.gui or (len(sys.argv) == 1):
        try:
            root = tk.Tk()
            app = FileTransferApp(root)
            root.mainloop()
        except ImportError:
            print("[!] Tkinter không khả dụng. Chuyển sang chế độ CLI.")
            print("    Cài đặt: sudo apt install python3-tk (Linux)")
            print("    Hoặc cài Python kèm Tkinter trên Windows.")
            cli_mode(args)
    else:
        cli_mode(args)


if __name__ == "__main__":
    main()
