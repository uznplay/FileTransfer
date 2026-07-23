package com.filetransfer

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.filetransfer.databinding.ActivityMainBinding

/**
 * Màn hình chính - Quản lý server nhận file.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isServerRunning = false
    private var currentIp = ""

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getStringExtra(FileReceiverService.EXTRA_STATUS) ?: return
            when (status) {
                "running" -> {
                    isServerRunning = true
                    currentIp = intent.getStringExtra(FileReceiverService.EXTRA_IP) ?: ""
                    val port = intent.getIntExtra(FileReceiverService.EXTRA_PORT, 8080)
                    updateUI(true, currentIp, port)
                }
                "stopped" -> {
                    isServerRunning = false
                    currentIp = ""
                    updateUI(false, "", 0)
                }
                else -> {
                    // Thông báo file mới
                    binding.tvLastFile.text = status
                }
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            requestStorageAccess()
        } else {
            Toast.makeText(this, "Cần quyền bộ nhớ để lưu file", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Đăng ký receiver nhận cập nhật từ service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                statusReceiver,
                IntentFilter(FileReceiverService.ACTION_UPDATE),
                RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(
                statusReceiver,
                IntentFilter(FileReceiverService.ACTION_UPDATE)
            )
        }

        setupClickListeners()
        checkPermissionsAndStart()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(statusReceiver)
    }

    private fun setupClickListeners() {
        binding.btnToggle.setOnClickListener {
            if (isServerRunning) {
                stopServer()
            } else {
                checkPermissionsAndStart()
            }
        }

        binding.btnOpenFolder.setOnClickListener {
            openDownloadFolder()
        }

        binding.btnChangePort.setOnClickListener {
            showPortDialog()
        }
    }

    /**
     * Kiểm tra và yêu cầu quyền.
     */
    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf<String>()

        // Quyền thông báo (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Quyền đọc file (Android 10-)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        } else {
            requestStorageAccess()
        }
    }

    /**
     * Yêu cầu quyền MANAGE_EXTERNAL_STORAGE (Android 11+).
     */
    private fun requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                startServer()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Cần quyền truy cập bộ nhớ")
                    .setMessage("Ứng dụng cần quyền quản lý file để lưu file vào thư mục Downloads.")
                    .setPositiveButton("Cấp quyền") { _, _ ->
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                    }
                    .setNegativeButton("Để sau") { _, _ -> }
                    .show()
            }
        } else {
            startServer()
        }
    }

    /**
     * Khởi động server.
     */
    private fun startServer() {
        if (isServerRunning) return

        // Tắt chế độ tiết kiệm pin
        disableBatteryOptimization()

        val port = getPortFromPreferences()

        val intent = Intent(this, FileReceiverService::class.java).apply {
            action = FileReceiverService.ACTION_START
            putExtra(FileReceiverService.EXTRA_PORT, port)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    /**
     * Dừng server.
     */
    private fun stopServer() {
        val intent = Intent(this, FileReceiverService::class.java).apply {
            action = FileReceiverService.ACTION_STOP
        }
        startService(intent)
    }

    /**
     * Cập nhật giao diện theo trạng thái server.
     */
    private fun updateUI(running: Boolean, ip: String, port: Int) {
        isServerRunning = running
        if (running) {
            binding.tvStatus.text = "Server đang chạy"
            binding.tvStatus.setTextColor(
                ContextCompat.getColor(this, android.R.color.holo_green_dark)
            )
            binding.tvIpAddress.text = "http://$ip:$port"
            binding.btnToggle.text = "Dừng server"
            binding.btnToggle.setBackgroundColor(
                ContextCompat.getColor(this, android.R.color.holo_red_light)
            )
            binding.indicatorRunning.setImageResource(android.R.drawable.presence_online)
        } else {
            binding.tvStatus.text = "Server đã dừng"
            binding.tvStatus.setTextColor(
                ContextCompat.getColor(this, android.R.color.black)
            )
            binding.tvIpAddress.text = "---"
            binding.btnToggle.text = "Khởi động server"
            binding.btnToggle.setBackgroundColor(
                ContextCompat.getColor(this, android.R.color.holo_blue_light)
            )
            binding.indicatorRunning.setImageResource(android.R.drawable.presence_offline)
        }
    }

    /**
     * Mở thư mục chứa file đã nhận.
     */
    private fun openDownloadFolder() {
        val downloadDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )
        val folder = Uri.parse(downloadDir.absolutePath + "/FileTransfer")
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(folder, "resource/folder")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Không thể mở thư mục", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Hộp thoại đổi port.
     */
    private fun showPortDialog() {
        val currentPort = getPortFromPreferences()
        val input = android.widget.EditText(this).apply {
            setText(currentPort.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            selectAll()
        }

        AlertDialog.Builder(this)
            .setTitle("Đổi cổng (Port)")
            .setMessage("Nhập số port (1024-65535):")
            .setView(input)
            .setPositiveButton("Lưu") { _, _ ->
                val port = input.text.toString().toIntOrNull() ?: 8080
                if (port in 1024..65535) {
                    getPreferences(MODE_PRIVATE).edit().putInt("server_port", port).apply()
                    Toast.makeText(this, "Port đã lưu: $port (cần khởi động lại server)", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Port không hợp lệ", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun getPortFromPreferences(): Int {
        return getPreferences(MODE_PRIVATE).getInt("server_port", 8080)
    }

    /**
     * Yêu cầu tắt tối ưu pin cho app.
     */
    private fun disableBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                AlertDialog.Builder(this)
                    .setTitle("Tối ưu pin")
                    .setMessage("Để server chạy ổn định, vui lòng tắt tối ưu pin cho ứng dụng này.")
                    .setPositiveButton("Cài đặt") { _, _ ->
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                    }
                    .setNegativeButton("Để sau", null)
                    .show()
            }
        }
    }
}
