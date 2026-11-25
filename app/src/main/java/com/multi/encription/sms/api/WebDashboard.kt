package com.multi.encription.sms.api

import android.content.Context
import java.io.IOException

object WebDashboard {

    fun getHtml(context: Context, apiKey: String, port: Int): String {
        return try {
            context.assets.open("dashboard.html").bufferedReader().use { it.readText() }
                .replace("{{API_KEY}}", apiKey)
                .replace("{{PORT}}", port.toString())
        } catch (e: IOException) {
            "<!DOCTYPE html><html><body><h2>Free OTP Gateway</h2><p>API Key: " + apiKey + "</p></body></html>"
        }
    }
}
