package com.example.salaryapp.data

import java.text.NumberFormat
import java.util.Locale

fun money(v: Double): String = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(v)
fun dayCount(d: Double): String = if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()

data class Profile(
    val empNo: String, val name: String, val designation: String, val office: String,
    val station: String, val department: String, val doj: String, val bankName: String,
    val bankAccMasked: String, val panMasked: String, val gpfEpf: String
)

data class PayslipSummary(
    val month: String, val label: String, val net: Double,
    val totalEarnings: Double, val totalDeductions: Double
)

data class Line(val label: String, val amount: Double, val prorated: Boolean)

data class Payslip(
    val month: String, val label: String, val lopDays: Double, val sanctionedDays: Double,
    val daysInMonth: Double, val earnings: List<Line>, val deductions: List<Line>,
    val totalEarnings: Double, val totalDeductions: Double, val net: Double
)
