package com.fiatlife.app.ui.screens.salary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fiatlife.app.domain.model.*
import com.fiatlife.app.ui.components.*
import com.fiatlife.app.ui.theme.*
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
        item {
            Text(
                text = "Paycheck Calculator",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Tab toggle
        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = state.activeTab == SalaryTab.PAYCHECK,
                    onClick = { viewModel.setActiveTab(SalaryTab.PAYCHECK) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("Per Paycheck") }
                SegmentedButton(
                    selected = state.activeTab == SalaryTab.ANNUAL,
                    onClick = { viewModel.setActiveTab(SalaryTab.ANNUAL) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("Annual Projection") }
            }
        }

        if (state.activeTab == SalaryTab.PAYCHECK) {
            paycheckContent(state, calc, viewModel)
        } else {
            annualContent(state, viewModel)
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
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
private fun androidx.compose.foundation.lazy.LazyListScope.paycheckContent(
    state: com.fiatlife.app.ui.viewmodel.SalaryState,
    calc: PaycheckCalculation,
    viewModel: SalaryViewModel
) {
    // Pay summary card
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
                    text = "Net Take Home",
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
                    StatItem("Annual Net", calc.annualizedNet.formatCurrency())
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
            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = overtimeMultiplier,
                    onValueChange = {
                        overtimeMultiplier = it
                        it.toDoubleOrNull()?.let { v -> viewModel.updateOvertimeMultiplier(v) }
                    },
                    label = { Text("OT Multiplier") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = state.config.payFrequency.name.lowercase()
                            .replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Frequency") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        PayFrequency.entries.forEach { freq ->
                            DropdownMenuItem(
                                text = { Text(freq.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    viewModel.updatePayFrequency(freq)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

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

    // Tax Configuration
    item {
        SectionCard(title = "Tax Configuration", icon = Icons.Filled.AccountBalance) {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = state.config.filingStatus.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Filing Status") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    FilingStatus.entries.forEach { status ->
                        DropdownMenuItem(
                            text = { Text(status.displayName) },
                            onClick = {
                                viewModel.updateFilingStatus(status)
                                expanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            var stateCode by remember(state.config.state) {
                mutableStateOf(state.config.state)
            }
            var countyName by remember(state.config.county) {
                mutableStateOf(state.config.county)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = stateCode,
                    onValueChange = {
                        stateCode = it.uppercase().take(2)
                        viewModel.updateState(stateCode)
                    },
                    label = { Text("State") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
                OutlinedTextField(
                    value = countyName,
                    onValueChange = {
                        countyName = it
                        viewModel.updateCounty(it)
                    },
                    label = { Text("County") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
            }
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

    // Direct Deposits
    item {
        SectionCard(
            title = "Direct Deposits",
            icon = Icons.Filled.AccountBalanceWallet,
            action = {
                IconButton(onClick = { viewModel.showAddDeposit() }) {
                    Icon(Icons.Filled.Add, "Add deposit")
                }
            }
        ) {
            if (calc.depositAllocations.isEmpty()) {
                Text(
                    text = "Set up how your take home pay is split across accounts",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                calc.depositAllocations.forEach { alloc ->
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
                            onClick = { viewModel.removeDeposit(alloc.deposit.id) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Outlined.Delete, "Remove", modifier = Modifier.size(18.dp))
                        }
                    }
                }
                if (calc.unallocatedAmount > 0.01) {
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
                            amount = calc.unallocatedAmount,
                            style = MaterialTheme.typography.bodyLarge,
                            color = WarningAmber
                        )
                    }
                }
            }
        }
    }

    // Save button
    item {
        Button(
            onClick = { viewModel.save() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = MaterialTheme.shapes.large,
            enabled = !state.isSaving
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(Icons.Filled.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Configuration", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun AnnualSummaryCard(proj: AnnualProjection) {
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
                text = "Annual Net Take Home",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            MoneyText(
                amount = proj.annualNetPay,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("Gross", proj.annualGrossPay.formatCurrency())
                StatItem("Total Tax", proj.annualTotalTaxes.formatCurrency())
                StatItem("Per Check", proj.perPaycheckNet.formatCurrency())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private fun androidx.compose.foundation.lazy.LazyListScope.annualContent(
    state: com.fiatlife.app.ui.viewmodel.SalaryState,
    viewModel: SalaryViewModel
) {
    val proj = state.annualProjection
    val base = state.annualBaseProjection

    if (state.config.hourlyRate <= 0) {
        item {
            SectionCard(title = "Setup Required", icon = Icons.Filled.Info) {
                Text(
                    text = "Switch to the Per Paycheck tab first to enter your hourly rate, " +
                            "deductions, and tax configuration. Annual projections are calculated " +
                            "from that data.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    // Annual summary hero card
    item { AnnualSummaryCard(proj) }

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

    // Income breakdown
    item {
        SectionCard(title = "Annual Income", icon = Icons.Filled.Payments) {
            AnnualLine("Regular Pay", proj.annualRegularPay)
            if (proj.annualOvertimePay > 0) {
                AnnualLine("Overtime Pay", proj.annualOvertimePay, color = ProfitGreen)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            AnnualLine("Gross Pay", proj.annualGrossPay, bold = true)
        }
    }

    // Tax breakdown
    item {
        SectionCard(title = "Annual Taxes", icon = Icons.Filled.AccountBalance) {
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

    // Deductions
    if (proj.preTaxDeductionBreakdown.isNotEmpty() || proj.postTaxDeductionBreakdown.isNotEmpty()) {
        item {
            SectionCard(title = "Annual Deductions", icon = Icons.Filled.RemoveCircleOutline) {
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

    // Net summary
    item {
        SectionCard(title = "Annual Summary", icon = Icons.Filled.Summarize) {
            AnnualLine("Gross Pay", proj.annualGrossPay)
            AnnualLine("Pre-Tax Deductions", -proj.annualPreTaxDeductions, color = LossRed)
            AnnualLine("Total Taxes", -proj.annualTotalTaxes, color = LossRed)
            AnnualLine("Post-Tax Deductions", -proj.annualPostTaxDeductions, color = LossRed)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            AnnualLine("Net Take Home", proj.annualNetPay, bold = true, color = ProfitGreen)
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
