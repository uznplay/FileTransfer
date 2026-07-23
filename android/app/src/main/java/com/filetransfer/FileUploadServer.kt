package com.filetransfer

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileOutputStream
import java.net.NetworkInterface

/**
 * HTTP Server nhận file upload từ máy tính.
 * Dùng thư viện NanoHTTPd - siêu nhẹ, chạy tốt trên Android.
 */
class FileUploadServer(
    port: Int,
    private val saveDir: File,
    private val onFileReceived: ((String, Long) -> Unit)? = null
) : NanoHTTPD(port) {

    companion object {
        private const val TAG = "FileUploadServer"
        private const val UPLOAD_URI = "/upload"
    }

    init {
        // Cho phép nhiều phần trong request (multipart/form-data)
        val tmpDir = File(saveDir, ".tmp")
        if (!tmpDir.exists()) tmpDir.mkdirs()
    }

    override fun serve(session: IHTTPSession): Response {
        return when (session.uri) {
            "/" -> handleIndex(session)
            UPLOAD_URI -> handleUpload(session)
            else -> newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "404 - Not Found"
            )
        }
    }

    /**
     * Trang chủ - hiển thị thông tin server và form upload.
     */
    private fun handleIndex(session: IHTTPSession): Response {
        val ip = getLocalIpAddress() ?: "unknown"
        val html = buildString {
            append("""<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>File Transfer - Android Receiver</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: -apple-system, 'Segoe UI', sans-serif; background: #f0f2f5; display: flex; justify-content: center; align-items: center; min-height: 100vh; padding: 20px; }
        .card { background: white; border-radius: 16px; box-shadow: 0 4px 24px rgba(0,0,0,0.1); padding: 32px; width: 100%; max-width: 480px; text-align: center; }
        h1 { color: #1a73e8; font-size: 24px; margin-bottom: 8px; }
        .status { color: #0f9d58; font-weight: 500; margin-bottom: 20px; }
        .info { background: #f8f9fa; border-radius: 10px; padding: 16px; margin-bottom: 24px; text-align: left; }
        .info div { padding: 4px 0; font-size: 14px; color: #444; }
        .info span { color: #1a73e8; font-weight: 600; }
        .drop-zone { border: 2px dashed #ccc; border-radius: 12px; padding: 32px 16px; cursor: pointer; transition: all 0.3s; margin-bottom: 16px; }
        .drop-zone:hover, .drop-zone.dragover { border-color: #1a73e8; background: #e8f0fe; }
        .drop-zone p { color: #666; font-size: 14px; }
        .drop-zone .icon { font-size: 40px; margin-bottom: 8px; }
        input[type=file] { display: none; }
        .btn { background: #1a73e8; color: white; border: none; padding: 14px 32px; border-radius: 8px; font-size: 16px; cursor: pointer; width: 100%; transition: background 0.2s; }
        .btn:hover { background: #1557b0; }
        .btn:disabled { background: #ccc; cursor: not-allowed; }
        #status { margin-top: 16px; padding: 12px; border-radius: 8px; display: none; font-size: 14px; }
        #status.success { display: block; background: #e6f4ea; color: #0f9d58; }
        #status.error { display: block; background: #fce8e6; color: #c5221f; }
        #status.loading { display: block; background: #e8f0fe; color: #1a73e8; }
        .progress-bar { width: 100%; height: 6px; background: #e0e0e0; border-radius: 3px; margin-top: 12px; overflow: hidden; display: none; }
        .progress-bar .fill { height: 100%; background: #1a73e8; width: 0%; transition: width 0.3s; }
        .progress-bar.active { display: block; }
    </style>
</head>
<body>
    <div class="card">
        <h1>📁 File Transfer</h1>
        <div class="status">✅ Server đang chạy</div>
        <div class="info">
            <div>📡 Địa chỉ: <span>http://$ip:$port</span></div>
            <div>📁 Lưu vào: <span>${saveDir.name}</span></div>
        </div>
        <form id="uploadForm" enctype="multipart/form-data">
            <div class="drop-zone" id="dropZone">
                <div class="icon">📤</div>
                <p><strong>Nhấp để chọn file</strong> hoặc kéo thả vào đây</p>
                <input type="file" name="file" id="fileInput" required>
            </div>
            <button type="submit" class="btn" id="submitBtn">Gửi file lên điện thoại</button>
        </form>
        <div id="status"></div>
        <div class="progress-bar" id="progressBar">
            <div class="fill" id="progressFill"></div>
        </div>
        <p style="margin-top: 16px; font-size: 12px; color: #999;">
            Kết nối qua mạng nội bộ &bull; Mã hóa đầu cuối
        </p>
    </div>
    <script>
        const dropZone = document.getElementById('dropZone');
        const fileInput = document.getElementById('fileInput');
        const form = document.getElementById('uploadForm');
        const statusDiv = document.getElementById('status');
        const submitBtn = document.getElementById('submitBtn');
        const progressBar = document.getElementById('progressBar');
        const progressFill = document.getElementById('progressFill');

        dropZone.addEventListener('click', () => fileInput.click());
        dropZone.addEventListener('dragover', (e) => { e.preventDefault(); dropZone.classList.add('dragover'); });
        dropZone.addEventListener('dragleave', () => dropZone.classList.remove('dragover'));
        dropZone.addEventListener('drop', (e) => {
            e.preventDefault();
            dropZone.classList.remove('dragover');
            if (e.dataTransfer.files.length) {
                fileInput.files = e.dataTransfer.files;
                const name = e.dataTransfer.files[0].name;
                dropZone.querySelector('p').innerHTML = '<strong>' + name + '</strong>';
            }
        });
        fileInput.addEventListener('change', () => {
            if (fileInput.files.length) {
                dropZone.querySelector('p').innerHTML = '<strong>' + fileInput.files[0].name + '</strong>';
            }
        });

        form.addEventListener('submit', async (e) => {
            e.preventDefault();
            const file = fileInput.files[0];
            if (!file) { showStatus('Vui lòng chọn file', 'error'); return; }

            submitBtn.disabled = true;
            submitBtn.textContent = 'Đang gửi...';
            showStatus('Đang gửi ' + file.name + '...', 'loading');
            progressBar.classList.add('active');

            const formData = new FormData();
            formData.append('file', file);

            try {
                const xhr = new XMLHttpRequest();
                xhr.open('POST', '/upload', true);

                xhr.upload.onprogress = (e) => {
                    if (e.lengthComputable) {
                        const pct = (e.loaded / e.total) * 100;
                        progressFill.style.width = pct + '%';
                    }
                };

                xhr.onload = () => {
                    if (xhr.status === 200) {
                        showStatus('✅ ' + xhr.responseText, 'success');
                    } else {
                        showStatus('❌ Lỗi: ' + xhr.responseText, 'error');
                    }
                    submitBtn.disabled = false;
                    submitBtn.textContent = 'Gửi file lên điện thoại';
                    setTimeout(() => progressBar.classList.remove('active'), 2000);
                };

                xhr.onerror = () => {
                    showStatus('❌ Lỗi kết nối tới server', 'error');
                    submitBtn.disabled = false;
                    submitBtn.textContent = 'Gửi file lên điện thoại';
                };

                xhr.send(formData);
            } catch (err) {
                showStatus('❌ Lỗi: ' + err.message, 'error');
                submitBtn.disabled = false;
                submitBtn.textContent = 'Gửi file lên điện thoại';
            }
        });

        function showStatus(msg, type) {
            statusDiv.textContent = msg;
            statusDiv.className = type;
        }
    </script>
</body>
</html>""")
        }
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=UTF-8", html)
    }

    /**
     * Xử lý upload file - nhận multipart/form-data và lưu vào bộ nhớ.
     */
    private fun handleUpload(session: IHTTPSession): Response {
        try {
            // Parse multipart data
            val files = HashMap<String, String>()
            session.parseBody(files)

            // Lấy file từ request
            val fileParam = files["file"] ?: return newFixedLengthResponse(
                Response.Status.BAD_REQUEST, MIME_PLAINTEXT,
                "Không tìm thấy file trong request"
            )

            val tempFile = File(fileParam)
            if (!tempFile.exists()) {
                return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST, MIME_PLAINTEXT,
                    "File tạm không tồn tại"
                )
            }

            // Tạo thư mục lưu nếu chưa có
            if (!saveDir.exists()) saveDir.mkdirs()

            // Lấy tên file gốc từ headers hoặc dùng tên file tạm
            val disposition = session.headers["content-disposition"]
            val originalName = extractFileName(disposition) ?: tempFile.name

            // Tạo file đích (tránh trùng tên)
            val destFile = getUniqueFile(File(saveDir, originalName))

            // Copy file tạm vào thư mục đích
            tempFile.copyTo(destFile, overwrite = true)

            // Xóa file tạm
            tempFile.delete()

            val fileSize = destFile.length()
            Log.i(TAG, "Đã nhận file: ${destFile.name} (${fileSize} bytes)")

            // Callback thông báo
            onFileReceived?.invoke(destFile.absolutePath, fileSize)

            return newFixedLengthResponse(
                Response.Status.OK, MIME_PLAINTEXT,
                "Đã nhận file: ${destFile.name} (${formatSize(fileSize)})"
            )

        } catch (e: Exception) {
            Log.e(TAG, "Lỗi xử lý upload: ${e.message}", e)
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT,
                "Lỗi server: ${e.message}"
            )
        }
    }

    /**
     * Trích xuất tên file từ Content-Disposition header.
     */
    private fun extractFileName(disposition: String?): String? {
        if (disposition == null) return null
        val regex = """filename\s*=\s*["']?([^"'\r\n]+)["']?""".toRegex()
        val match = regex.find(disposition)
        return match?.groupValues?.get(1)
    }

    /**
     * Tạo tên file không trùng lặp bằng cách thêm số vào cuối.
     */
    private fun getUniqueFile(file: File): File {
        if (!file.exists()) return file
        val name = file.nameWithoutExtension
        val ext = file.extension
        var counter = 1
        var newFile: File
        do {
            newFile = File(file.parent, "${name}($counter).${ext}")
            counter++
        } while (newFile.exists())
        return newFile
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
            else -> "${"%.2f".format(bytes.toDouble() / (1024 * 1024 * 1024))} GB"
        }
    }

    /**
     * Lấy địa chỉ IP cục bộ trên mạng WiFi.
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                // Chỉ lấy giao diện WiFi hoặc Ethernet (không lấy lo, docker...)
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val name = networkInterface.name.lowercase()
                if (name.contains("wlan") || name.contains("eth") || name.contains("rndis")) {
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                            return addr.hostAddress
                        }
                    }
                }
            }
            // Fallback: lấy IPv4 đầu tiên không loopback
            val interfaces2 = NetworkInterface.getNetworkInterfaces()
            while (interfaces2.hasMoreElements()) {
                val networkInterface = interfaces2.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi lấy địa chỉ IP: ${e.message}")
        }
        return null
    }
}
