package com.example.salaryapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.salaryapp.data.Api
import com.example.salaryapp.data.ApiException
import com.example.salaryapp.data.Payslip
import com.example.salaryapp.data.PayslipSummary
import com.example.salaryapp.data.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

enum class Stage { LOGIN, CHANGE_PASSWORD, MAIN }

class AppViewModel : ViewModel() {
    var stage by mutableStateOf(Stage.LOGIN); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var name by mutableStateOf(""); private set
    var profile by mutableStateOf<Profile?>(null); private set
    var payslips by mutableStateOf<List<PayslipSummary>>(emptyList()); private set
    var loadingList by mutableStateOf(false); private set
    var detail by mutableStateOf<Payslip?>(null); private set
    var detailError by mutableStateOf<String?>(null); private set

    // Kept in memory only. Nothing is written to the phone's storage.
    private var token: String? = null

    fun clearError() { error = null }

    fun login(empNo: String, password: String) {
        if (busy) return
        error = null
        busy = true
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Api.login(empNo.trim(), password) } }
            busy = false
            r.onSuccess { res ->
                token = res.token
                name = res.name
                if (res.mustChange) {
                    stage = Stage.CHANGE_PASSWORD
                } else {
                    stage = Stage.MAIN
                    refresh()
                }
            }.onFailure { error = describe(it) }
        }
    }

    fun changePassword(current: String, newPassword: String, confirm: String, onDone: () -> Unit) {
        val t = token ?: return
        if (busy) return
        if (newPassword.length < 8) { error = "New password must be at least 8 characters"; return }
        if (newPassword != confirm) { error = "The two new passwords don't match"; return }
        error = null
        busy = true
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Api.changePassword(t, current, newPassword) } }
            busy = false
            r.onSuccess { newToken ->
                token = newToken
                if (stage == Stage.CHANGE_PASSWORD) {
                    stage = Stage.MAIN
                    refresh()
                }
                onDone()
            }.onFailure { handleFailure(it) }
        }
    }

    fun refresh() {
        val t = token ?: return
        loadingList = true
        error = null
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Api.profile(t) to Api.payslips(t) } }
            loadingList = false
            r.onSuccess { (p, list) -> profile = p; payslips = list }
                .onFailure { handleFailure(it) }
        }
    }

    fun openPayslip(month: String) {
        val t = token ?: return
        detail = null
        detailError = null
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Api.payslip(t, month) } }
            r.onSuccess { detail = it }
                .onFailure { if (isSessionError(it)) handleFailure(it) else detailError = describe(it) }
        }
    }

    fun logout() {
        token = null
        stage = Stage.LOGIN
        name = ""
        profile = null
        payslips = emptyList()
        detail = null
        detailError = null
        busy = false
        loadingList = false
        error = null
    }

    private fun isSessionError(e: Throwable) = e is ApiException && e.code == "session"

    private fun handleFailure(e: Throwable) {
        if (isSessionError(e)) {
            logout()
            error = "Your session expired. Please sign in again."
        } else {
            error = describe(e)
        }
    }

    private fun describe(e: Throwable): String = when (e) {
        is ApiException -> e.message ?: "Request failed"
        is IOException -> "Can't reach the server. Check your internet connection and try again."
        else -> "Something went wrong. Please try again."
    }
}
