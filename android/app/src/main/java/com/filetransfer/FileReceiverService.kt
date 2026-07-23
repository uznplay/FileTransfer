package com.filetransfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import java.io.File

/**
 * Foreground Service giữ HTTP server chạy nền.
 * - Chạy server trên port 8080 (mặc định)
 * - Hiển thị notification để tránh bị Android kill
 * - Giữ WakeLock để server không tắt khi màn hình tối
 */
class FileReceiverService : Service() {

    companion object {
        private const val TAG = "FileReceiverService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "file_transfer_server"
        private const val DEFAULT_PORT = 8080

        // Intent actions
        const val ACTION_START = "com.filetransfer.START_SERVER"
        const val ACTION_STOP = "com.filetransfer.STOP_SERVER"
        const val ACTION_UPDATE = "com.filetransfer.UPDATE_STATUS"

        // Extras
        const val EXTRA_PORT = "port"
        const val EXTRA_STATUS = "status"
        const val EXTRA_IP = "ip_address"
        const val EXTRA_FILE_PATH = "file_path"
        const val EXTRA_FILE_SIZE = "file_size"

        /**
         * Kiểm tra server đang chạy hay không.
         */
        var isRunning = false
            private set

        private var serverInstance: FileUploadServer? = null
        private var wakeLock: PowerManager.WakeLock? = null
    }

    private lateinit var saveDir: File
    private var port = DEFAULT_PORT

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        saveDir = getSaveDirectory()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                port = intent.getIntExtra(EXTRA_PORT, DEFAULT_PORT)
                startServer()
            }
            ACTION_STOP -> {
                stopServer()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    /**
     * Khởi động HTTP server.
     */
    private fun startServer() {
        if (isRunning) {
            Log.d(TAG, "Server đã đang chạy, bỏ qua.")
            return
        }

        try {
            // Tạo server
            val server = FileUploadServer(
                port = port,
                saveDir = saveDir,
                onFileReceived = { path, size ->
                    onFileReceived(path, size)
                }
            )
            server.start()

            serverInstance = server
            isRunning = true

            // Giữ WakeLock để server không tắt
            acquireWakeLock()

            // Lấy IP
            val ip = server.getLocalIpAddress() ?: "unknown"

            // Hiển thị notification
            startForeground(NOTIFICATION_ID, createNotification(ip, port))

            // Gửi broadcast cập nhật UI
            sendStatusBroadcast(true, ip)

            Log.i(TAG, "Server đã khởi động trên port $port, IP: $ip")

        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khởi động server: ${e.message}", e)
            isRunning = false
            stopSelf()
        }
    }

    /**
     * Dừng HTTP server.
     */
    private fun stopServer() {
        try {
            serverInstance?.stop()
            serverInstance = null
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi dừng server: ${e.message}")
        }

        isRunning = false
        releaseWakeLock()

        // Cập nhật notification
        stopForeground(STOP_FOREGROUND_REMOVE)

        // Gửi broadcast cập nhật UI
        sendStatusBroadcast(false, "")

        Log.i(TAG, "Server đã dừng.")
    }

    /**
     * Callback khi có file mới được nhận.
     */
    private fun onFileReceived(path: String, size: Long) {
        val fileName = File(path).name
        val sizeStr = formatSize(size)
        Log.i(TAG, "File nhận: $fileName ($sizeStr)")

        // Cập nhật notification với thông tin file vừa nhận
        val notification = createNotification(
            ip = serverInstance?.getLocalIpAddress() ?: "unknown",
            port = port,
            lastFile = fileName
        )
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)

        // Gửi broadcast
        val intent = Intent(ACTION_UPDATE).apply {
            putExtra(EXTRA_STATUS, "Đã nhận: $fileName ($sizeStr)")
            putExtra(EXTRA_FILE_PATH, path)
            putExtra(EXTRA_FILE_SIZE, size)
        }
        sendBroadcast(intent)
    }

    /**
     * Tạo notification channel (Android 8+).
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "File Transfer Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Thông báo trạng thái server nhận file"
                setShowBadge(false)
            }
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Tạo notification hiển thị.
     */
    private fun createNotification(ip: String, port: Int, lastFile: String? = null): Notification {
        val stopIntent = Intent(this, FileReceiverService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("File Transfer Server")
            .setContentText("http://$ip:$port")
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dừng", stopPendingIntent)

        if (lastFile != null) {
            builder.setSubText("Mới nhất: $lastFile")
        }

        return builder.build()
    }

    /**
     * Lấy thư mục lưu file trên bộ nhớ ngoài.
     */
    private fun getSaveDirectory(): File {
        val dir = File(
            getExternalFilesDir(null)?.parentFile?.parentFile,  // /storage/emulated/0/Android
            "Download/FileTransfer"
        )
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Giữ WakeLock để CPU không ngủ.
     */
    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "FileTransfer:ServerWakeLock"
            )
            wakeLock?.acquire(4 * 60 * 60 * 1000L) // Tối đa 4 giờ
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    /**
     * Gửi broadcast cập nhật trạng thái cho Activity.
     */
    private fun sendStatusBroadcast(running: Boolean, ip: String) {
        val intent = Intent(ACTION_UPDATE).apply {
            putExtra(EXTRA_STATUS, if (running) "running" else "stopped")
            putExtra(EXTRA_IP, ip)
            putExtra(EXTRA_PORT, port)
        }
        sendBroadcast(intent)
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
            else -> "${"%.2f".format(bytes.toDouble() / (1024 * 1024 * 1024))} GB"
        }
    }
}
