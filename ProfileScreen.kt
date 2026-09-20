package com.example.salaryapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.salaryapp.AppViewModel

@Composable
fun ProfileScreen(vm: AppViewModel, onChangePassword: () -> Unit) {
    val p = vm.profile
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Profile", style = MaterialTheme.typography.headlineSmall)
        if (p == null) {
            Text(if (vm.loadingList) "Loading…" else "Profile not available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(p.name, style = MaterialTheme.typography.titleLarge)
                    Info("Employee ID", p.empNo); Info("Designation", p.designation)
                    Info("Department", p.department); Info("Office", p.office)
                    Info("Station", p.station); Info("Date of joining", p.doj)
                    Info("GPF/EPF No", p.gpfEpf)
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Bank & tax", style = MaterialTheme.typography.titleMedium)
                    Info("Bank", p.bankName); Info("Account", p.bankAccMasked); Info("PAN", p.panMasked)
                    Text(
                        "To correct any detail, contact HR / Payroll.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        OutlinedButton(onClick = onChangePassword, modifier = Modifier.fillMaxWidth()) { Text("Change password") }
        Button(onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
    }
}

@Composable
private fun Info(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.padding(start = 16.dp), textAlign = TextAlign.End)
    }
}
