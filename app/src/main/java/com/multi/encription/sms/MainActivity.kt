package com.multi.encription.sms

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.multi.encription.sms.core.SmsManager
import com.multi.encription.sms.database.SmsDatabase
import com.multi.encription.sms.service.SmsGatewayService
import com.multi.encription.sms.ui.SmsHistoryAdapter
import com.multi.encription.sms.utils.ConfigManager
import com.multi.encription.sms.utils.PermissionHelper
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var configManager: ConfigManager
    private lateinit var smsManager: SmsManager
    private lateinit var database: SmsDatabase
    private lateinit var smsHistoryAdapter: SmsHistoryAdapter

    // UI Components
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var statusDot: View
    private lateinit var serverStatusText: TextView
    private lateinit var serverToggle: SwitchMaterial
    private lateinit var baseUrlDisplay: TextView
    private lateinit var copyUrlButton: Button
    private lateinit var apiKeyText: TextView
    private lateinit var copyKeyButton: Button
    private lateinit var regenerateKeyButton: Button
    private lateinit var portText: TextView
    private lateinit var apiUrlsText: TextView
    private lateinit var testApiButton: Button
    private lateinit var openDashboardButton: Button
    private lateinit var externalDomainButton: Button
    private lateinit var historyCountBadge: TextView
    private lateinit var emptyHistoryLayout: LinearLayout
    private lateinit var smsHistoryRecyclerView: RecyclerView
    private lateinit var sendSmsFab: ExtendedFloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.appBarLayout)) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(0, statusBars.top, 0, 0)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.nestedScrollView)) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(0, 0, 0, navBars.bottom + 88)
            insets
        }

        initializeComponents()
        setupUI()
        checkPermissions()
    }

    private fun initializeComponents() {
        configManager = ConfigManager(this)
        smsManager = SmsManager(this)
        database = SmsDatabase.getDatabase(this)

        // Initialize UI components
        topAppBar = findViewById(R.id.topAppBar)
        statusDot = findViewById(R.id.statusDot)
        serverStatusText = findViewById(R.id.serverStatusText)
        serverToggle = findViewById(R.id.serverToggle)
        baseUrlDisplay = findViewById(R.id.baseUrlDisplay)
        copyUrlButton = findViewById(R.id.copyUrlButton)
        apiKeyText = findViewById(R.id.apiKeyText)
        copyKeyButton = findViewById(R.id.copyKeyButton)
        regenerateKeyButton = findViewById(R.id.regenerateKeyButton)
        portText = findViewById(R.id.portText)
        apiUrlsText = findViewById(R.id.apiUrlsText)
        testApiButton = findViewById(R.id.testApiButton)
        openDashboardButton = findViewById(R.id.openDashboardButton)
        externalDomainButton = findViewById(R.id.externalDomainButton)
        historyCountBadge = findViewById(R.id.historyCountBadge)
        emptyHistoryLayout = findViewById(R.id.emptyHistoryLayout)
        smsHistoryRecyclerView = findViewById(R.id.smsHistoryRecyclerView)
        sendSmsFab = findViewById(R.id.sendSmsFab)
    }

    private fun setupUI() {
        // Setup RecyclerView
        smsHistoryAdapter = SmsHistoryAdapter { sms ->
            // Handle SMS item click - show details
            showSmsDetails(sms)
        }

        smsHistoryRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = smsHistoryAdapter
        }

        // Observe SMS history & manage empty state
        database.smsDao().getAllSmsLiveData().observe(this) { smsList ->
            smsHistoryAdapter.submitList(smsList)
            if (smsList.isNullOrEmpty()) {
                emptyHistoryLayout.visibility = View.VISIBLE
                smsHistoryRecyclerView.visibility = View.GONE
                historyCountBadge.text = "0 sent"
            } else {
                emptyHistoryLayout.visibility = View.GONE
                smsHistoryRecyclerView.visibility = View.VISIBLE
                historyCountBadge.text = "${smsList.size} sent"
            }
        }

        // Setup server toggle
        serverToggle.isChecked = configManager.isServerEnabled
        serverToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !PermissionHelper.hasSmsPermissions(this)) {
                serverToggle.isChecked = false
                showRestrictedSettingsDialog()
                return@setOnCheckedChangeListener
            }
            configManager.isServerEnabled = isChecked
            if (isChecked) {
                SmsGatewayService.startService(this)
            } else {
                SmsGatewayService.stopService(this)
            }
            updateServerStatus()
            updateApiUrls()
        }

        if (configManager.isServerEnabled && PermissionHelper.hasSmsPermissions(this)) {
            SmsGatewayService.startService(this)
        }

        // Setup FAB for sending SMS
        sendSmsFab.setOnClickListener {
            showSendSmsDialog()
        }

        // Quick Copy Buttons
        copyUrlButton.setOnClickListener {
            val deviceIp = getDeviceIpAddress()
            val baseUrl = configManager.getApiBaseUrl(deviceIp)
            copyToClipboard("Base URL", baseUrl)
        }

        copyKeyButton.setOnClickListener {
            copyToClipboard("API Key", configManager.apiKey)
        }

        regenerateKeyButton.setOnClickListener {
            showApiKeyDialog()
        }

        openDashboardButton.setOnClickListener {
            val deviceIp = getDeviceIpAddress()
            val baseUrl = configManager.getApiBaseUrl(deviceIp)
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(baseUrl))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open browser: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // Setup click listeners for configuration
        apiKeyText.setOnClickListener { showApiKeyDialog() }
        portText.setOnClickListener { showPortDialog() }
        apiUrlsText.setOnClickListener { showApiUrlsDialog() }

        // Setup test API button
        testApiButton.setOnClickListener { showTestApiDialog() }

        // Setup external domain button
        externalDomainButton.setOnClickListener { showExternalDomainDialog() }

        // Initial UI displays
        updateApiKeyDisplay()
        updatePortDisplay()
        updateServerStatus()
        updateApiUrls()
        updateExternalDomainButton()
    }

    private fun checkPermissions() {
        if (!PermissionHelper.hasAllPermissions(this)) {
            if (PermissionHelper.shouldShowPermissionRationale(this)) {
                showPermissionRationaleDialog()
            } else {
                PermissionHelper.requestAllPermissions(this)
            }
        }
        checkBatteryOptimization()
    }

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                }
            } catch (e: Exception) {
                Log.d("MainActivity", "Battery optimization prompt skipped: ${e.message}")
            }
        }
    }

    private fun showPermissionRationaleDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("SMS Permission Required")
            .setMessage("This app requires SMS Send and Notification permissions to act as an OTP Gateway. Please grant SMS permissions.")
            .setPositiveButton("Grant Permissions") { _, _ ->
                PermissionHelper.requestAllPermissions(this)
            }
            .setNegativeButton("Help / Settings") { dialog, _ ->
                dialog.dismiss()
                showRestrictedSettingsDialog()
            }
            .show()
    }

    private fun showRestrictedSettingsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("⚠️ SMS Permission & Restricted Settings")
            .setMessage(
                "On modern Android (13/14/15), sideloaded apps require one manual step to allow SMS permissions:\n\n" +
                "1. Tap 'Open App Settings' below.\n" +
                "2. Tap the three dots (⋮) in the top-right corner.\n" +
                "3. Tap 'Allow restricted settings' (verify with your fingerprint/PIN).\n" +
                "4. Go to 'Permissions' ➔ 'SMS' ➔ select 'Allow'.\n\n" +
                "Once allowed, return to this app and enable the API server!"
            )
            .setPositiveButton("Open App Settings") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (PermissionHelper.hasSmsPermissions(this)) {
            if (!configManager.isServerEnabled) {
                configManager.isServerEnabled = true
                serverToggle.isChecked = true
                SmsGatewayService.startService(this)
            }
            updateServerStatus()
            updateApiUrls()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PermissionHelper.ALL_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "All gateway permissions granted!", Toast.LENGTH_SHORT).show()
                serverToggle.isChecked = true
                configManager.isServerEnabled = true
                SmsGatewayService.startService(this)
                updateServerStatus()
                updateApiUrls()
            } else {
                showRestrictedSettingsDialog()
            }
        }
    }

    private fun updateServerStatus() {
        val isRunning = configManager.isServerEnabled
        if (isRunning) {
            serverStatusText.text = "Server Active: Port ${configManager.serverPort}"
            statusDot.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.status_delivered))
            topAppBar.subtitle = "Online · Port ${configManager.serverPort}"
        } else {
            serverStatusText.text = "Server Stopped"
            statusDot.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.status_pending))
            topAppBar.subtitle = "Server Offline"
        }
    }

    private fun updateApiKeyDisplay() {
        val apiKey = configManager.apiKey
        val masked = if (apiKey.length > 14) "${apiKey.take(8)}...${apiKey.takeLast(4)}" else apiKey
        apiKeyText.text = "API Key: $masked"
    }

    private fun updatePortDisplay() {
        portText.text = "Port: ${configManager.serverPort} (tap to change)"
    }

    private fun updateApiUrls() {
        val deviceIp = getDeviceIpAddress()
        val baseUrl = configManager.getApiBaseUrl(deviceIp)
        baseUrlDisplay.text = baseUrl

        val urlsText = """
            POST $baseUrl/api/send
            Headers: X-API-Key: ${configManager.apiKey}
            Body: {"phone_number": "+1234567890", "message": "OTP: 123456"}
        """.trimIndent()

        apiUrlsText.text = urlsText
    }

    private fun getDeviceIpAddress(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (!networkInterface.isLoopback && networkInterface.isUp) {
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (!address.isLoopbackAddress && address.hostAddress?.contains(':') == false) {
                            return address.hostAddress ?: "192.168.1.100"
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback to a common local IP pattern
        }
        return "192.168.1.100" // Fallback IP
    }

    private fun showSendSmsDialog() {
        if (!PermissionHelper.hasSmsPermissions(this)) {
            showRestrictedSettingsDialog()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_send_sms, null)
        val phoneEditText = dialogView.findViewById<EditText>(R.id.phoneEditText)
        val messageEditText = dialogView.findViewById<EditText>(R.id.messageEditText)

        MaterialAlertDialogBuilder(this)
            .setTitle("Send SMS")
            .setView(dialogView)
            .setPositiveButton("Send") { _, _ ->
                val phone = phoneEditText.text.toString().trim()
                val message = messageEditText.text.toString().trim()

                if (phone.isBlank() || message.isBlank()) {
                    Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                sendSms(phone, message)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendSms(phoneNumber: String, message: String) {
        lifecycleScope.launch {
            try {
                val result = smsManager.sendSms(phoneNumber, message)
                if (result.isSuccess) {
                    Toast.makeText(this@MainActivity, "SMS queued for sending", Toast.LENGTH_SHORT).show()
                } else {
                    val error = result.exceptionOrNull()
                    Toast.makeText(this@MainActivity, "Failed to send SMS: ${error?.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showSmsDetails(sms: com.multi.encription.sms.database.SmsEntity) {
        val message = """
            Phone: ${sms.phoneNumber}
            Message: ${sms.message}
            Status: ${sms.status}
            Timestamp: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(sms.timestamp))}
            ${if (sms.deliveryTimestamp != null) "Delivered: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(sms.deliveryTimestamp))}" else ""}
            ${if (sms.errorMessage != null) "Error: ${sms.errorMessage}" else ""}
            ${if (sms.requestId != null) "Request ID: ${sms.requestId}" else ""}
        """.trimIndent()

        MaterialAlertDialogBuilder(this)
            .setTitle("SMS Details")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showApiKeyDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_api_key, null)
        val keyDisplay = dialogView.findViewById<TextView>(R.id.dialogApiKeyDisplay)
        keyDisplay.text = configManager.apiKey

        MaterialAlertDialogBuilder(this)
            .setTitle("API Authentication Key")
            .setView(dialogView)
            .setPositiveButton("Copy Key") { _, _ ->
                copyToClipboard("API Key", configManager.apiKey)
            }
            .setNeutralButton("Generate New") { _, _ ->
                val newApiKey = configManager.regenerateApiKey()
                updateApiKeyDisplay()
                updateApiUrls()
                Toast.makeText(this, "New API key generated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Done", null)
            .show()
    }

    private fun showPortDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_port, null)
        val portInput = dialogView.findViewById<EditText>(R.id.portInputEditText)
        portInput.setText(configManager.serverPort.toString())
        portInput.setSelection(portInput.text.length)

        MaterialAlertDialogBuilder(this)
            .setTitle("Server Port")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val port = portInput.text.toString().toIntOrNull()
                if (port != null && port in 1024..65535) {
                    configManager.serverPort = port
                    updatePortDisplay()
                    updateApiUrls()

                    if (configManager.isServerEnabled) {
                        // Restart server with new port
                        SmsGatewayService.stopService(this)
                        SmsGatewayService.startService(this)
                    }

                    Toast.makeText(this, "Port updated to $port", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Please enter a valid port number (1024-65535)", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showApiUrlsDialog() {
        val deviceIp = getDeviceIpAddress()
        val port = configManager.serverPort
        val apiKey = configManager.apiKey
        val baseUrl = "http://$deviceIp:$port"

        val message = """
            📡 SMS Gateway API Endpoints

            Base URL: $baseUrl
            API Key: $apiKey

            🔗 Available Endpoints:

            1. Send SMS:
            POST $baseUrl/api/send
            Headers: X-API-Key: $apiKey
            Body: {"phone_number": "+1234567890", "message": "Hello!"}

            2. Check SMS Status:
            GET $baseUrl/api/status?sms_id=123
            Headers: X-API-Key: $apiKey

            3. Get SMS History:
            GET $baseUrl/api/history?limit=10
            Headers: X-API-Key: $apiKey

            4. Server Info (No Auth):
            GET $baseUrl/api/info
        """.trimIndent()

        MaterialAlertDialogBuilder(this)
            .setTitle("API Endpoints")
            .setMessage(message)
            .setPositiveButton("Copy Base URL") { _, _ ->
                copyToClipboard("Base URL", baseUrl)
            }
            .setNeutralButton("Copy API Key") { _, _ ->
                copyToClipboard("API Key", apiKey)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showTestApiDialog() {
        if (!configManager.isServerEnabled) {
            Toast.makeText(this, "Please enable the API server first", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_test_api, null)
        val phoneEditText = dialogView.findViewById<EditText>(R.id.testPhoneEditText)
        val messageEditText = dialogView.findViewById<EditText>(R.id.testMessageEditText)

        phoneEditText.setText("+91")
        messageEditText.setText("Test message from SMS Gateway API")

        MaterialAlertDialogBuilder(this)
            .setTitle("Test API - Send SMS")
            .setView(dialogView)
            .setPositiveButton("Send Test SMS") { _, _ ->
                val phone = phoneEditText.text.toString().trim()
                val message = messageEditText.text.toString().trim()

                if (phone.isBlank() || message.isBlank()) {
                    Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                testApiSendSms(phone, message)
            }
            .setNeutralButton("Server Info") { _, _ ->
                testApiServerInfo()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun testApiSendSms(phoneNumber: String, message: String) {
        lifecycleScope.launch {
            try {
                val result = smsManager.sendSms(phoneNumber, message, configManager.apiKey, "test-${System.currentTimeMillis()}")
                if (result.isSuccess) {
                    val smsId = result.getOrThrow()
                    showTestResult("✅ SMS Test Successful", """
                        SMS queued for sending!

                        SMS ID: $smsId
                        Phone: $phoneNumber
                        Message: $message

                        Check the SMS History below to see delivery status.
                    """.trimIndent())
                } else {
                    val error = result.exceptionOrNull()
                    showTestResult("❌ SMS Test Failed", "Error: ${error?.message}")
                }
            } catch (e: Exception) {
                showTestResult("❌ SMS Test Failed", "Exception: ${e.message}")
            }
        }
    }

    private fun testApiServerInfo() {
        val deviceIp = getDeviceIpAddress()
        val port = configManager.serverPort
        val infoUrl = "http://$deviceIp:$port/api/info"

        showTestResult("📡 Server Info Test", """
            Test this URL in your browser or API client:

            $infoUrl

            This endpoint doesn't require authentication and should return server information in JSON format.
        """.trimIndent())
    }

    private fun showTestResult(title: String, message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun updateExternalDomainButton() {
        val buttonText = if (configManager.useExternalDomain) {
            "External Domain: ${configManager.externalDomain}"
        } else {
            "Setup External Domain"
        }
        externalDomainButton.text = buttonText
    }

    private fun showExternalDomainDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_external_domain, null)
        val domainEditText = dialogView.findViewById<EditText>(R.id.domainEditText)
        val enableSwitch = dialogView.findViewById<CompoundButton>(R.id.enableExternalDomainSwitch)
        val instructionsText = dialogView.findViewById<TextView>(R.id.instructionsText)

        // Pre-fill current values
        domainEditText.setText(configManager.externalDomain)
        enableSwitch.isChecked = configManager.useExternalDomain

        instructionsText.text = """
            1. Forward public web traffic to: ${getDeviceIpAddress()}:${configManager.serverPort}
            2. Point your domain (e.g. sms.yourdomain.com) to the proxy
            3. Enter your domain below
        """.trimIndent()

        MaterialAlertDialogBuilder(this)
            .setTitle("External Domain Setup")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val domain = domainEditText.text.toString().trim()
                val enabled = enableSwitch.isChecked

                if (enabled && domain.isBlank()) {
                    Toast.makeText(this, "Please enter a domain name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                configManager.externalDomain = domain
                configManager.useExternalDomain = enabled

                updateApiUrls()
                updateExternalDomainButton()

                val message = if (enabled) {
                    "External domain enabled: $domain"
                } else {
                    "Using local network access"
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("View Setup Guide") { _, _ ->
                showExternalDomainSetupGuide()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showExternalDomainSetupGuide() {
        val deviceIp = getDeviceIpAddress()
        val port = configManager.serverPort

        val guide = """
            Android Device Address: $deviceIp:$port

            Setup Options:
            1. Cloudflare Tunnel (Free & Easiest)
            • Run cloudflared tunnel --url http://$deviceIp:$port on any PC on the same Wi-Fi.

            2. VPS + Nginx
            • Proxy pass incoming HTTPS traffic to $deviceIp:$port.
        """.trimIndent()

        MaterialAlertDialogBuilder(this)
            .setTitle("Setup Guide")
            .setMessage(guide)
            .setPositiveButton("OK", null)
            .show()
    }
}