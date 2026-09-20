package com.example.salaryapp.data

import com.example.salaryapp.BuildConfig
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** [code] is the server's machine-readable reason, e.g. "session", "locked", "invalid_credentials". */
class ApiException(val status: Int, message: String, val code: String = "") : Exception(message)

/** All calls block: run them off the main thread (the ViewModel uses Dispatchers.IO). */
object Api {
    private val base: String get() = BuildConfig.SERVER_URL.trimEnd('/')

    data class LoginResult(val token: String, val mustChange: Boolean, val name: String)

    fun login(empNo: String, password: String): LoginResult {
        val j = call("POST", "/api/login", JSONObject().put("empNo", empNo).put("password", password))
        return LoginResult(j.getString("token"), j.optBoolean("mustChange"), j.optString("name"))
    }

    /** Returns a fresh session token. */
    fun changePassword(token: String, current: String, newPassword: String): String =
        call(
            "POST", "/api/change-password",
            JSONObject().put("currentPassword", current).put("newPassword", newPassword), token
        ).getString("token")

    fun profile(token: String): Profile =
        parseProfile(call("GET", "/api/me", token = token).getJSONObject("profile"))

    fun payslips(token: String): List<PayslipSummary> {
        val arr = call("GET", "/api/payslips", token = token).getJSONArray("payslips")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            PayslipSummary(
                o.getString("month"), o.optString("label"), o.optDouble("net"),
                o.optDouble("totalEarnings"), o.optDouble("totalDeductions")
            )
        }
    }

    fun payslip(token: String, month: String): Payslip {
        val o = call("GET", "/api/payslips/$month", token = token).getJSONObject("payslip")
        return Payslip(
            month = o.getString("month"), label = o.optString("label"),
            lopDays = o.optDouble("lopDays"), sanctionedDays = o.optDouble("sanctionedDays"),
            daysInMonth = o.optDouble("daysInMonth"),
            earnings = lines(o.optJSONArray("earnings")), deductions = lines(o.optJSONArray("deductions")),
            totalEarnings = o.optDouble("totalEarnings"), totalDeductions = o.optDouble("totalDeductions"),
            net = o.optDouble("net")
        )
    }

    private fun lines(arr: JSONArray?): List<Line> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Line(o.optString("label"), o.optDouble("amount"), o.optBoolean("prorated"))
        }
    }

    private fun parseProfile(o: JSONObject) = Profile(
        empNo = o.optString("empNo"), name = o.optString("name"), designation = o.optString("designation"),
        office = o.optString("office"), station = o.optString("station"), department = o.optString("department"),
        doj = o.optString("doj"), bankName = o.optString("bankName"), bankAccMasked = o.optString("bankAccMasked"),
        panMasked = o.optString("panMasked"), gpfEpf = o.optString("gpfEpf")
    )

    private fun call(method: String, path: String, body: JSONObject? = null, token: String? = null): JSONObject {
        val conn = URL(base + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Accept", "application/json")
            if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = try {
                if (text.isBlank()) JSONObject() else JSONObject(text)
            } catch (e: JSONException) {
                throw ApiException(status, "Unexpected response from the server ($status)")
            }
            if (status !in 200..299) {
                throw ApiException(status, json.optString("error", "Request failed ($status)"), json.optString("code"))
            }
            return json
        } finally {
            conn.disconnect()
        }
    }
}
