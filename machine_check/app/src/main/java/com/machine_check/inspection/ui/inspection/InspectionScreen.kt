package com.machine_check.inspection.ui.inspection

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.io.File

/**
 * 点检页面
 * 根据设备型号加载模板，动态生成表单（支持拍照）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionScreen(
    deviceModel: String,
    employeeId: String,
    frequency: String = "日",
    periodKey: String = "",
    onBack: () -> Unit,
    viewModel: InspectionViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 加载模板
    LaunchedEffect(deviceModel, frequency) {
        viewModel.loadTemplates(deviceModel, employeeId, frequency, periodKey)
    }

    // 错误 Snackbar
    LaunchedEffect((uiState as? InspectionUiState.Form)?.errorMessage) {
        val msg = (uiState as? InspectionUiState.Form)?.errorMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    // 提交成功 Snackbar + 返回
    LaunchedEffect((uiState as? InspectionUiState.Form)?.submitSuccess) {
        if ((uiState as? InspectionUiState.Form)?.submitSuccess == true) {
            snackbarHostState.showSnackbar("提交成功！")
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("点检 - $deviceModel [${frequency}检]") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when (val state = uiState) {
            is InspectionUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("正在加载点检模板...")
                    }
                }
            }

            is InspectionUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            viewModel.retry(deviceModel, employeeId, frequency, periodKey)
                        }) { Text("重试") }
                    }
                }
            }

            is InspectionUiState.Empty -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) { Text("该设备型号暂无点检模板") }
            }

            is InspectionUiState.Form -> {
                InspectionForm(
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

/**
 * 点检表单
 */
@Composable
private fun InspectionForm(
    state: InspectionUiState.Form,
    viewModel: InspectionViewModel,
    modifier: Modifier = Modifier
) {
    // 相机 URI 状态（每个 requirePhoto 项独立管理）
    var pendingPhotoIndex by remember { mutableStateOf(-1) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && pendingPhotoIndex >= 0) {
            cameraUri?.let { uri ->
                viewModel.onPhotoTaken(pendingPhotoIndex, uri.path ?: "")
            }
        }
        pendingPhotoIndex = -1
    }

    val context = LocalContext.current

    Column(modifier = modifier.fillMaxSize()) {
        // ===== Pending Photo 提示条 =====
        if (state.pendingPhotoItems.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFFFF3E0),
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = Color(0xFFE65100),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "需要为以下项目拍照",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE65100)
                        )
                        state.pendingPhotoItems.forEach { ppi ->
                            ppi.missingItems.forEach { item ->
                                Text(
                                    "• $item",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFBF360C)
                                )
                            }
                        }
                    }
                    TextButton(onClick = { viewModel.skipPhotoUpload() }) {
                        Text("跳过", color = Color(0xFFE65100))
                    }
                }
            }
        }

        // ===== 上传进度 =====
        if (state.isUploadingPhotos) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary
            )
            state.photoUploadProgress?.let { progress ->
                Text(
                    text = progress,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // 表单列表
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(
                items = state.items,
                key = { _, item -> item.template.id }
            ) { index, itemState ->
                InspectionItemCard(
                    itemState = itemState,
                    index = index,
                    onNormalAbnormalChanged = { isNormal ->
                        viewModel.onNormalAbnormalChanged(index, isNormal)
                    },
                    onNumericValueChanged = { value ->
                        viewModel.onNumericValueChanged(index, value)
                    },
                    onRemarkChanged = { remark ->
                        viewModel.onRemarkChanged(index, remark)
                    },
                    onTakePhoto = {
                        val photoDir = File(context.cacheDir, "inspection_photos")
                        photoDir.mkdirs()
                        val photoFile = File(photoDir, "photo_${System.currentTimeMillis()}.jpg")
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            photoFile
                        )
                        cameraUri = uri
                        pendingPhotoIndex = index
                        cameraLauncher.launch(uri)
                    }
                )
            }
        }

        // 底部提交按钮
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Button(
                onClick = { viewModel.submitInspection() },
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp),
                enabled = !state.isSubmitting && !state.isUploadingPhotos
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("提交中...")
                } else if (state.isUploadingPhotos) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("正在上传照片...")
                } else {
                    Text("提交点检", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/**
 * 单条点检项卡片
 * 支持 normal_abnormal / numeric 输入 + 拍照功能
 */
@Composable
private fun InspectionItemCard(
    itemState: InspectionItemState,
    index: Int,
    onNormalAbnormalChanged: (Boolean) -> Unit,
    onNumericValueChanged: (String) -> Unit,
    onRemarkChanged: (String) -> Unit,
    onTakePhoto: () -> Unit
) {
    val template = itemState.template
    val isValid = itemState.isValid

    Card(
        modifier = Modifier.fillMaxWidth().border(
            width = if (!isValid) 2.dp else 0.dp,
            color = if (!isValid) MaterialTheme.colorScheme.error else Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${index + 1}. ${template.itemName}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    // requirePhoto 标记
                    if (template.requirePhoto) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFE3F2FD)
                        ) {
                            Text(
                                text = "📷 需拍照",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF1565C0)
                            )
                        }
                    }
                }
                if (!isValid) {
                    Text(
                        text = "必填",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // 根据类型渲染输入控件
            when (template.itemType) {
                "normal_abnormal" -> {
                    NormalAbnormalInput(
                        selectedNormal = itemState.selectedNormal,
                        onSelectionChanged = onNormalAbnormalChanged
                    )
                }
                "numeric" -> {
                    NumericInput(
                        value = itemState.numericValue,
                        onValueChanged = onNumericValueChanged,
                        unit = template.unit,
                        normalMin = template.normalMin,
                        normalMax = template.normalMax,
                        isOutOfRange = itemState.isOutOfRange
                    )
                }
            }

            // 备注输入
            OutlinedTextField(
                value = itemState.remark,
                onValueChange = onRemarkChanged,
                label = {
                    if (itemState.remarkRequired) {
                        Text("备注（必填）", color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("备注（可选）")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = itemState.remarkRequired && itemState.remark.isBlank()
            )

            // ===== 照片区域 =====
            if (template.requirePhoto) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (itemState.selectedNormal == false)
                            "📷 拍照（异常项必须拍照）"
                        else
                            "📷 拍照（可选）",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (itemState.selectedNormal == false)
                            Color(0xFFC62828) else Color(0xFF1565C0)
                    )

                    if (itemState.photoLocalPath != null) {
                        // 已拍照 → 显示缩略图 + 重拍按钮
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("已拍照", style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF4CAF50))
                        }
                    }
                }

                // 照片预览
                if (itemState.photoLocalPath != null) {
                    AsyncImage(
                        model = File(itemState.photoLocalPath),
                        contentDescription = "照片预览",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Fit
                    )
                }

                // 拍照按钮
                OutlinedButton(
                    onClick = onTakePhoto,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !itemState.isPhotoUploading
                ) {
                    if (itemState.isPhotoUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp), strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("正在上传...")
                    } else {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (itemState.photoLocalPath != null) "重新拍照" else "拍照")
                    }
                }
            }
        }
    }
}

