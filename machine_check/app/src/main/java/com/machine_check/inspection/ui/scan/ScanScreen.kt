package com.machine_check.inspection.ui.scan

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.machine_check.inspection.ui.components.QrCodeScanner

/**
 * 扫码页面
 * 步骤1: 输入/扫描工号 → 步骤2: 输入/扫描设备型号 → 步骤3: 选择频率 → 进入点检
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onNavigateToInspection: (deviceModel: String, employeeId: String, frequency: String, periodKey: String) -> Unit,
    viewModel: ScanViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // 导航触发
    LaunchedEffect(uiState.navigateToInspection) {
        uiState.navigateToInspection?.let { navData ->
            val parts = navData.split("/", limit = 3)
            val deviceModel = parts[0]
            val frequency = parts.getOrElse(1) { "日" }
            val periodKey = parts.getOrElse(2) { "" }
            onNavigateToInspection(deviceModel, uiState.employeeId, frequency, periodKey)
            viewModel.onNavigationComplete()
        }
    }

    // 从点检页返回时重新检查频率可用性 + 刷新未点检列表
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAfterInspection()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设备点检") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        if (uiState.isScanning) {
            // ========== 扫码全屏模式 ==========
            Box(modifier = Modifier.fillMaxSize()) {
                QrCodeScanner(
                    onBarcodeScanned = { barcode -> viewModel.onBarcodeScanned(barcode) },
                    isActive = uiState.isScanning,
                    modifier = Modifier.fillMaxSize()
                )

                // 扫描目标提示
                Text(
                    text = if (uiState.currentScanTarget == ScanTarget.EMPLOYEE_ID)
                        "请扫描工号二维码" else "请扫描设备型号二维码",
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // 取消按钮
                Button(
                    onClick = { viewModel.stopScanning() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                ) {
                    Text("取消扫码")
                }
            }
        } else {
            // ========== 输入模式 ==========
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // ---- 工号区域 ----
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (uiState.employeeValidated)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "步骤 1: 员工工号",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = uiState.employeeId,
                            onValueChange = { viewModel.onEmployeeIdChange(it) },
                            label = { Text("工号") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = uiState.validationError != null,
                            supportingText = if (uiState.validationError != null) {
                                { Text(uiState.validationError!!, color = MaterialTheme.colorScheme.error) }
                            } else if (uiState.employeeValidated) {
                                { Text("✓ 工号验证通过", color = MaterialTheme.colorScheme.primary) }
                            } else null,
                            trailingIcon = {
                                Row {
                                    // 手动验证按钮
                                    if (!uiState.employeeValidated && uiState.employeeId.isNotBlank() && !uiState.isValidatingEmployee) {
                                        TextButton(onClick = {
                                            viewModel.validateEmployeeId(uiState.employeeId)
                                        }) {
                                            Text("验证")
                                        }
                                    }
                                    IconButton(onClick = {
                                        viewModel.startScanning(ScanTarget.EMPLOYEE_ID)
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.QrCodeScanner,
                                            contentDescription = "扫描工号二维码"
                                        )
                                    }
                                }
                            }
                        )

                        // 验证中进度条
                        if (uiState.isValidatingEmployee) {
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }

                // ---- 设备型号区域 ----
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "步骤 2: 设备型号",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = uiState.deviceModel,
                            onValueChange = { viewModel.onDeviceModelChange(it) },
                            label = { Text("设备型号") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = uiState.employeeValidated,
                            trailingIcon = {
                                IconButton(onClick = {
                                    viewModel.startScanning(ScanTarget.DEVICE_MODEL)
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = "扫描设备型号二维码"
                                    )
                                }
                            }
                        )
                    }
                }

                // ---- 扫码结果提示 ----
                if (uiState.scanResult != null && !uiState.isScanning) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            text = "扫描结果: ${uiState.scanResult}",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // ---- 步骤 3: 点检频率 ----
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "步骤 3: 点检频率",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            // 当月未点检 checkbox — 工号验证通过后才可用
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 0.dp)
                            ) {
                                Checkbox(
                                    checked = uiState.showUninspectedMonthly,
                                    onCheckedChange = { viewModel.toggleUninspectedMonthly() },
                                    enabled = uiState.employeeValidated
                                )
                                Text(
                                    text = "当月未检",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (uiState.uninspectedMonthlyCount > 0)
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (uiState.uninspectedMonthlyCount > 0) {
                                    Text(
                                        text = "(${uiState.uninspectedMonthlyCount})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        val freqInfo = uiState.frequenciesAvailable

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 日检
                            val dailyAvailable = freqInfo?.daily?.available != false
                            FilterChip(
                                selected = uiState.selectedFrequency == "日",
                                onClick = { viewModel.onFrequencySelected("日") },
                                label = { Text(if (dailyAvailable) "日检" else "日检 ✓") },
                                enabled = dailyAvailable,
                                modifier = Modifier.weight(1f)
                            )

                            // 周检
                            val weeklyAvailable = freqInfo?.weekly?.available != false
                            FilterChip(
                                selected = uiState.selectedFrequency == "周",
                                onClick = { viewModel.onFrequencySelected("周") },
                                label = { Text(if (weeklyAvailable) "周检" else "周检 ✓") },
                                enabled = weeklyAvailable,
                                modifier = Modifier.weight(1f)
                            )

                            // 月检
                            val monthlyAvailable = freqInfo?.monthly?.available != false
                            FilterChip(
                                selected = uiState.selectedFrequency == "月",
                                onClick = { viewModel.onFrequencySelected("月") },
                                label = { Text(if (monthlyAvailable) "月检" else "月检 ✓") },
                                enabled = monthlyAvailable,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // 展开的当月未点检设备 location 列表
                        if (uiState.showUninspectedMonthly) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))

                            if (uiState.isLoadingUninspectedMonthly) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            if (uiState.uninspectedMonthlyList.isEmpty() && !uiState.isLoadingUninspectedMonthly) {
                                Text(
                                    text = "✅ 当月所有设备均已点检",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                uiState.uninspectedMonthlyList.forEach { device ->
                                    val location = device.deviceLocation.ifEmpty { "未配置位置" }
                                    Text(
                                        text = "📍 $location — ${device.deviceModel}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 检查中进度条
                if (uiState.isCheckingFrequencies) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                // ---- 未点检 / 异常点检设备列表 ----
                if (uiState.employeeValidated) {
                    val hasIssues = uiState.uninspectedList.isNotEmpty() || uiState.abnormalList.isNotEmpty()
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (hasIssues)
                                MaterialTheme.colorScheme.errorContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "⚠ 设备点检状态",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                if (uiState.isLoadingUninspected) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // ---- 未点检（必须点检）----
                            Text(
                                text = "未点检（必须点检）：",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                            if (uiState.uninspectedList.isEmpty()) {
                                Text(
                                    text = "  ✓ 全部完成",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.6f)
                                )
                            } else {
                                uiState.uninspectedList.forEach { item ->
                                    Text(
                                        text = "  [${item.frequency}] ${item.location}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // ---- 异常点检（必须+选择）----
                            Text(
                                text = "异常点检（全部设备）：",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                            if (uiState.abnormalList.isEmpty()) {
                                Text(
                                    text = "  ✓ 无异常",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.6f)
                                )
                            } else {
                                uiState.abnormalList.forEach { item ->
                                    Text(
                                        text = "  [${item.frequency}] ${item.location}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    )
                                }
                            }

                            // 全部正常时显示
                            if (!hasIssues && !uiState.isLoadingUninspected) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "✓ 当前所有设备点检正常",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ---- 进入点检按钮 ----
                val freqInfo = uiState.frequenciesAvailable
                val selectedAvailable = when (uiState.selectedFrequency) {
                    "日" -> freqInfo?.daily?.available != false
                    "周" -> freqInfo?.weekly?.available != false
                    "月" -> freqInfo?.monthly?.available != false
                    else -> false
                }
                Button(
                    onClick = { viewModel.navigateToInspection() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = uiState.employeeValidated
                        && uiState.deviceModel.isNotBlank()
                        && !uiState.isCheckingFrequencies
                        && selectedAvailable
                ) {
                    Text(
                        text = "进入点检",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
