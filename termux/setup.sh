#!/data/data/com.termux/files/usr/bin/bash
# ==============================================================
# setup.sh - Script thiết lập Termux làm Receiver cho File Transfer
# ==============================================================
# Script này tự động:
#   1. Cấp quyền truy cập bộ nhớ điện thoại
#   2. Cập nhật gói & cài đặt openssh
#   3. Thiết lập SSH password
#   4. Khởi động sshd trên port 8022
#   5. Hiển thị địa chỉ IP cục bộ để nhập trên Desktop
# ==============================================================

set -e  # Dừng nếu có lỗi

# --- Màu sắc cho terminal ---
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${CYAN}============================================${NC}"
echo -e "${CYAN}   TERMUX FILE TRANSFER - SETUP RECEIVER    ${NC}"
echo -e "${CYAN}============================================${NC}"
echo ""

# --- Bước 1: Cấp quyền bộ nhớ ---
echo -e "${YELLOW}[1/5] Cấp quyền truy cập bộ nhớ...${NC}"
termux-setup-storage
echo -e "${GREEN}  => Hoàn tất.${NC}"
echo ""

# --- Bước 2: Cập nhật gói ---
echo -e "${YELLOW}[2/5] Cập nhật danh sách gói...${NC}"
pkg update -y
echo -e "${GREEN}  => Hoàn tất.${NC}"
echo ""

# --- Bước 3: Cài đặt openssh và termux-api ---
echo -e "${YELLOW}[3/6] Cài đặt OpenSSH Server + Termux-API...${NC}"
pkg install openssh termux-api -y
echo -e "${GREEN}  => Hoàn tất.${NC}"
echo ""

# --- Bước 4: Thiết lập mật khẩu cho SSH ---
echo -e "${YELLOW}[4/6] Thiết lập mật khẩu SSH...${NC}"
echo -e "${CYAN}  Nhập mật khẩu bạn muốn đặt (để trống = mặc định 'termux'):${NC}"
read -s USER_PASSWORD
USER_PASSWORD="${USER_PASSWORD:-termux}"
echo "$USER_PASSWORD" | passwd --stdin 2>/dev/null || echo -e "$USER_PASSWORD\n$USER_PASSWORD" | passwd
echo -e "${GREEN}  => Mật khẩu SSH đã được thiết lập.${NC}"
echo ""

# --- Bước 5: Khởi động SSH Server ---
echo -e "${YELLOW}[5/6] Khởi động SSH Server...${NC}"

# Kill sshd nếu đang chạy để restart
pkill -x sshd 2>/dev/null || true

# Khởi động sshd trên port 8022
sshd -p 8022
echo -e "${GREEN}  => SSH Server đã chạy trên port 8022.${NC}"
echo ""

# --- Bước 6: Tạo thư mục đích và hiển thị thông tin ---
echo -e "${YELLOW}[6/6] Hoàn tất thiết lập...${NC}"

# --- Tạo thư mục đích để nhận file ---
DEST_DIR="$HOME/storage/downloads/FileTransfer"
mkdir -p "$DEST_DIR"
echo -e "${GREEN}  File nhận sẽ được lưu tại: $DEST_DIR${NC}"
echo ""

# --- Hiển thị thông tin kết nối ---
echo -e "${CYAN}============================================${NC}"
echo -e "${GREEN}  THIẾT LẬP HOÀN TẤT!${NC}"
echo -e "${CYAN}============================================${NC}"
echo ""

# Lấy địa chỉ IP cục bộ
IP_ADDR=$(ifconfig 2>/dev/null | grep -Eo 'inet (addr:)?([0-9]*\.){3}[0-9]*' | grep -Eo '([0-9]*\.){3}[0-9]*' | grep -v '127.0.0.1' | head -1)

if [ -z "$IP_ADDR" ]; then
    IP_ADDR=$(ip -4 addr show 2>/dev/null | grep -oP '(?<=inet\s)\d+(\.\d+){3}' | grep -v '127.0.0.1' | head -1)
fi

echo -e "  Địa chỉ IP Termux:  ${CYAN}${IP_ADDR:-KHÔNG XÁC ĐỊNH}${NC}"
echo -e "  SSH Port:           ${CYAN}8022${NC}"
echo -e "  Username:           ${CYAN}$(whoami)${NC}"
echo -e "  Mật khẩu:           ${CYAN}(đã nhập ở bước 4)${NC}"
echo ""
echo -e "  Tính năng:"
echo -e "  ${GREEN}✓${NC} Gửi file qua SSH/SCP"
echo -e "  ${GREEN}✓${NC} Gửi tin nhắn kèm file (hiện notification)"
echo ""
echo -e "${YELLOW}  => Trên Desktop, hãy nhập các thông tin trên vào ứng dụng.${NC}"
echo ""

# --- Giữ sshd chạy nền ---
echo -e "${CYAN}SSHD đang chạy ngầm. Đóng terminal này sẽ KHÔNG tắt SSHD.${NC}"
echo -e "${CYAN}Để dừng SSHD, gõ lệnh: pkill sshd${NC}"
echo ""