/**
 * normal_abnormal 类型输入：两个切换按钮
 */
@Composable
private fun NormalAbnormalInput(
    selectedNormal: Boolean?,
    onSelectionChanged: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = { onSelectionChanged(true) },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selectedNormal == true)
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "✓ 正常",
                color = if (selectedNormal == true)
                    MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface
            )
        }
        Button(
            onClick = { onSelectionChanged(false) },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selectedNormal == false)
                    MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.surfaceVariant
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "✗ 异常",
                color = if (selectedNormal == false)
                    MaterialTheme.colorScheme.onError
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * numeric 类型输入：数值输入框 + 实时范围检查
 */
@Composable
private fun NumericInput(
    value: String,
    onValueChanged: (String) -> Unit,
    unit: String?,
    normalMin: Double?,
    normalMax: Double?,
    isOutOfRange: Boolean = false
) {
    val numericValue = value.toDoubleOrNull()
    val isInRange = when {
        value.isBlank() -> null
        numericValue == null -> false
        isOutOfRange -> false
        normalMin != null && normalMax != null ->
            numericValue >= normalMin && numericValue <= normalMax
        else -> true
    }

    var flashCount by remember { mutableStateOf(0) }
    var isFlashing by remember { mutableStateOf(false) }

    LaunchedEffect(isOutOfRange) {
        if (isOutOfRange) {
            flashCount = 0; isFlashing = true
            repeat(6) { flashCount++; delay(150) }
            isFlashing = false
        }
    }

    val flashBorderColor = if (isFlashing && flashCount % 2 == 0) {
        Color.Transparent
    } else if (isOutOfRange) {
        Color(0xFFF44336)
    } else {
        when (isInRange) {
            null -> MaterialTheme.colorScheme.outline
            true -> Color(0xFF4CAF50)
            false -> Color(0xFFF44336)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChanged,
            label = {
                val rangeText = buildString {
                    if (normalMin != null && normalMax != null)
                        append("范围: $normalMin ~ $normalMax")
                    if (unit != null) {
                        if (isNotEmpty()) append(" "); append(unit)
                    }
                }
                Text(rangeText.ifEmpty { "请输入数值" })
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = isOutOfRange,
            supportingText = if (isOutOfRange) {
                { Text("数值超出正常范围，将作为异常记录", color = Color(0xFFF44336)) }
            } else if (isInRange == false) {
                { Text("数值超出正常范围", color = Color(0xFFF44336)) }
            } else null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = flashBorderColor,
                unfocusedBorderColor = flashBorderColor
            )
        )
    }
}
