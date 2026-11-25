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
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.multi.encription.sms.core.SmsManager
import com.multi.encription.sms.database.SmsDatabase
import com.multi.encription.sms.database.SmsEntity
import com.multi.encription.sms.database.SmsStatus
import com.multi.encription.sms.service.SmsGatewayService
import com.multi.encription.sms.ui.SmsHistoryAdapter
import com.multi.encription.sms.utils.ConfigManager
import com.multi.encription.sms.utils.PermissionHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var configManager: ConfigManager
    private lateinit var smsManager: SmsManager
    private lateinit var database: SmsDatabase
    private lateinit var smsHistoryAdapter: SmsHistoryAdapter

    private var isApiKeyVisible = false

    // UI Components
    private lateinit var rootCoordinator: CoordinatorLayout
    private lateinit var headerStatusDot: View
    private lateinit var headerSubtitleText: TextView
    private lateinit var serverIcon: ImageView
    private lateinit var statusDot: View
    private lateinit var serverStatusText: TextView
    private lateinit var serverToggle: SwitchMaterial
    private lateinit var baseUrlDisplay: TextView
    private lateinit var copyUrlButton: MaterialButton
    private lateinit var portText: TextView
    private lateinit var changePortButton: MaterialButton
    private lateinit var apiKeyText: TextView
    private lateinit var toggleKeyVisibilityButton: MaterialButton
    private lateinit var copyKeyButton: MaterialButton
    private lateinit var regenerateKeyButton: MaterialButton
    private lateinit var copyCodeButton: MaterialButton
    private lateinit var apiUrlsText: TextView
    private lateinit var testApiButton: MaterialButton
    private lateinit var openDashboardButton: MaterialButton
    private lateinit var externalDomainButton: MaterialButton
    private lateinit var historyCountBadge: TextView
    private lateinit var emptyHistoryLayout: View
    private lateinit var smsHistoryRecyclerView: RecyclerView
    private lateinit var sendSmsFab: ExtendedFloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val appBarLayout = findViewById<View>(R.id.appBarLayout)
        val nestedScrollView = findViewById<View>(R.id.nestedScrollView)
        val fab = findViewById<View>(R.id.sendSmsFab)

        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(0, statusBars.top, 0, 0)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(nestedScrollView) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(0, 0, 0, navBars.bottom + dpToPx(92))
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(fab) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val lp = v.layoutParams as ViewGroup.MarginLayoutParams
            lp.bottomMargin = navBars.bottom + dpToPx(16)
            v.layoutParams = lp
            insets
        }

        initializeComponents()
        setupUI()
        checkPermissions()
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun showFeedback(message: String, isError: Boolean = false) {
        val snackbar = Snackbar.make(rootCoordinator, message, Snackbar.LENGTH_SHORT)
        snackbar.anchorView = sendSmsFab
        snackbar.setBackgroundTint(
            ContextCompat.getColor(
                this,
                if (isError) R.color.accent_error_bg else R.color.bg_elevated
            )
        )
        snackbar.setTextColor(
            ContextCompat.getColor(
                this,
                if (isError) R.color.accent_error_text else R.color.text_primary
            )
        )
        snackbar.show()
    }

    private fun initializeComponents() {
        configManager = ConfigManager(this)
        smsManager = SmsManager(this)
        database = SmsDatabase.getDatabase(this)

        rootCoordinator = findViewById(R.id.main)
        headerStatusDot = findViewById(R.id.headerStatusDot)
        headerSubtitleText = findViewById(R.id.headerSubtitleText)
        serverIcon = findViewById(R.id.serverIcon)
        statusDot = findViewById(R.id.statusDot)
        serverStatusText = findViewById(R.id.serverStatusText)
        serverToggle = findViewById(R.id.serverToggle)
        baseUrlDisplay = findViewById(R.id.baseUrlDisplay)
        copyUrlButton = findViewById(R.id.copyUrlButton)
        portText = findViewById(R.id.portText)
        changePortButton = findViewById(R.id.changePortButton)
        apiKeyText = findViewById(R.id.apiKeyText)
        toggleKeyVisibilityButton = findViewById(R.id.toggleKeyVisibilityButton)
        copyKeyButton = findViewById(R.id.copyKeyButton)
        regenerateKeyButton = findViewById(R.id.regenerateKeyButton)
        copyCodeButton = findViewById(R.id.copyCodeButton)
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
        smsHistoryAdapter = SmsHistoryAdapter { sms ->
            showSmsDetails(sms)
        }

        smsHistoryRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = smsHistoryAdapter
        }

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
                showFeedback("API server started on port ${configManager.serverPort}")
            } else {
                SmsGatewayService.stopService(this)
                showFeedback("API server stopped")
            }
            updateServerStatus()
            updateApiUrls()
        }

        if (configManager.isServerEnabled && PermissionHelper.hasSmsPermissions(this)) {
            SmsGatewayService.startService(this)
        }

        sendSmsFab.setOnClickListener { showSendSmsDialog() }

        copyUrlButton.setOnClickListener {
            val baseUrl = configManager.getApiBaseUrl(getDeviceIpAddress())
            copyToClipboard("API Endpoint", baseUrl)
        }

        toggleKeyVisibilityButton.setOnClickListener {
            isApiKeyVisible = !isApiKeyVisible
            updateApiKeyDisplay()
        }

        copyKeyButton.setOnClickListener {
            copyToClipboard("API Key", configManager.apiKey)
        }

        regenerateKeyButton.setOnClickListener {
            showRegenerateKeyConfirmationDialog()
        }

        copyCodeButton.setOnClickListener {
            val baseUrl = configManager.getApiBaseUrl(getDeviceIpAddress())
            val curlCommand = """
                curl -X POST $baseUrl/api/send \
                  -H "Content-Type: application/json" \
                  -H "X-API-Key: <YOUR_API_KEY>" \
                  -d '{"phone_number": "+918114963709", "message": "Your OTP is 123456"}'
            """.trimIndent()
            copyToClipboard("Sample Request", curlCommand)
        }

        openDashboardButton.setOnClickListener {
            val baseUrl = configManager.getApiBaseUrl(getDeviceIpAddress())
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(baseUrl))
                startActivity(intent)
            } catch (e: Exception) {
                showFeedback("Could not open browser: ${e.message}", isError = true)
            }
        }

        apiKeyText.setOnClickListener { showApiKeyDialog() }
        portText.setOnClickListener { showPortDialog() }
        changePortButton.setOnClickListener { showPortDialog() }
        apiUrlsText.setOnClickListener { showApiUrlsDialog() }
        testApiButton.setOnClickListener { showTestApiDialog() }
        externalDomainButton.setOnClickListener { showExternalDomainDialog() }

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
            .setMessage("Free OTP Gateway requires SMS Send and Notification permissions to dispatch OTPs over your device's cellular connection.")
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
            .setTitle("SMS Permission & Restricted Settings")
            .setMessage(
                "On Android 13, 14, and 15, sideloaded apps require one manual step to enable SMS permissions:\n\n" +
                "1. Tap 'Open App Settings' below.\n" +
                "2. Tap the three dots menu in the top-right corner.\n" +
                "3. Select 'Allow restricted settings' and verify.\n" +
                "4. Go to Permissions > SMS and select Allow.\n\n" +
                "Return to this app once allowed to start the API server."
            )
            .setPositiveButton("Open App Settings") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    showFeedback("Could not open app settings", isError = true)
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
                showFeedback("Gateway permissions granted")
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
        val port = configManager.serverPort
        if (isRunning) {
            val green = ContextCompat.getColor(this, R.color.accent_success)
            serverStatusText.text = "Running"
            serverStatusText.setTextColor(green)
            statusDot.backgroundTintList = ColorStateList.valueOf(green)
            serverIcon.imageTintList = ColorStateList.valueOf(green)
            headerStatusDot.backgroundTintList = ColorStateList.valueOf(green)
            headerSubtitleText.text = "Online · Port $port"
        } else {
            val red = ContextCompat.getColor(this, R.color.accent_error)
            val redText = ContextCompat.getColor(this, R.color.accent_error_text)
            serverStatusText.text = "Stopped"
            serverStatusText.setTextColor(redText)
            statusDot.backgroundTintList = ColorStateList.valueOf(red)
            serverIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.text_muted))
            headerStatusDot.backgroundTintList = ColorStateList.valueOf(red)
            headerSubtitleText.text = "Offline · Port $port"
        }
    }

    private fun updateApiKeyDisplay() {
        val apiKey = configManager.apiKey
        if (isApiKeyVisible) {
            apiKeyText.text = apiKey
            toggleKeyVisibilityButton.setIconResource(R.drawable.ic_visibility_off)
            toggleKeyVisibilityButton.contentDescription = "Hide API key"
        } else {
            val masked = if (apiKey.length > 12) {
                "${apiKey.take(8)}••••••••${apiKey.takeLast(4)}"
            } else {
                "••••••••••••"
            }
            apiKeyText.text = masked
            toggleKeyVisibilityButton.setIconResource(R.drawable.ic_visibility)
            toggleKeyVisibilityButton.contentDescription = "Reveal API key"
        }
    }

    private fun updatePortDisplay() {
        portText.text = "Port: ${configManager.serverPort}"
    }

    private fun updateApiUrls() {
        val baseUrl = configManager.getApiBaseUrl(getDeviceIpAddress())
        baseUrlDisplay.text = baseUrl
        apiUrlsText.text = buildSyntaxHighlightedPreview(baseUrl)
    }

    private fun buildSyntaxHighlightedPreview(baseUrl: String): SpannableStringBuilder {
        val cMethod = ContextCompat.getColor(this, R.color.syntax_method)
        val cEndpoint = ContextCompat.getColor(this, R.color.syntax_endpoint)
        val cProp = ContextCompat.getColor(this, R.color.syntax_property)
        val cValue = ContextCompat.getColor(this, R.color.syntax_value)
        val cSupport = ContextCompat.getColor(this, R.color.syntax_supporting)
        val cDefault = ContextCompat.getColor(this, R.color.syntax_default)

        val sb = SpannableStringBuilder()
        fun appendColored(text: String, color: Int) {
            val start = sb.length
            sb.append(text)
            sb.setSpan(ForegroundColorSpan(color), start, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        appendColored("POST", cMethod)
        appendColored(" ", cDefault)
        appendColored("$baseUrl/api/send", cEndpoint)
        appendColored("\n", cDefault)
        appendColored("X-API-Key: ", cSupport)
        appendColored("<YOUR_API_KEY>", cValue)
        appendColored("\n{\n  ", cDefault)
        appendColored("\"phone_number\"", cProp)
        appendColored(": ", cDefault)
        appendColored("\"+918114963709\"", cValue)
        appendColored(",\n  ", cDefault)
        appendColored("\"message\"", cProp)
        appendColored(": ", cDefault)
        appendColored("\"Your OTP is 123456\"", cValue)
        appendColored("\n}", cDefault)

        return sb
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
        } catch (_: Exception) {
            // Fallback to local IP pattern
        }
        return "192.168.1.100"
    }

    private fun showSendSmsDialog() {
        if (!PermissionHelper.hasSmsPermissions(this)) {
            showRestrictedSettingsDialog()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_send_sms, null)
        val phoneLayout = dialogView.findViewById<TextInputLayout>(R.id.phoneInputLayout)
        val messageLayout = dialogView.findViewById<TextInputLayout>(R.id.messageInputLayout)
        val phoneEditText = dialogView.findViewById<TextInputEditText>(R.id.phoneEditText)
        val messageEditText = dialogView.findViewById<TextInputEditText>(R.id.messageEditText)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Send SMS")
            .setView(dialogView)
            .setPositiveButton("Send", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val sendBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            sendBtn.setOnClickListener {
                val phone = phoneEditText.text?.toString()?.trim().orEmpty()
                val message = messageEditText.text?.toString()?.trim().orEmpty()
                var isValid = true

                if (phone.length < 4) {
                    phoneLayout.error = "Enter a valid phone number with country code"
                    isValid = false
                } else {
                    phoneLayout.error = null
                }

                if (message.isBlank()) {
                    messageLayout.error = "Enter a message or OTP to send"
                    isValid = false
                } else {
                    messageLayout.error = null
                }

                if (isValid) {
                    dialog.dismiss()
                    sendSms(phone, message)
                }
            }
        }
        dialog.show()
    }

    private fun sendSms(phoneNumber: String, message: String) {
        showFeedback("Sending SMS to $phoneNumber...")
        lifecycleScope.launch {
            try {
                val result = smsManager.sendSms(phoneNumber, message)
                if (result.isSuccess) {
                    showFeedback("SMS queued for sending")
                } else {
                    val error = result.exceptionOrNull()
                    showFeedback("Failed to send SMS: ${error?.message}", isError = true)
                }
            } catch (e: Exception) {
                showFeedback("Error sending SMS: ${e.message}", isError = true)
            }
        }
    }

    private fun showSmsDetails(sms: SmsEntity) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_sms_details, null)
        val phoneText = dialogView.findViewById<TextView>(R.id.detailPhoneText)
        val statusBadge = dialogView.findViewById<TextView>(R.id.detailStatusBadge)
        val messageText = dialogView.findViewById<TextView>(R.id.detailMessageText)
        val metadataText = dialogView.findViewById<TextView>(R.id.detailMetadataText)

        phoneText.text = sms.phoneNumber
        messageText.text = sms.message

        val (label, bgColor, fgColor) = when (sms.status) {
            SmsStatus.DELIVERED -> Triple("Delivered", R.color.status_delivered_bg, R.color.status_delivered)
            SmsStatus.SENT -> Triple("Sent", R.color.status_sent_bg, R.color.status_sent)
            SmsStatus.PENDING -> Triple("Pending", R.color.status_pending_bg, R.color.status_pending)
            SmsStatus.FAILED -> Triple("Failed", R.color.status_failed_bg, R.color.status_failed)
            SmsStatus.UNKNOWN -> Triple("Unknown", R.color.status_unknown_bg, R.color.status_unknown)
        }
        statusBadge.text = label
        statusBadge.setTextColor(ContextCompat.getColor(this, fgColor))
        statusBadge.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, bgColor))

        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val metaLines = mutableListOf<String>()
        metaLines.add("Sent: ${fmt.format(Date(sms.timestamp))}")
        sms.deliveryTimestamp?.let { metaLines.add("Delivered: ${fmt.format(Date(it))}") }
        sms.requestId?.takeIf { it.isNotBlank() }?.let { metaLines.add("Request ID: $it") }
        sms.errorMessage?.takeIf { it.isNotBlank() }?.let { metaLines.add("Error: $it") }
        metadataText.text = metaLines.joinToString("\n")

        MaterialAlertDialogBuilder(this)
            .setTitle("SMS Details")
            .setView(dialogView)
            .setPositiveButton("Copy Message") { _, _ ->
                copyToClipboard("Message", sms.message)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showApiKeyDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_api_key, null)
        val keyDisplay = dialogView.findViewById<TextView>(R.id.dialogApiKeyDisplay)
        val btnCopy = dialogView.findViewById<MaterialButton>(R.id.btnDialogCopyKey)
        val btnRegen = dialogView.findViewById<MaterialButton>(R.id.btnDialogRegenerateKey)
        val btnDone = dialogView.findViewById<MaterialButton>(R.id.btnDialogKeyDone)

        keyDisplay.text = configManager.apiKey

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("API Authentication Key")
            .setView(dialogView)
            .create()

        btnCopy.setOnClickListener {
            copyToClipboard("API Key", configManager.apiKey)
        }
        btnRegen.setOnClickListener {
            showRegenerateKeyConfirmationDialog {
                keyDisplay.text = configManager.apiKey
            }
        }
        btnDone.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showRegenerateKeyConfirmationDialog(onRegenerated: (() -> Unit)? = null) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Generate New API Key?")
            .setMessage("Replacing the existing API key will immediately disconnect any clients or scripts using the current key until you update them.")
            .setPositiveButton("Generate New") { _, _ ->
                configManager.regenerateApiKey()
                updateApiKeyDisplay()
                updateApiUrls()
                onRegenerated?.invoke()
                showFeedback("New API key generated")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPortDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_port, null)
        val portLayout = dialogView.findViewById<TextInputLayout>(R.id.portInputLayout)
        val portInput = dialogView.findViewById<TextInputEditText>(R.id.portInputEditText)
        portInput.setText(configManager.serverPort.toString())
        portInput.setSelection(portInput.text?.length ?: 0)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Server Port")
            .setView(dialogView)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val saveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveBtn.setOnClickListener {
                val port = portInput.text?.toString()?.trim()?.toIntOrNull()
                if (port != null && port in 1024..65535) {
                    portLayout.error = null
                    configManager.serverPort = port
                    updatePortDisplay()
                    updateServerStatus()
                    updateApiUrls()

                    if (configManager.isServerEnabled) {
                        SmsGatewayService.stopService(this)
                        SmsGatewayService.startService(this)
                    }

                    dialog.dismiss()
                    showFeedback("Server port updated to $port")
                } else {
                    portLayout.error = "Enter a valid port between 1024 and 65535"
                }
            }
        }
        dialog.show()
    }

    private fun showApiUrlsDialog() {
        val baseUrl = configManager.getApiBaseUrl(getDeviceIpAddress())

        val message = """
            Base URL:
            $baseUrl

            Available Endpoints:

            1. Send SMS
            POST $baseUrl/api/send
            Header: X-API-Key: <YOUR_API_KEY>
            Body: {"phone_number": "+918114963709", "message": "OTP: 123456"}

            2. Check SMS Status
            GET $baseUrl/api/status?sms_id=123
            Header: X-API-Key: <YOUR_API_KEY>

            3. Get SMS History
            GET $baseUrl/api/history?limit=10
            Header: X-API-Key: <YOUR_API_KEY>

            4. Server Health Info (No Auth)
            GET $baseUrl/api/info
        """.trimIndent()

        MaterialAlertDialogBuilder(this)
            .setTitle("API Endpoints Reference")
            .setMessage(message)
            .setPositiveButton("Copy Base URL") { _, _ ->
                copyToClipboard("Base URL", baseUrl)
            }
            .setNeutralButton("Copy API Key") { _, _ ->
                copyToClipboard("API Key", configManager.apiKey)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showTestApiDialog() {
        if (!configManager.isServerEnabled) {
            showFeedback("Enable the API server first to run tests", isError = true)
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_test_api, null)
        val phoneLayout = dialogView.findViewById<TextInputLayout>(R.id.testPhoneInputLayout)
        val messageLayout = dialogView.findViewById<TextInputLayout>(R.id.testMessageInputLayout)
        val phoneEditText = dialogView.findViewById<TextInputEditText>(R.id.testPhoneEditText)
        val messageEditText = dialogView.findViewById<TextInputEditText>(R.id.testMessageEditText)
        val resultIcon = dialogView.findViewById<ImageView>(R.id.testResultIcon)
        val resultTextView = dialogView.findViewById<TextView>(R.id.testResultTextView)
        val btnSend = dialogView.findViewById<MaterialButton>(R.id.btnSendTestSms)
        val btnServerInfo = dialogView.findViewById<MaterialButton>(R.id.btnTestServerInfo)
        val btnClose = dialogView.findViewById<MaterialButton>(R.id.btnCloseTestDialog)

        phoneEditText.setText("+91")
        phoneEditText.setSelection(phoneEditText.text?.length ?: 0)
        messageEditText.setText("Test message from Free OTP Gateway")

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Test API - Send SMS")
            .setView(dialogView)
            .create()

        var isSubmitting = false

        btnSend.setOnClickListener {
            if (isSubmitting) return@setOnClickListener

            val phone = phoneEditText.text?.toString()?.trim().orEmpty()
            val message = messageEditText.text?.toString()?.trim().orEmpty()
            var isValid = true

            if (phone.length < 4) {
                phoneLayout.error = "Enter a valid phone number"
                isValid = false
            } else {
                phoneLayout.error = null
            }

            if (message.isBlank()) {
                messageLayout.error = "Enter a test message"
                isValid = false
            } else {
                messageLayout.error = null
            }

            if (!isValid) return@setOnClickListener

            isSubmitting = true
            btnSend.isEnabled = false
            btnSend.text = "Sending..."
            resultIcon.setImageResource(R.drawable.ic_info)
            resultIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.accent_warning))
            resultTextView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            resultTextView.text = "Queuing SMS to $phone..."

            lifecycleScope.launch {
                try {
                    val result = smsManager.sendSms(phone, message, configManager.apiKey, "test-${System.currentTimeMillis()}")
                    if (result.isSuccess) {
                        val smsId = result.getOrThrow()
                        resultIcon.setImageResource(R.drawable.ic_check_circle)
                        resultIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.accent_success))
                        resultTextView.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
                        resultTextView.text = "SMS queued successfully (ID: #$smsId). Check Outbound SMS History for delivery status."
                    } else {
                        val error = result.exceptionOrNull()
                        resultIcon.setImageResource(R.drawable.ic_error_circle)
                        resultIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.accent_error))
                        resultTextView.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.accent_error_text))
                        resultTextView.text = "Test failed: ${error?.message ?: "Unknown error"}"
                    }
                } catch (e: Exception) {
                    resultIcon.setImageResource(R.drawable.ic_error_circle)
                    resultIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.accent_error))
                    resultTextView.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.accent_error_text))
                    resultTextView.text = "Connection error: ${e.message}"
                } finally {
                    isSubmitting = false
                    btnSend.isEnabled = true
                    btnSend.text = "Send Test SMS"
                }
            }
        }

        btnServerInfo.setOnClickListener {
            val infoUrl = "${configManager.getApiBaseUrl(getDeviceIpAddress())}/api/info"
            resultIcon.setImageResource(R.drawable.ic_info)
            resultIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.accent_info))
            resultTextView.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            resultTextView.text = "Health Endpoint (No Auth):\n$infoUrl\nOpen in any browser on the same network to verify JSON status."
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        showFeedback("$label copied to clipboard")
    }

    private fun updateExternalDomainButton() {
        externalDomainButton.text = if (configManager.useExternalDomain && configManager.externalDomain.isNotBlank()) {
            "External Domain: ${configManager.externalDomain}"
        } else {
            "Setup External Domain"
        }
    }

    private fun showExternalDomainDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_external_domain, null)
        val domainLayout = dialogView.findViewById<TextInputLayout>(R.id.domainInputLayout)
        val domainEditText = dialogView.findViewById<TextInputEditText>(R.id.domainEditText)
        val enableSwitch = dialogView.findViewById<SwitchMaterial>(R.id.enableExternalDomainSwitch)
        val stateText = dialogView.findViewById<TextView>(R.id.domainToggleStateText)
        val instructionsText = dialogView.findViewById<TextView>(R.id.instructionsText)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btnSaveDomain)
        val btnGuide = dialogView.findViewById<MaterialButton>(R.id.btnViewSetupGuide)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancelDomain)

        domainEditText.setText(configManager.externalDomain)
        enableSwitch.isChecked = configManager.useExternalDomain

        fun refreshToggleState(checked: Boolean) {
            if (checked) {
                stateText.text = "Enabled (routing via custom domain)"
                stateText.setTextColor(ContextCompat.getColor(this, R.color.accent_success))
            } else {
                stateText.text = "Disabled (using local IP address)"
                stateText.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
            }
        }
        refreshToggleState(enableSwitch.isChecked)
        enableSwitch.setOnCheckedChangeListener { _, isChecked ->
            refreshToggleState(isChecked)
            if (!isChecked) domainLayout.error = null
        }

        val deviceAddr = "${getDeviceIpAddress()}:${configManager.serverPort}"
        instructionsText.text = """
            1. Set up a Cloudflare Tunnel or Nginx reverse proxy.
            2. Forward incoming HTTPS traffic to $deviceAddr.
            3. Enter your public domain above and enable routing.
        """.trimIndent()

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("External Domain Setup")
            .setView(dialogView)
            .create()

        btnSave.setOnClickListener {
            val domain = domainEditText.text?.toString()?.trim().orEmpty()
            val enabled = enableSwitch.isChecked

            if (enabled && domain.isBlank()) {
                domainLayout.error = "Enter a domain name to enable custom routing"
                return@setOnClickListener
            }

            domainLayout.error = null
            configManager.externalDomain = domain
            configManager.useExternalDomain = enabled

            updateApiUrls()
            updateExternalDomainButton()
            dialog.dismiss()

            if (enabled) {
                showFeedback("External domain saved: $domain")
            } else {
                showFeedback("Switched to local network access")
            }
        }

        btnGuide.setOnClickListener {
            dialog.dismiss()
            showExternalDomainSetupGuide()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showExternalDomainSetupGuide() {
        val deviceIp = getDeviceIpAddress()
        val port = configManager.serverPort
        val deviceAddress = "http://$deviceIp:$port"
        val cfCommand = "cloudflared tunnel --url $deviceAddress"
        val nginxConfig = """
            location / {
                proxy_pass $deviceAddress;
                proxy_set_header Host ${'$'}host;
                proxy_set_header X-Real-IP ${'$'}remote_addr;
            }
        """.trimIndent()

        val dialogView = layoutInflater.inflate(R.layout.dialog_setup_guide, null)
        val addressText = dialogView.findViewById<TextView>(R.id.guideDeviceAddressText)
        val cfCmdText = dialogView.findViewById<TextView>(R.id.guideCloudflareCmdText)
        val nginxText = dialogView.findViewById<TextView>(R.id.guideNginxConfigText)
        val btnCopyAddress = dialogView.findViewById<MaterialButton>(R.id.btnCopyDeviceAddress)
        val btnCopyCf = dialogView.findViewById<MaterialButton>(R.id.btnCopyCloudflareCmd)
        val btnClose = dialogView.findViewById<MaterialButton>(R.id.btnCloseGuide)

        addressText.text = deviceAddress
        cfCmdText.text = cfCommand
        nginxText.text = nginxConfig

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("External Domain Setup Guide")
            .setView(dialogView)
            .create()

        btnCopyAddress.setOnClickListener {
            copyToClipboard("Device Address", deviceAddress)
        }
        btnCopyCf.setOnClickListener {
            copyToClipboard("Cloudflare Command", cfCommand)
        }
        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}
