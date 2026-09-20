package com.example.salaryapp

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.salaryapp.ui.App
import com.example.salaryapp.ui.SalaryTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Blocks screenshots and hides the app in the recent-apps preview.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent { SalaryTheme { App(vm) } }
    }

    // Sign out whenever the app leaves the foreground (not on screen rotation).
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) vm.logout()
    }
}
