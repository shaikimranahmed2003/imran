package com.example.salaryapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.salaryapp.AppViewModel
import com.example.salaryapp.data.PayslipSummary
import com.example.salaryapp.data.money

@Composable
fun HomeScreen(vm: AppViewModel, onOpen: (String) -> Unit, onSeeAll: () -> Unit) {
    val latest = vm.payslips.firstOrNull()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Hello, ${vm.name.substringBefore(' ')}", style = MaterialTheme.typography.headlineSmall)
            vm.profile?.let {
                Text(
                    listOf(it.designation, it.empNo).filter { s -> s.isNotBlank() }.joinToString(" · "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (vm.loadingList && latest == null) {
            item { Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator() } }
        }
        vm.error?.let { msg ->
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(msg)
                        TextButton(onClick = { vm.refresh() }) { Text("Try again") }
                    }
                }
            }
        }
        if (latest != null) {
            item {
                Card(
                    Modifier.fillMaxWidth().clickable { onOpen(latest.month) },
                    colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Take home · ${latest.label}")
                        Text(money(latest.net), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Earnings", style = MaterialTheme.typography.labelMedium)
                                Text(money(latest.totalEarnings))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Deductions", style = MaterialTheme.typography.labelMedium)
                                Text(money(latest.totalDeductions))
                            }
                        }
                    }
                }
            }
            val earlier = vm.payslips.drop(1).take(3)
            if (earlier.isNotEmpty()) {
                item { Text("Earlier months", style = MaterialTheme.typography.titleMedium) }
                items(earlier, key = { it.month }) { PayslipRow(it, onOpen) }
                item { TextButton(onClick = onSeeAll) { Text("See all payslips") } }
            }
        } else if (!vm.loadingList && vm.error == null) {
            item { Text("No payslips have been published for you yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        item { TextButton(onClick = { vm.refresh() }) { Text("Refresh") } }
    }
}

@Composable
fun PayslipRow(p: PayslipSummary, onOpen: (String) -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onOpen(p.month) }) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(p.label, fontWeight = FontWeight.SemiBold)
            Text(money(p.net), style = MaterialTheme.typography.titleMedium)
        }
    }
}
