@file:OptIn(ExperimentalMaterial3Api::class)

package com.fiatlife.app.ui.screens.salary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.util.UUID
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fiatlife.app.domain.model.*
import com.fiatlife.app.ui.components.*
import com.fiatlife.app.ui.theme.*
import com.fiatlife.app.ui.viewmodel.DepositEditTarget
import com.fiatlife.app.ui.viewmodel.SalaryTab
import com.fiatlife.app.ui.viewmodel.SalaryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalaryScreen(
    viewModel: SalaryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val calc = state.calculation
    val snackbarHostState = remember { SnackbarHostState() }
    var showTaxSetup by remember { mutableStateOf(false) }
    var summaryStatsAnnual by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        if (state.message.isNotBlank()) {
            snackbarHostState.showSnackbar(
                message = state.message,
                duration = SnackbarDuration.Short
            )
            viewModel.clearMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Tab toggle + tax setup
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                    SegmentedButton(
                        selected = state.activeTab == SalaryTab.SUMMARY,
                        onClick = { viewModel.setActiveTab(SalaryTab.SUMMARY) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text("Summary") }
                    SegmentedButton(
                        selected = state.activeTab == SalaryTab.WHATIF,
                        onClick = { viewModel.setActiveTab(SalaryTab.WHATIF) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text("Model") }
                }
                BadgedBox(
                    badge = {
                        if (state.config.isTaxSetupIncomplete()) {
                            Badge(
                                modifier = Modifier.size(8.dp),
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                ) {
                    OutlinedButton(
                        onClick = { showTaxSetup = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = "Tax",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        when (state.activeTab) {
            SalaryTab.SUMMARY -> summaryContent(
                state,
                viewModel,
                summaryStatsAnnual,
                onSummaryStatsAnnualChange = { summaryStatsAnnual = it },
            )
            SalaryTab.WHATIF -> whatIfContent(state, calc, viewModel)
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }

    if (showTaxSetup) {
        TaxSetupDialog(
            config = state.config,
            onDismiss = { showTaxSetup = false },
            onFilingStatus = viewModel::updateFilingStatus,
            onState = viewModel::updateState,
            onCounty = viewModel::updateCounty
        )
    }

    if (state.showLogDialog) {
        LogPaycheckDialog(
            editing = state.editingLog,
            payDateHint = state.logPayDateHint,
            calculation = calc,
            config = state.config,
            onDismiss = { viewModel.dismissLogDialog() },
            onSave = { viewModel.saveLogPaycheck(it) }
        )
    }

    if (state.showDeductionDialog) {
        DeductionDialog(
            deduction = state.editingDeduction,
            isPreTax = state.isPreTaxDeduction,
            onDismiss = { viewModel.dismissDeductionDialog() },
            onSave = { viewModel.saveDeduction(it) }
        )
    }

    if (state.showDepositDialog) {
        DepositDialog(
            deposit = state.editingDeposit,
            onDismiss = { viewModel.dismissDepositDialog() },
            onSave = { viewModel.saveDeposit(it) }
        )
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
    )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private fun androidx.compose.foundation.lazy.LazyListScope.whatIfContent(
    state: com.fiatlife.app.ui.viewmodel.SalaryState,
    calc: PaycheckCalculation,
    viewModel: SalaryViewModel
) {
    item {
        SectionCard(title = "Hypothetical modeling", icon = Icons.Filled.Info) {
            Text(
                text = "Changes here are hypothetical — they do not affect your Summary " +
                        "projections until you log real paychecks or save updated settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Modeled Net (This Period)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                MoneyText(
                    amount = calc.netPay,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem("Gross", calc.grossPay.formatCurrency())
                    StatItem("Taxes", calc.totalTaxes.formatCurrency())
                    StatItem("Modeled Annual", state.annualProjection.annualNetPay.formatCurrency())
                }
            }
        }
    }

    // Hours & Rate
    item {
        SectionCard(title = "Pay Rate & Hours", icon = Icons.Filled.AccessTime) {
            var hourlyRate by remember(state.config.hourlyRate) {
                mutableStateOf(if (state.config.hourlyRate > 0) state.config.hourlyRate.toString() else "")
            }
            var standardHours by remember(state.config.standardHoursPerPeriod) {
                mutableStateOf(state.config.standardHoursPerPeriod.toString())
            }
            var overtimeHours by remember(state.config.overtimeHours) {
                mutableStateOf(if (state.config.overtimeHours > 0) state.config.overtimeHours.toString() else "")
            }
            var overtimeMultiplier by remember(state.config.overtimeMultiplier) {
                mutableStateOf(state.config.overtimeMultiplier.toString())
            }
            var annualSalary by remember(state.config.annualSalary) {
                mutableStateOf(if (state.config.annualSalary > 0) state.config.annualSalary.toString() else "")
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                PayType.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = state.config.payType == type,
                        onClick = { viewModel.updatePayType(type) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = PayType.entries.size)
                    ) { Text(type.displayName) }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (state.config.payType == PayType.SALARY) {
                CurrencyTextField(
                    value = annualSalary,
                    onValueChange = {
                        annualSalary = it
                        it.toDoubleOrNull()?.let { v -> viewModel.updateAnnualSalary(v) }
                    },
                    label = "Annual Salary"
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = overtimeHours,
                    onValueChange = {
                        overtimeHours = it
                        it.toDoubleOrNull()?.let { v -> viewModel.updateOvertimeHours(v) }
                    },
                    label = { Text("OT Hours") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            } else {
                CurrencyTextField(
                    value = hourlyRate,
                    onValueChange = {
                        hourlyRate = it
                        it.toDoubleOrNull()?.let { v -> viewModel.updateHourlyRate(v) }
                    },
                    label = "Hourly Rate"
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = standardHours,
                        onValueChange = {
                            standardHours = it
                            it.toDoubleOrNull()?.let { v -> viewModel.updateStandardHours(v) }
                        },
                        label = { Text("Standard Hours") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = overtimeHours,
                        onValueChange = {
                            overtimeHours = it
                            it.toDoubleOrNull()?.let { v -> viewModel.updateOvertimeHours(v) }
                        },
                        label = { Text("OT Hours") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = overtimeMultiplier,
                onValueChange = {
                    overtimeMultiplier = it
                    it.toDoubleOrNull()?.let { v -> viewModel.updateOvertimeMultiplier(v) }
                },
                label = { Text("OT Multiplier") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            if (calc.overtimePay > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    LabeledValue(label = "Regular Pay", value = calc.regularPay.formatCurrency())
                    LabeledValue(
                        label = "Overtime Pay",
                        value = calc.overtimePay.formatCurrency(),
                        valueColor = ProfitGreen
                    )
                }
            }
        }
    }

    // Withholding overrides
    item {
        SectionCard(title = "Withholding overrides", icon = Icons.Filled.AccountBalance) {
            Text(
                text = "Filing status and state are set from the tax button above. Tap any rate below to match your actual stub.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Tax Breakdown (per paycheck)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Tap any rate to override with your actual withholding percentage",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            EditableTaxLine(
                label = "Federal Income Tax",
                amount = calc.federalTax,
                defaultRate = calc.federalMarginalRate,
                customRate = state.config.taxOverrides.customFederalTaxRate,
                onRateChange = { viewModel.updateCustomFederalTaxRate(it) }
            )
            EditableTaxLine(
                label = "State Income Tax",
                amount = calc.stateTax,
                defaultRate = calc.stateTaxRate,
                customRate = state.config.taxOverrides.customStateTaxRate,
                onRateChange = { viewModel.updateCustomStateTaxRate(it) }
            )
            EditableTaxLine(
                label = "County/Local Tax",
                amount = calc.countyTax,
                defaultRate = calc.countyTaxRate,
                customRate = state.config.taxOverrides.customCountyTaxRate,
                onRateChange = { viewModel.updateCustomCountyTaxRate(it) }
            )
            EditableTaxLine(
                label = "Social Security",
                amount = calc.socialSecurity,
                defaultRate = calc.socialSecurityRate,
                customRate = state.config.taxOverrides.customSocialSecurityRate,
                onRateChange = { viewModel.updateCustomSocialSecurityRate(it) }
            )
            EditableTaxLine(
                label = "Medicare",
                amount = calc.medicare,
                defaultRate = calc.medicareRate,
                customRate = state.config.taxOverrides.customMedicareRate,
                onRateChange = { viewModel.updateCustomMedicareRate(it) }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            TaxLine("Total Taxes", calc.totalTaxes, bold = true)
            Text(
                text = "Effective rate: ${calc.effectiveTaxRate.formatPercentage()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // Pre-Tax Deductions
    item {
        SectionCard(
            title = "Pre-Tax Deductions",
            icon = Icons.Filled.RemoveCircleOutline,
            action = {
                IconButton(onClick = { viewModel.showAddDeduction(isPreTax = true) }) {
                    Icon(Icons.Filled.Add, "Add deduction")
                }
            }
        ) {
            if (state.config.preTaxDeductions.isEmpty()) {
                Text(
                    text = "No pre-tax deductions added",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.config.preTaxDeductions.forEach { deduction ->
                    DeductionRow(
                        deduction = deduction,
                        onEdit = { viewModel.showEditDeduction(deduction, true) },
                        onDelete = { viewModel.removeDeduction(deduction.id, true) }
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                TaxLine("Total Pre-Tax", calc.totalPreTaxDeductions, bold = true)
            }
        }
    }

    // Post-Tax Deductions
    item {
        SectionCard(
            title = "Post-Tax Deductions",
            icon = Icons.Filled.RemoveCircleOutline,
            action = {
                IconButton(onClick = { viewModel.showAddDeduction(isPreTax = false) }) {
                    Icon(Icons.Filled.Add, "Add deduction")
                }
            }
        ) {
            if (state.config.postTaxDeductions.isEmpty()) {
                Text(
                    text = "No post-tax deductions added",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.config.postTaxDeductions.forEach { deduction ->
                    DeductionRow(
                        deduction = deduction,
                        onEdit = { viewModel.showEditDeduction(deduction, false) },
                        onDelete = { viewModel.removeDeduction(deduction.id, false) }
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                TaxLine("Total Post-Tax", calc.totalPostTaxDeductions, bold = true)
            }
        }
    }

    directDepositsContent(state, viewModel, DepositEditTarget.WHATIF)

    whatIfAnnualSections(state, viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
private fun androidx.compose.foundation.lazy.LazyListScope.whatIfAnnualSections(
    state: com.fiatlife.app.ui.viewmodel.SalaryState,
    viewModel: SalaryViewModel
) {
    val proj = state.annualProjection
    val base = state.annualBaseProjection

    item {
        SectionCard(title = "Annual Model", icon = Icons.Filled.CalendarMonth) {
            Text(
                text = "See how changes above would play out over a full year — useful for " +
                        "modeling raises, bonus pay, or annual overtime.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // Overtime estimator
    item {
        SectionCard(title = "Overtime Estimator", icon = Icons.Filled.Schedule) {
            var otText by remember(state.annualOvertimeHours) {
                mutableStateOf(
                    if (state.annualOvertimeHours > 0) state.annualOvertimeHours.toString() else ""
                )
            }
            OutlinedTextField(
                value = otText,
                onValueChange = {
                    otText = it
                    viewModel.updateAnnualOvertimeHours(it.toDoubleOrNull() ?: 0.0)
                },
                label = { Text("Estimated Annual OT Hours") },
                placeholder = { Text("e.g. 200") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                leadingIcon = { Icon(Icons.Filled.MoreTime, contentDescription = null) },
                supportingText = {
                    val perPeriod = if (state.config.payFrequency.periodsPerYear > 0)
                        state.annualOvertimeHours / state.config.payFrequency.periodsPerYear else 0.0
                    Text("~ %.1f OT hrs per paycheck".format(perPeriod))
                }
            )

            if (state.annualOvertimeHours > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    LabeledValue(
                        label = "OT Gross",
                        value = proj.annualOvertimePay.formatCurrency(),
                        valueColor = ProfitGreen
                    )
                    LabeledValue(
                        label = "Extra Net vs Base",
                        value = (proj.annualNetPay - base.annualNetPay).formatCurrency(),
                        valueColor = ProfitGreen
                    )
                }
            }
        }
    }

    item {
        SectionCard(title = "Modeled Annual Income", icon = Icons.Filled.Payments) {
            AnnualLine("Regular Pay", proj.annualRegularPay)
            if (proj.annualOvertimePay > 0) {
                AnnualLine("Overtime Pay", proj.annualOvertimePay, color = ProfitGreen)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            AnnualLine("Gross Pay", proj.annualGrossPay, bold = true)
        }
    }

    item {
        SectionCard(title = "Modeled Annual Taxes", icon = Icons.Filled.AccountBalance) {
            AnnualLine("Federal Income Tax", proj.annualFederalTax, color = LossRed)
            AnnualLine("State Income Tax", proj.annualStateTax, color = LossRed)
            if (proj.annualCountyTax > 0) {
                AnnualLine("County/Local Tax", proj.annualCountyTax, color = LossRed)
            }
            AnnualLine("Social Security", proj.annualSocialSecurity, color = LossRed)
            AnnualLine("Medicare", proj.annualMedicare, color = LossRed)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            AnnualLine("Total Taxes", proj.annualTotalTaxes, bold = true, color = LossRed)

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LabeledValue(
                    label = "Effective Rate",
                    value = proj.effectiveTaxRate.formatPercentage()
                )
                LabeledValue(
                    label = "Marginal Federal",
                    value = proj.marginalFederalRate.formatPercentage()
                )
            }
        }
    }

    if (proj.preTaxDeductionBreakdown.isNotEmpty() || proj.postTaxDeductionBreakdown.isNotEmpty()) {
        item {
            SectionCard(title = "Modeled Annual Deductions", icon = Icons.Filled.RemoveCircleOutline) {
                if (proj.preTaxDeductionBreakdown.isNotEmpty()) {
                    Text(
                        text = "Pre-Tax",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    proj.preTaxDeductionBreakdown.forEach { d ->
                        AnnualLine(d.name, d.amount)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    AnnualLine("Total Pre-Tax", proj.annualPreTaxDeductions, bold = true)
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (proj.postTaxDeductionBreakdown.isNotEmpty()) {
                    Text(
                        text = "Post-Tax",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    proj.postTaxDeductionBreakdown.forEach { d ->
                        AnnualLine(d.name, d.amount)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    AnnualLine("Total Post-Tax", proj.annualPostTaxDeductions, bold = true)
                }
            }
        }
    }

    // With OT vs Without OT comparison
    if (state.annualOvertimeHours > 0) {
        item {
            SectionCard(title = "Overtime Impact", icon = Icons.Filled.CompareArrows) {
                ComparisonRow("Gross Pay", base.annualGrossPay, proj.annualGrossPay)
                ComparisonRow("Federal Tax", base.annualFederalTax, proj.annualFederalTax)
                ComparisonRow("State Tax", base.annualStateTax, proj.annualStateTax)
                ComparisonRow("Social Security", base.annualSocialSecurity, proj.annualSocialSecurity)
                ComparisonRow("Medicare", base.annualMedicare, proj.annualMedicare)
                ComparisonRow("Total Taxes", base.annualTotalTaxes, proj.annualTotalTaxes)
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                ComparisonRow("Net Take Home", base.annualNetPay, proj.annualNetPay)

                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = ProfitGreen.copy(alpha = 0.1f)
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Net gain from ${proj.overtimeHoursUsed.toInt()} hrs OT",
                            style = MaterialTheme.typography.labelMedium,
                            color = ProfitGreen
                        )
                        MoneyText(
                            amount = proj.annualNetPay - base.annualNetPay,
                            style = MaterialTheme.typography.headlineSmall,
                            color = ProfitGreen
                        )
                        val extraTaxes = proj.annualTotalTaxes - base.annualTotalTaxes
                        val extraGross = proj.annualGrossPay - base.annualGrossPay
                        val otTaxRate = if (extraGross > 0) extraTaxes / extraGross else 0.0
                        Text(
                            text = "OT effective tax rate: ${otTaxRate.formatPercentage()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    item {
        SectionCard(title = "Modeled Annual Summary", icon = Icons.Filled.Summarize) {
            AnnualLine("Gross Pay", proj.annualGrossPay)
            AnnualLine("Pre-Tax Deductions", -proj.annualPreTaxDeductions, color = LossRed)
            AnnualLine("Total Taxes", -proj.annualTotalTaxes, color = LossRed)
            AnnualLine("Post-Tax Deductions", -proj.annualPostTaxDeductions, color = LossRed)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            AnnualLine("Net Take Home", proj.annualNetPay, bold = true, color = ProfitGreen)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.summaryContent(
    state: com.fiatlife.app.ui.viewmodel.SalaryState,
    viewModel: SalaryViewModel,
    statsViewAnnual: Boolean,
    onSummaryStatsAnnualChange: (Boolean) -> Unit,
) {
    val ytd = state.ytdSummary
    val annual = ytd?.annualExtrapolation
    val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)

    // Year selector + Log button
    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.setSummaryYear(state.summaryYear - 1) }) {
                    Icon(Icons.Filled.ChevronLeft, "Previous year")
                }
                Text(
                    text = state.summaryYear.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(
                    onClick = { viewModel.setSummaryYear(state.summaryYear + 1) },
                    enabled = state.summaryYear < currentYear
                ) {
                    Icon(Icons.Filled.ChevronRight, "Next year")
                }
            }
            Button(onClick = { viewModel.showLogPaycheck(null) }) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Log Paycheck")
            }
        }
    }

    if (ytd == null) {
        item {
            SectionCard(title = "No data yet", icon = Icons.Filled.Info) {
                Text(
                    text = "Set up your pay in the Model tab, then log paychecks to track " +
                            "year-to-date earnings, taxes, and deductions here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        payRateHistoryContent(state, viewModel)
        directDepositsContent(state, viewModel, DepositEditTarget.SUMMARY)
        return
    }

    // Hero + toggleable YTD / projected annual stats
    item {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Year-To-Date Net",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = if (ytd.source == YtdSummary.Source.LOGGED) "Logged" else "Estimated",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    MoneyText(
                        amount = ytd.netPay,
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (ytd.progressPercent / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "${ytd.progressPercent.toInt()}% of projected annual net " +
                                "(${ytd.annualNetTarget.formatCurrency()})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !statsViewAnnual,
                            onClick = { onSummaryStatsAnnualChange(false) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) { Text("Year To Date") }
                        SegmentedButton(
                            selected = statsViewAnnual,
                            onClick = { onSummaryStatsAnnualChange(true) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) { Text("Projected Annual") }
                    }
                }
            }

            if (!statsViewAnnual) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatTile("Gross YTD", ytd.grossPay.formatCurrency())
                    StatTile("Taxes YTD", ytd.totalTaxes.formatCurrency())
                    StatTile(
                        "Deductions",
                        (ytd.totalPreTaxDeductions + ytd.totalPostTaxDeductions).formatCurrency()
                    )
                    StatTile("OT hours", "%.1f".format(ytd.overtimeHours))
                }

                BreakdownCardCompose("Earnings", ytd.earnings, ytd.grossPay, negative = false)
                BreakdownCardCompose(
                    title = "Taxes",
                    lines = ytd.taxes,
                    total = ytd.totalTaxes,
                    negative = true,
                    footer = "Effective rate ${
                        if (ytd.grossPay > 0) "%.1f%%".format(ytd.totalTaxes / ytd.grossPay * 100) else "0.0%"
                    }"
                )
                if (ytd.preTaxDeductions.isNotEmpty()) {
                    BreakdownCardCompose(
                        "Pre-Tax Deductions",
                        ytd.preTaxDeductions,
                        ytd.totalPreTaxDeductions,
                        negative = true
                    )
                }
                if (ytd.postTaxDeductions.isNotEmpty()) {
                    BreakdownCardCompose(
                        "Post-Tax Deductions",
                        ytd.postTaxDeductions,
                        ytd.totalPostTaxDeductions,
                        negative = true
                    )
                }
                if (ytd.employerContributions.isNotEmpty()) {
                    BreakdownCardCompose(
                        "Employer Contributions",
                        ytd.employerContributions,
                        ytd.employerContributions.sumOf { it.amount },
                        negative = false
                    )
                }
            } else if (annual != null) {
                Text(
                    text = if (annual.source == AnnualExtrapolation.Source.LOGGED) {
                        "From your paychecks"
                    } else {
                        "Estimated"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (annual.source == AnnualExtrapolation.Source.LOGGED) {
                        ProfitGreen
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.align(Alignment.End)
                )
                Text(
                    text = if (annual.source == AnnualExtrapolation.Source.LOGGED) {
                        "YTD actuals from ${annual.basedOnPaychecks} logged paycheck" +
                                if (annual.basedOnPaychecks == 1) "" else "s" +
                                ", plus latest pay rate × ${annual.remainingPaychecksProjected} " +
                                "remaining pay period" +
                                if (annual.remainingPaychecksProjected == 1) "" else "s" + "."
                    } else {
                        "Log paychecks to project from actual stubs, or use Model settings until then."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (annual.source == AnnualExtrapolation.Source.LOGGED) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = buildString {
                                append("Project average OT forward")
                                if (annual.averageOvertimeHoursPerPaycheck > 0.0) {
                                    append(" (")
                                    append("%.1f".format(annual.averageOvertimeHoursPerPaycheck))
                                    append(" hrs/paycheck so far)")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = state.projectOvertimeForward,
                            onCheckedChange = { viewModel.setProjectOvertimeForward(it) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatTile("Projected net", annual.annualNetPay.formatCurrency())
                    StatTile("Projected gross", annual.annualGrossPay.formatCurrency())
                    StatTile("Projected taxes", annual.annualTotalTaxes.formatCurrency())
                    StatTile("Per paycheck", annual.perPaycheckNet.formatCurrency())
                }

                SectionCard(title = "Annual pace", icon = Icons.Filled.TrendingUp) {
                    Text(
                        text = buildString {
                            append("${annual.scheduledPaychecksInYear} pay periods this year · ")
                            append("%.1f".format(annual.overtimeHours))
                            append(" projected OT hours")
                            if (annual.source == AnnualExtrapolation.Source.LOGGED &&
                                !annual.projectOvertimeForward
                            ) {
                                append(" (logged only)")
                            }
                            append(" · ")
                            append(annual.annualTotalDeductions.formatCurrency())
                            append(" projected deductions")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FederalTaxReturnSection(config = state.config, annual = annual)

                if (annual.earnings.isNotEmpty()) {
                    BreakdownCardCompose("Earnings", annual.earnings, annual.annualGrossPay, negative = false)
                }
                if (annual.taxes.isNotEmpty()) {
                    BreakdownCardCompose(
                        title = "Taxes",
                        lines = annual.taxes,
                        total = annual.annualTotalTaxes,
                        negative = true,
                        footer = "Effective rate ${annual.effectiveTaxRate.formatPercentage()}"
                    )
                }
                if (annual.preTaxDeductions.isNotEmpty()) {
                    BreakdownCardCompose(
                        "Pre-Tax Deductions",
                        annual.preTaxDeductions,
                        annual.annualPreTaxDeductions,
                        negative = true
                    )
                }
                if (annual.postTaxDeductions.isNotEmpty()) {
                    BreakdownCardCompose(
                        "Post-Tax Deductions",
                        annual.postTaxDeductions,
                        annual.annualPostTaxDeductions,
                        negative = true
                    )
                }
                if (annual.employerContributions.isNotEmpty()) {
                    BreakdownCardCompose(
                        "Employer Contributions",
                        annual.employerContributions,
                        annual.employerContributions.sumOf { it.amount },
                        negative = false
                    )
                }
            }
        }
    }

    if (!statsViewAnnual) {
    item {
        SectionCard(title = "Paycheck Schedule", icon = Icons.Filled.CalendarMonth) {
            var firstPaydayOfYear by remember(state.config.firstPaydayOfYearMillis) {
                mutableStateOf(state.config.firstPaydayOfYearMillis?.let { formatIsoDate(it) } ?: "")
            }
            var payFreqExpanded by remember { mutableStateOf(false) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = payFreqExpanded,
                    onExpandedChange = { payFreqExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = state.config.payFrequency.name.lowercase()
                            .replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Pay frequency") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(payFreqExpanded) },
                        modifier = Modifier.menuAnchor(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )
                    ExposedDropdownMenu(
                        expanded = payFreqExpanded,
                        onDismissRequest = { payFreqExpanded = false }
                    ) {
                        PayFrequency.entries.forEach { freq ->
                            DropdownMenuItem(
                                text = {
                                    Text(freq.name.lowercase().replaceFirstChar { it.uppercase() })
                                },
                                onClick = {
                                    viewModel.updatePayFrequency(freq)
                                    payFreqExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = firstPaydayOfYear,
                    onValueChange = {
                        firstPaydayOfYear = it.take(10)
                        viewModel.updateFirstPaydayOfYear(parseIsoDate(it))
                    },
                    label = { Text("First payday") },
                    placeholder = { Text("2026-01-09") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            Text(
                text = when (state.config.payFrequency) {
                    PayFrequency.WEEKLY, PayFrequency.BIWEEKLY ->
                        "Pay frequency and first payday are required for weekly and biweekly schedules. " +
                                "Logged paychecks on matching dates are recognized automatically."
                    else ->
                        "Pay frequency drives YTD counting and Model calculations. First payday improves monthly estimates."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }

    // Paycheck log
    item {
        SectionCard(
            title = "Paycheck Log",
            icon = Icons.Filled.ReceiptLong,
            action = {
                val missing = SalarySummary.missingPaydaysForYear(state.config, state.summaryYear)
                val canDetectMissing =
                    SalarySummary.canDetectMissingPaychecks(state.config, state.summaryYear)
                TextButton(
                    onClick = { viewModel.generateMissingPaycheckLogs(state.summaryYear) },
                    enabled = missing.isNotEmpty() &&
                        canDetectMissing &&
                        state.calculation.grossPay > 0.0
                ) {
                    Text(
                        if (missing.isEmpty()) "Generate missing"
                        else "Generate missing (${missing.size})"
                    )
                }
            }
        ) {
            val logs = SalarySummary.logsForYear(state.config, state.summaryYear)
            val missing = SalarySummary.missingPaydaysForYear(state.config, state.summaryYear)
            val canDetectMissing = SalarySummary.canDetectMissingPaychecks(state.config, state.summaryYear)

            Text(
                text = buildString {
                    append("${ytd.scheduledPaychecksYtd} paycheck")
                    if (ytd.scheduledPaychecksYtd != 1) append("s")
                    append(" received · ${ytd.scheduledPaychecksInYear} expected this year")
                    if (ytd.remainingPaychecks > 0) {
                        append(" · ${ytd.remainingPaychecks} remaining")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            if (logs.isEmpty() && missing.isEmpty()) {
                Text(
                    text = if (canDetectMissing) {
                        "No paychecks logged for ${state.summaryYear} yet."
                    } else {
                        "No paychecks logged for ${state.summaryYear} yet. Set your first payday of the year " +
                                "above to track missing checks."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                if (missing.isNotEmpty()) {
                    Text(
                        text = "${missing.size} missing paycheck${if (missing.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = WarningAmber
                    )
                    Spacer(Modifier.height(4.dp))
                    missing.forEach { payday ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    formatIsoDate(payday),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "Not logged",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { viewModel.showLogPaycheck(payDateHint = payday) }) {
                                Text("Log")
                            }
                        }
                    }
                    if (logs.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = "Logged",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }
                logs.forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(formatIsoDate(entry.payDate), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                "Gross ${entry.grossPay.formatCurrency()}" +
                                        (entry.overtimeHours?.let { if (it > 0) " · ${it} OT hrs" else "" } ?: "") +
                                        (if (entry.autoGenerated == true) " · Estimated" else "") +
                                        (entry.notes?.let { if (it.isNotBlank()) " · $it" else "" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        MoneyText(amount = entry.netPay, style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { viewModel.showLogPaycheck(entry) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Edit, "Edit", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { viewModel.removeLogPaycheck(entry.id) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.Delete, "Delete", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

    payRateHistoryContent(state, viewModel)

    directDepositsContent(state, viewModel, DepositEditTarget.SUMMARY)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.payRateHistoryContent(
    state: com.fiatlife.app.ui.viewmodel.SalaryState,
    viewModel: SalaryViewModel
) {
    val inferred = SalarySummary.inferPayRatesFromLogs(state.config, state.summaryYear)
    val inferredById = inferred.raises.associateBy { it.change.id }
    val sorted = state.config.payRateHistory
        .filter { change ->
            SalarySummary.yearOf(change.effectiveDate) == state.summaryYear ||
                !change.id.startsWith(SalarySummary.INFERRED_RAISE_ID_PREFIX)
        }
        .sortedByDescending { it.effectiveDate }

    item {
        SectionCard(
            title = "Pay Rate History (Raises)",
            icon = Icons.Filled.TrendingUp,
            action = {
                IconButton(onClick = { viewModel.addRaise() }) {
                    Icon(Icons.Filled.Add, "Add raise")
                }
            }
        ) {
            Text(
                text = "Auto-filled from logged paychecks when regular pay increases. Starting " +
                        "pay comes from your first paycheck of the year.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            inferred.startingRate?.let { start ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Starting pay (${state.summaryYear})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "From first paycheck on ${formatIsoDate(start.effectiveDate)}: " +
                            "${start.regularGrossPerPaycheck.formatCurrency()} per paycheck" +
                            if (start.payType == PayType.SALARY) {
                                " · ${start.annualSalary.formatCurrency()} annual"
                            } else {
                                " · ${start.hourlyRate.formatCurrency()}/hr"
                            },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (sorted.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
            }
            sorted.forEach { change ->
                inferredById[change.id]?.let { inferredRaise ->
                    Text(
                        text = "+${"%.1f".format(inferredRaise.percentIncrease)}% raise · +" +
                                inferredRaise.perPaycheckIncrease.formatCurrency() + " per paycheck",
                        style = MaterialTheme.typography.labelMedium,
                        color = ProfitGreen,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                RaiseRow(
                    change = change,
                    onChange = { viewModel.updateRaise(it) },
                    onDelete = { viewModel.removeRaise(change.id) }
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.directDepositsContent(
    state: com.fiatlife.app.ui.viewmodel.SalaryState,
    viewModel: SalaryViewModel,
    target: DepositEditTarget
) {
    val calc = state.calculation
    val allocations = when (target) {
        DepositEditTarget.SUMMARY -> calc.depositAllocations
        DepositEditTarget.WHATIF -> {
            val deposits = state.whatIfDirectDeposits ?: state.config.directDeposits
            PaycheckCalculator.calculateDepositAllocations(deposits, calc.netPay)
        }
    }
    val unallocated = calc.netPay - allocations.sumOf { it.calculatedAmount }
    val customized = target == DepositEditTarget.WHATIF && state.whatIfDirectDeposits != null
    val hint = when (target) {
        DepositEditTarget.SUMMARY -> null
        DepositEditTarget.WHATIF ->
            if (customized) {
                "Custom projection splits — reset to match Summary anytime."
            } else {
                "Mirrors your Summary splits until you change amounts or percentages here."
            }
    }

    item {
        SectionCard(
            title = "Direct Deposits",
            icon = Icons.Filled.AccountBalanceWallet,
            action = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (customized) {
                        TextButton(onClick = { viewModel.resetWhatIfDirectDeposits() }) {
                            Text("Reset")
                        }
                    }
                    IconButton(onClick = { viewModel.showAddDeposit(target) }) {
                        Icon(Icons.Filled.Add, "Add deposit")
                    }
                }
            }
        ) {
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
            }
            if (allocations.isEmpty()) {
                Text(
                    text = "Set up how your take home pay is split across accounts",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                allocations.forEach { alloc ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = alloc.deposit.accountName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${alloc.deposit.bankName} - ${alloc.deposit.accountType.displayName}" +
                                        if (alloc.deposit.isRemainder) " (Remainder)" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        MoneyText(
                            amount = alloc.calculatedAmount,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        IconButton(
                            onClick = { viewModel.showEditDeposit(alloc.deposit, target) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.Edit, "Edit", modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = { viewModel.removeDeposit(alloc.deposit.id, target) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Outlined.Delete, "Remove", modifier = Modifier.size(18.dp))
                        }
                    }
                }
                if (unallocated > 0.01) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Unallocated",
                            style = MaterialTheme.typography.bodyMedium,
                            color = WarningAmber,
                            fontWeight = FontWeight.Medium
                        )
                        MoneyText(
                            amount = unallocated,
                            style = MaterialTheme.typography.bodyLarge,
                            color = WarningAmber
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BreakdownCardCompose(
    title: String,
    lines: List<YtdBreakdownLine>,
    total: Double,
    negative: Boolean,
    footer: String? = null
) {
    if (lines.isEmpty()) return
    SectionCard(title = title, icon = Icons.Filled.Receipt, action = {
        Text(
            text = (if (negative) "−" else "") + total.formatCurrency(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (negative) LossRed else MaterialTheme.colorScheme.onSurface
        )
    }) {
        lines.forEach { line ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = line.label + if (line.hours > 0) " · %.1f hrs".format(line.hours) else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = (if (negative) "−" else "") + line.amount.formatCurrency(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (negative) LossRed else MaterialTheme.colorScheme.onSurface
                )
            }
        }
        if (footer != null) {
            Spacer(Modifier.height(4.dp))
            Text(footer, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RaiseRow(
    change: PayRateChange,
    onChange: (PayRateChange) -> Unit,
    onDelete: () -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            var dateText by remember(change.id, change.effectiveDate) {
                mutableStateOf(formatIsoDate(change.effectiveDate))
            }
            OutlinedTextField(
                value = dateText,
                onValueChange = {
                    dateText = it.take(10)
                    parseIsoDate(it)?.let { ms -> onChange(change.copy(effectiveDate = ms)) }
                },
                label = { Text("Effective date") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            val isSalary = change.payType == PayType.SALARY
            var rateText by remember(change.id) {
                mutableStateOf(
                    (if (isSalary) change.annualSalary else change.hourlyRate)
                        ?.let { if (it > 0) it.toString() else "" } ?: ""
                )
            }
            OutlinedTextField(
                value = rateText,
                onValueChange = {
                    rateText = it
                    val v = it.toDoubleOrNull()
                    onChange(if (isSalary) change.copy(annualSalary = v) else change.copy(hourlyRate = v))
                },
                label = { Text(if (isSalary) "Salary" else "Rate") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.Delete, "Remove", modifier = Modifier.size(18.dp))
            }
        }
        var noteText by remember(change.id) { mutableStateOf(change.note ?: "") }
        OutlinedTextField(
            value = noteText,
            onValueChange = { noteText = it; onChange(change.copy(note = it)) },
            label = { Text("Note (e.g. merit raise)") },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            singleLine = true,
            shape = MaterialTheme.shapes.medium
        )
    }
}

private data class LogLinesUi(
    val earnings: List<PaycheckLineItem>,
    val taxes: List<PaycheckLineItem>,
    val preTax: List<PaycheckLineItem>,
    val postTax: List<PaycheckLineItem>
)

private fun linesFromEntryAndroid(entry: PaycheckLogEntry?): LogLinesUi {
    if (entry == null) return LogLinesUi(emptyList(), emptyList(), emptyList(), emptyList())
    fun id() = UUID.randomUUID().toString()
    val earnings = if (entry.earnings.isNotEmpty()) entry.earnings
    else listOf(PaycheckLineItem(id(), "Regular", entry.grossPay))
    val taxes = if (entry.taxes.isNotEmpty()) entry.taxes
    else entry.totalTaxes?.let { listOf(PaycheckLineItem(id(), "Taxes", it)) } ?: emptyList()
    val preTax = if (entry.preTaxDeductions.isNotEmpty()) entry.preTaxDeductions
    else entry.totalPreTaxDeductions?.let { listOf(PaycheckLineItem(id(), "Pre-tax", it)) } ?: emptyList()
    val postTax = if (entry.postTaxDeductions.isNotEmpty()) entry.postTaxDeductions
    else entry.totalPostTaxDeductions?.let { listOf(PaycheckLineItem(id(), "Post-tax", it)) } ?: emptyList()
    return LogLinesUi(earnings, taxes, preTax, postTax)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogPaycheckDialog(
    editing: PaycheckLogEntry?,
    payDateHint: Long?,
    calculation: PaycheckCalculation,
    config: SalaryConfig,
    onDismiss: () -> Unit,
    onSave: (PaycheckLogEntry) -> Unit
) {
    val initial = remember(editing) { linesFromEntryAndroid(editing) }
    var payDate by remember(editing, payDateHint) {
        mutableStateOf(formatIsoDate(editing?.payDate ?: payDateHint ?: System.currentTimeMillis()))
    }
    var earnings by remember { mutableStateOf(initial.earnings) }
    var taxes by remember { mutableStateOf(initial.taxes) }
    var preTax by remember { mutableStateOf(initial.preTax) }
    var postTax by remember { mutableStateOf(initial.postTax) }
    var notes by remember { mutableStateOf(editing?.notes ?: "") }

    val gross = earnings.sumOf { it.amount }
    val totalTaxes = taxes.sumOf { it.amount }
    val totalPre = preTax.sumOf { it.amount }
    val totalPost = postTax.sumOf { it.amount }
    val net = gross - totalTaxes - totalPre - totalPost

    val lastEntry = config.paycheckLog.filter { it.id != editing?.id }.maxByOrNull { it.payDate }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (editing == null) "Log Paycheck" else "Edit Paycheck",
                    style = MaterialTheme.typography.titleLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (calculation.grossPay > 0) {
                        OutlinedButton(onClick = {
                            val l = SalarySummary.lineItemsFromCalculation(calculation, config.overtimeHours)
                            earnings = l.earnings; taxes = l.taxes
                            preTax = l.preTaxDeductions; postTax = l.postTaxDeductions
                        }) { Text("Prefill calc") }
                    }
                    if (lastEntry != null) {
                        OutlinedButton(onClick = {
                            val l = linesFromEntryAndroid(lastEntry)
                            fun reid(list: List<PaycheckLineItem>) = list.map { it.copy(id = UUID.randomUUID().toString()) }
                            earnings = reid(l.earnings); taxes = reid(l.taxes)
                            preTax = reid(l.preTax); postTax = reid(l.postTax)
                        }) { Text("Copy last") }
                    }
                }
                OutlinedTextField(
                    value = payDate,
                    onValueChange = { payDate = it.take(10) },
                    label = { Text("Pay date (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(Modifier.padding(12.dp)) {
                        SummaryLine("Gross", gross, false)
                        SummaryLine("Taxes", totalTaxes, true)
                        SummaryLine("Deductions", totalPre + totalPost, true)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Net", fontWeight = FontWeight.SemiBold)
                            MoneyText(amount = net, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                LogLineSection(
                    title = "Earnings",
                    lines = earnings,
                    showHours = true,
                    useEarningsPresets = true
                ) { earnings = it }
                LogLineSection("Taxes", taxes, showHours = false) { taxes = it }
                LogLineSection("Pre-Tax Deductions", preTax, showHours = false) { preTax = it }
                LogLineSection("Post-Tax Deductions", postTax, showHours = false) { postTax = it }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = {
                            val dateMs = parseIsoDate(payDate) ?: System.currentTimeMillis()
                            val otRegex = Regex("overtime|^ot\\b", RegexOption.IGNORE_CASE)
                            val otLine = earnings.firstOrNull { otRegex.containsMatchIn(it.label) }
                            fun clean(list: List<PaycheckLineItem>) = list
                                .map { it.copy(label = it.label.ifBlank { "Other" }) }
                                .filter { it.amount != 0.0 || (it.hours ?: 0.0) != 0.0 }
                            onSave(
                                PaycheckLogEntry(
                                    id = editing?.id ?: "",
                                    payDate = dateMs,
                                    grossPay = gross,
                                    netPay = net,
                                    totalTaxes = totalTaxes,
                                    totalPreTaxDeductions = totalPre,
                                    totalPostTaxDeductions = totalPost,
                                    overtimeHours = otLine?.hours,
                                    notes = notes.ifBlank { null },
                                    earnings = clean(earnings),
                                    taxes = clean(taxes),
                                    preTaxDeductions = clean(preTax),
                                    postTaxDeductions = clean(postTax)
                                )
                            )
                        },
                        enabled = earnings.isNotEmpty()
                    ) { Text(if (editing == null) "Log" else "Update") }
                }
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, amount: Double, negative: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = (if (negative) "−" else "") + amount.formatCurrency(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (negative) LossRed else MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogLineSection(
    title: String,
    lines: List<PaycheckLineItem>,
    showHours: Boolean,
    useEarningsPresets: Boolean = false,
    onLinesChange: (List<PaycheckLineItem>) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    lines.sumOf { it.amount }.formatCurrency(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = {
                    onLinesChange(lines + PaycheckLineItem(id = UUID.randomUUID().toString(), label = ""))
                }) { Text("+ Add") }
            }
        }
        lines.forEach { line ->
            LineEditorRow(
                line = line,
                showHours = showHours,
                useEarningsPresets = useEarningsPresets,
                onChange = { updated -> onLinesChange(lines.map { if (it.id == line.id) updated else it }) },
                onRemove = { onLinesChange(lines.filter { it.id != line.id }) }
            )
        }
    }
}

@Composable
private fun EarningsLabelField(
    label: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val presets = SalarySummary.earningsCategories
    val isPreset = label in presets
    var pickExpanded by remember { mutableStateOf(false) }

    if (!isPreset) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedTextField(
                value = label,
                onValueChange = onChange,
                label = { Text("Label") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                shape = MaterialTheme.shapes.small
            )
            ExposedDropdownMenuBox(
                expanded = pickExpanded,
                onExpandedChange = { pickExpanded = it }
            ) {
                OutlinedTextField(
                    value = "Category",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .width(104.dp)
                        .menuAnchor(),
                    textStyle = MaterialTheme.typography.bodySmall,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(pickExpanded) },
                    shape = MaterialTheme.shapes.small
                )
                ExposedDropdownMenu(
                    expanded = pickExpanded,
                    onDismissRequest = { pickExpanded = false }
                ) {
                    presets.forEach { preset ->
                        DropdownMenuItem(
                            text = { Text(preset) },
                            onClick = {
                                onChange(preset)
                                pickExpanded = false
                            }
                        )
                    }
                }
            }
        }
    } else {
        var presetExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = presetExpanded,
            onExpandedChange = { presetExpanded = it },
            modifier = modifier
        ) {
            OutlinedTextField(
                value = label,
                onValueChange = {},
                readOnly = true,
                label = { Text("Label") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(presetExpanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                shape = MaterialTheme.shapes.small
            )
            ExposedDropdownMenu(
                expanded = presetExpanded,
                onDismissRequest = { presetExpanded = false }
            ) {
                presets.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text(preset) },
                        onClick = {
                            onChange(preset)
                            presetExpanded = false
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Custom…") },
                    onClick = {
                        onChange("")
                        presetExpanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun LineEditorRow(
    line: PaycheckLineItem,
    showHours: Boolean,
    useEarningsPresets: Boolean = false,
    onChange: (PaycheckLineItem) -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (useEarningsPresets) {
            EarningsLabelField(
                label = line.label,
                onChange = { onChange(line.copy(label = it)) },
                modifier = Modifier.weight(1f)
            )
        } else {
            OutlinedTextField(
                value = line.label,
                onValueChange = { onChange(line.copy(label = it)) },
                label = { Text("Label") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                shape = MaterialTheme.shapes.small
            )
        }
        if (showHours) {
            var hoursText by remember(line.id) {
                mutableStateOf(line.hours?.let { if (it > 0) it.toString() else "" } ?: "")
            }
            OutlinedTextField(
                value = hoursText,
                onValueChange = { hoursText = it; onChange(line.copy(hours = it.toDoubleOrNull())) },
                label = { Text("hrs") },
                modifier = Modifier.width(64.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = MaterialTheme.shapes.small
            )
        }
        var amtText by remember(line.id) {
            mutableStateOf(if (line.amount != 0.0) line.amount.toString() else "")
        }
        OutlinedTextField(
            value = amtText,
            onValueChange = { amtText = it; onChange(line.copy(amount = it.toDoubleOrNull() ?: 0.0)) },
            label = { Text("$") },
            modifier = Modifier.width(96.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = MaterialTheme.shapes.small
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Outlined.Delete, "Remove", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun TaxLine(
    label: String,
    amount: Double,
    bold: Boolean = false,
    grossForPercentage: Double? = null
) {
    val percentageText = when {
        grossForPercentage != null && grossForPercentage > 0 && amount >= 0 ->
            " (${(amount / grossForPercentage * 100).formatPercentage()})"
        else -> ""
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = amount.formatCurrency() + percentageText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                color = LossRed
            )
        }
    }
}

@Composable
private fun TaxLineWithRate(
    label: String,
    amount: Double,
    rate: Double
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
            if (rate > 0) {
                Text(
                    text = "(${(rate * 100).formatPercentage(1)})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = amount.formatCurrency(),
            style = MaterialTheme.typography.bodyMedium,
            color = LossRed
        )
    }
}

@Composable
private fun EditableTaxLine(
    label: String,
    amount: Double,
    defaultRate: Double,
    customRate: Double?,
    onRateChange: (Double?) -> Unit
) {
    val displayRate = customRate ?: defaultRate
    var editing by remember { mutableStateOf(false) }
    var rateText by remember(customRate, defaultRate) {
        mutableStateOf("%.2f".format(displayRate * 100))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        if (editing) {
            OutlinedTextField(
                value = rateText,
                onValueChange = { input ->
                    rateText = input
                    val pct = input.toDoubleOrNull()
                    if (pct != null) {
                        onRateChange(pct / 100.0)
                    }
                },
                modifier = Modifier.width(80.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                suffix = { Text("%", style = MaterialTheme.typography.bodySmall) },
                shape = MaterialTheme.shapes.small
            )
            IconButton(
                onClick = {
                    editing = false
                    if (rateText.toDoubleOrNull() == null) {
                        onRateChange(null)
                        rateText = "%.2f".format(defaultRate * 100)
                    }
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Filled.Check, contentDescription = "Done", modifier = Modifier.size(16.dp))
            }
        } else {
            TextButton(
                onClick = { editing = true },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text(
                    text = "%.2f%%".format(displayRate * 100),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (customRate != null)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (customRate != null) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = amount.formatCurrency(),
                style = MaterialTheme.typography.bodyMedium,
                color = LossRed
            )
        }
    }
}

@Composable
private fun DeductionRow(
    deduction: Deduction,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = deduction.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = deduction.category.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = if (deduction.isPercentage) "${deduction.amount}%" else deduction.amount.formatCurrency(),
            style = MaterialTheme.typography.bodyMedium
        )
        Row {
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Edit, "Edit", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.Delete, "Delete", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaxSetupDialog(
    config: SalaryConfig,
    onDismiss: () -> Unit,
    onFilingStatus: (FilingStatus) -> Unit,
    onState: (String) -> Unit,
    onCounty: (String) -> Unit
) {
    var filingExpanded by remember { mutableStateOf(false) }
    var stateCode by remember(config.state) { mutableStateOf(config.state) }
    var countyName by remember(config.county) { mutableStateOf(config.county) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Tax setup",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Filing status and state drive federal and state withholding estimates. " +
                            "Override individual rates on the Model tab if your stub differs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                ExposedDropdownMenuBox(
                    expanded = filingExpanded,
                    onExpandedChange = { filingExpanded = it }
                ) {
                    OutlinedTextField(
                        value = config.filingStatus.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Filing status") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(filingExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )
                    ExposedDropdownMenu(
                        expanded = filingExpanded,
                        onDismissRequest = { filingExpanded = false }
                    ) {
                        FilingStatus.entries.forEach { status ->
                            DropdownMenuItem(
                                text = { Text(status.displayName) },
                                onClick = {
                                    onFilingStatus(status)
                                    filingExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = stateCode,
                    onValueChange = {
                        stateCode = it.uppercase().take(2)
                        onState(stateCode)
                    },
                    label = { Text("State") },
                    placeholder = { Text("CA") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    supportingText = {
                        Text("Required for state income tax estimates.")
                    }
                )

                OutlinedTextField(
                    value = countyName,
                    onValueChange = {
                        countyName = it
                        onCounty(it)
                    },
                    label = { Text("County") },
                    placeholder = { Text("Optional — for local tax label") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun DeductionDialog(
    deduction: Deduction?,
    isPreTax: Boolean,
    onDismiss: () -> Unit,
    onSave: (Deduction) -> Unit
) {
    var name by remember { mutableStateOf(deduction?.name ?: "") }
    var amount by remember { mutableStateOf(deduction?.amount?.toString() ?: "") }
    var isPercentage by remember { mutableStateOf(deduction?.isPercentage ?: false) }
    var selectedCategory by remember {
        mutableStateOf(deduction?.category ?: DeductionCategory.OTHER)
    }
    var categoryExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (deduction == null) "Add ${if (isPreTax) "Pre-Tax" else "Post-Tax"} Deduction" else "Edit Deduction")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )

                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedCategory.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = MaterialTheme.shapes.medium
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        DeductionCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.displayName) },
                                onClick = {
                                    selectedCategory = cat
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isPercentage) {
                        PercentageTextField(
                            value = amount,
                            onValueChange = { amount = it },
                            label = "Amount",
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        CurrencyTextField(
                            value = amount,
                            onValueChange = { amount = it },
                            label = "Amount",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    FilterChip(
                        selected = isPercentage,
                        onClick = { isPercentage = !isPercentage },
                        label = { Text("%") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val d = Deduction(
                        id = deduction?.id ?: "",
                        name = name,
                        amount = amount.toDoubleOrNull() ?: 0.0,
                        type = if (isPreTax) DeductionType.PRE_TAX else DeductionType.POST_TAX,
                        category = selectedCategory,
                        isPercentage = isPercentage,
                        isEnabled = true
                    )
                    onSave(d)
                },
                enabled = name.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DepositDialog(
    deposit: DirectDeposit?,
    onDismiss: () -> Unit,
    onSave: (DirectDeposit) -> Unit
) {
    var accountName by remember { mutableStateOf(deposit?.accountName ?: "") }
    var bankName by remember { mutableStateOf(deposit?.bankName ?: "") }
    var amount by remember { mutableStateOf(deposit?.amount?.toString() ?: "") }
    var isPercentage by remember { mutableStateOf(deposit?.isPercentage ?: false) }
    var isRemainder by remember { mutableStateOf(deposit?.isRemainder ?: false) }
    var selectedType by remember { mutableStateOf(deposit?.accountType ?: AccountType.CHECKING) }
    var typeExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (deposit == null) "Add Direct Deposit" else "Edit Direct Deposit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = accountName,
                    onValueChange = { accountName = it },
                    label = { Text("Account Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
                OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it },
                    label = { Text("Bank Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )

                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedType.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Account Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = MaterialTheme.shapes.medium
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        AccountType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName) },
                                onClick = {
                                    selectedType = type
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isRemainder,
                        onClick = { isRemainder = !isRemainder },
                        label = { Text("Remainder") }
                    )
                }

                if (!isRemainder) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isPercentage) {
                            PercentageTextField(
                                value = amount,
                                onValueChange = { amount = it },
                                label = "Amount",
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            CurrencyTextField(
                                value = amount,
                                onValueChange = { amount = it },
                                label = "Amount",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        FilterChip(
                            selected = isPercentage,
                            onClick = { isPercentage = !isPercentage },
                            label = { Text("%") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        DirectDeposit(
                            id = deposit?.id ?: "",
                            accountName = accountName,
                            bankName = bankName,
                            accountType = selectedType,
                            amount = if (isRemainder) 0.0 else (amount.toDoubleOrNull() ?: 0.0),
                            isPercentage = isPercentage,
                            isRemainder = isRemainder,
                            sortOrder = deposit?.sortOrder ?: 0
                        )
                    )
                },
                enabled = accountName.isNotBlank() && (isRemainder || (amount.toDoubleOrNull() ?: 0.0) > 0)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun FederalTaxReturnSection(
    config: SalaryConfig,
    annual: AnnualExtrapolation
) {
    val projection = remember(config, annual) {
        SalarySummary.projectFederalTaxReturn(config, annual)
    }
    val filingLabel = when (config.filingStatus) {
        FilingStatus.SINGLE -> "Single"
        FilingStatus.MARRIED_FILING_JOINTLY -> "Married filing jointly"
        FilingStatus.MARRIED_FILING_SEPARATELY -> "Married filing separately"
        FilingStatus.HEAD_OF_HOUSEHOLD -> "Head of household"
    }

    SectionCard(title = "Projected Federal Return", icon = Icons.Filled.Description) {
        if (projection.annualGross <= 0.0) {
            Text(
                text = "Set your pay rate and tax info to estimate federal withholding vs tax owed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@SectionCard
        }

        Text(
            text = "W-2 estimate for $filingLabel using the standard deduction. " +
                    "Excludes credits, other income, and itemized deductions.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val refund = projection.refundOrBalance
        val (outcomeLabel, outcomeAmount, outcomePositive) = when {
            refund > 1.0 -> Triple("Estimated refund", refund, true)
            refund < -1.0 -> Triple("Estimated balance due", kotlin.math.abs(refund), false)
            else -> Triple("On track", 0.0, true)
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = outcomeLabel.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (outcomeAmount > 0.0) {
                    MoneyText(
                        amount = outcomeAmount,
                        style = MaterialTheme.typography.displaySmall,
                        color = if (outcomePositive) ProfitGreen else LossRed
                    )
                } else {
                    Text(
                        text = "—",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        ReturnLine("Projected wages", projection.annualGross)
        if (projection.annualPreTaxDeductions > 0.0) {
            ReturnLine("Pre-tax deductions", projection.annualPreTaxDeductions, negative = true)
        }
        ReturnLine("Adjusted gross income", projection.adjustedGrossIncome, bold = true)
        ReturnLine(
            "Standard deduction ($filingLabel)",
            projection.standardDeduction,
            negative = true
        )
        ReturnLine("Federal taxable income", projection.federalTaxableIncome, bold = true)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        ReturnLine("Estimated federal tax owed", projection.estimatedFederalTaxOwed)
        ReturnLine("Federal tax withheld", projection.federalWithheld)
    }
}

@Composable
private fun ReturnLine(
    label: String,
    amount: Double,
    negative: Boolean = false,
    bold: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = (if (negative && amount > 0.0) "−" else "") + amount.formatCurrency(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            color = if (negative && amount > 0.0) LossRed else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun AnnualLine(
    label: String,
    amount: Double,
    bold: Boolean = false,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal
        )
        Text(
            text = amount.formatCurrency(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            color = color
        )
    }
}

@Composable
private fun ComparisonRow(label: String, baseValue: Double, withOtValue: Double) {
    val diff = withOtValue - baseValue
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = baseValue.formatCurrency(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
        Text(
            text = withOtValue.formatCurrency(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
        Text(
            text = (if (diff >= 0) "+" else "") + diff.formatCurrency(),
            style = MaterialTheme.typography.bodySmall,
            color = if (label.contains("Tax", ignoreCase = true) && diff > 0) LossRed else ProfitGreen,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

private fun parseIsoDate(input: String): Long? {
    val value = input.trim()
    if (value.isEmpty()) return null
    val parts = value.split("-")
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].toIntOrNull() ?: return null
    val cal = java.util.Calendar.getInstance()
    cal.set(java.util.Calendar.YEAR, year)
    cal.set(java.util.Calendar.MONTH, (month - 1).coerceIn(0, 11))
    cal.set(java.util.Calendar.DAY_OF_MONTH, day.coerceAtLeast(1))
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun formatIsoDate(millis: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    return String.format(
        java.util.Locale.US,
        "%04d-%02d-%02d",
        cal.get(java.util.Calendar.YEAR),
        cal.get(java.util.Calendar.MONTH) + 1,
        cal.get(java.util.Calendar.DAY_OF_MONTH)
    )
}
