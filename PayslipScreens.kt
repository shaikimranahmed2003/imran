package com.example.salaryapp.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.salaryapp.AppViewModel
import com.example.salaryapp.data.Line
import com.example.salaryapp.data.Payslip
import com.example.salaryapp.data.dayCount
import com.example.salaryapp.data.money

@Composable
fun PayslipListScreen(vm: AppViewModel, onOpen: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Payslips", style = MaterialTheme.typography.headlineSmall) }
        if (vm.payslips.isEmpty()) {
            item {
                Text(
                    if (vm.loadingList) "Loading…" else "No payslips have been published for you yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(vm.payslips, key = { it.month }) { PayslipRow(it, onOpen) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayslipDetailScreen(vm: AppViewModel, month: String, onBack: () -> Unit) {
    LaunchedEffect(month) { vm.openPayslip(month) }
    val p = vm.detail
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(p?.label ?: "Payslip") },
            windowInsets = WindowInsets(0),
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                if (p != null) IconButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, summary(vm.profile?.name ?: vm.name, p))
                    }
                    ctx.startActivity(Intent.createChooser(send, "Share payslip"))
                }) { Icon(Icons.Default.Share, "Share") }
            }
        )
        when {
            vm.detailError != null -> Text(vm.detailError ?: "", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            p == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            else -> PayslipBody(p)
        }
    }
}

@Composable
private fun PayslipBody(p: Payslip) {
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(20.dp)) {
                Text("Take home salary")
                Text(money(p.net), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text(
                    "Paid days ${dayCount(p.sanctionedDays)} of ${dayCount(p.daysInMonth)}" +
                        if (p.lopDays > 0) " · LOP ${dayCount(p.lopDays)}" else "",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Section("Earnings", p.earnings, "Total earnings", p.totalEarnings)
        Section("Deductions", p.deductions, "Total deductions", p.totalDeductions)
    }
}

@Composable
private fun Section(title: String, rows: List<Line>, totalLabel: String, total: Double) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (rows.isEmpty()) Text("None", color = MaterialTheme.colorScheme.onSurfaceVariant)
            rows.forEach { r ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (r.prorated) "${r.label} (prorated)" else r.label, Modifier.weight(1f).padding(end = 12.dp))
                    Text(money(r.amount))
                }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(totalLabel, fontWeight = FontWeight.SemiBold)
                Text(money(total), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun summary(name: String, p: Payslip) = buildString {
    appendLine("TGNPDCL payslip - ${p.label}")
    appendLine(name)
    appendLine("Total earnings: ${money(p.totalEarnings)}")
    appendLine("Total deductions: ${money(p.totalDeductions)}")
    appendLine("Take home: ${money(p.net)}")
}
