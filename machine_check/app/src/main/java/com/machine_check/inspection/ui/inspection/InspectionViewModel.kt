package com.machine_check.inspection.ui.inspection

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.machine_check.inspection.data.models.InspectionResultItem
import com.machine_check.inspection.data.models.InspectionTemplate
import com.machine_check.inspection.data.models.PendingPhotoItem
import com.machine_check.inspection.data.models.SaveDailyRecordRequest
import com.machine_check.inspection.data.models.SaveRecordItem
import com.machine_check.inspection.data.repository.InspectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * 单个点检项的表单状态
 */
data class InspectionItemState(
    val template: InspectionTemplate,
    val selectedNormal: Boolean? = null,
    val numericValue: String = "",
    val remark: String = "",
    val isValid: Boolean = true,
    val remarkRequired: Boolean = false,
    val isOutOfRange: Boolean = false,
    // 照片相关
    val photoLocalPath: String? = null,          // 本地照片路径（待上传）
    val isPhotoUploading: Boolean = false,       // 正在上传中
    val uploadedPhotoIds: List<Int> = emptyList() // 已上传成功的 photoId 列表
)

/**
 * 点检页面 UI 状态
 */
sealed interface InspectionUiState {
    data object Loading : InspectionUiState
    data class Form(
        val deviceModel: String,
        val employeeId: String,
        val frequency: String = "日",
        val periodKey: String = "",
        val items: List<InspectionItemState>,
        val isSubmitting: Boolean = false,
        val submitSuccess: Boolean = false,
        val errorMessage: String? = null,
        // 两阶段提交相关
        val savedRecordIds: List<Int> = emptyList(),
        val pendingPhotoItems: List<PendingPhotoItem> = emptyList(),
        val isUploadingPhotos: Boolean = false,
        val photoUploadProgress: String? = null  // "3/5 已上传"
    ) : InspectionUiState
    data class Error(val message: String) : InspectionUiState
    data class Empty(val deviceModel: String) : InspectionUiState
}

/**
 * 点检页面 ViewModel
 */
class InspectionViewModel : ViewModel() {

    private val repository = InspectionRepository()

    private val _uiState = MutableStateFlow<InspectionUiState>(InspectionUiState.Loading)
    val uiState: StateFlow<InspectionUiState> = _uiState.asStateFlow()

    /** 加载指定设备型号的点检模板 */
    fun loadTemplates(deviceModel: String, employeeId: String, frequency: String = "日", periodKey: String = "") {
        _uiState.value = InspectionUiState.Loading
        viewModelScope.launch {
            repository.getTemplates(deviceModel, frequency).fold(
                onSuccess = { templates ->
                    if (templates.isEmpty()) {
                        _uiState.value = InspectionUiState.Empty(deviceModel)
                    } else {
                        val sortedTemplates = templates.sortedBy { it.sortOrder }
                        val items = sortedTemplates.map { template ->
                            InspectionItemState(template = template)
                        }
                        _uiState.value = InspectionUiState.Form(
                            deviceModel = deviceModel,
                            employeeId = employeeId,
                            frequency = frequency,
                            periodKey = periodKey,
                            items = items
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.value = InspectionUiState.Error(
                        error.message ?: "未知错误，请重试"
                    )
                }
            )
        }
    }

    /** 切换 normal_abnormal 选项 */
    fun onNormalAbnormalChanged(itemIndex: Int, isNormal: Boolean) {
        val state = _uiState.value as? InspectionUiState.Form ?: return
        val updatedItems = state.items.toMutableList()
        updatedItems[itemIndex] = updatedItems[itemIndex].copy(
            selectedNormal = isNormal,
            isValid = true,
            remarkRequired = !isNormal
        )
        _uiState.update { (it as InspectionUiState.Form).copy(items = updatedItems) }
    }

    /** 更新 numeric 数值 */
    fun onNumericValueChanged(itemIndex: Int, value: String) {
        val state = _uiState.value as? InspectionUiState.Form ?: return
        val numValue = value.toDoubleOrNull()
        val template = state.items[itemIndex].template
        val outOfRange = numValue != null &&
            ((template.normalMin != null && numValue < template.normalMin) ||
             (template.normalMax != null && numValue > template.normalMax))
        val updatedItems = state.items.toMutableList()
        updatedItems[itemIndex] = updatedItems[itemIndex].copy(
            numericValue = value,
            isValid = true,
            isOutOfRange = outOfRange
        )
        _uiState.update { (it as InspectionUiState.Form).copy(items = updatedItems) }
    }

    /** 更新备注 */
    fun onRemarkChanged(itemIndex: Int, remark: String) {
        val state = _uiState.value as? InspectionUiState.Form ?: return
        val updatedItems = state.items.toMutableList()
        updatedItems[itemIndex] = updatedItems[itemIndex].copy(remark = remark)
        _uiState.update { (it as InspectionUiState.Form).copy(items = updatedItems) }
    }

    /** 拍照回调 — 保存本地路径并触发上传 */
    fun onPhotoTaken(itemIndex: Int, filePath: String) {
        val state = _uiState.value as? InspectionUiState.Form ?: return
        val updatedItems = state.items.toMutableList()
        updatedItems[itemIndex] = updatedItems[itemIndex].copy(
            photoLocalPath = filePath
        )
        _uiState.update { (it as InspectionUiState.Form).copy(items = updatedItems) }
    }

    /** 清除错误提示 */
    fun clearError() {
        val state = _uiState.value as? InspectionUiState.Form ?: return
        _uiState.update { state.copy(errorMessage = null) }
    }

    /** ========== 两阶段提交 ========== */

    /**
     * 阶段 1: 保存点检数据到 records/save
     * 阶段 2: 上传缺照片项的照片
     */
    fun submitInspection() {
        val state = _uiState.value as? InspectionUiState.Form ?: return
        val deviceModel = state.deviceModel
        val employeeId = state.employeeId
        val inspectionMonth = java.time.LocalDate.now().toString()

        // ===== 验证 =====
        val validatedItems = state.items.mapIndexed { index, item ->
            val template = item.template
            val baseValid = when (template.itemType) {
                "normal_abnormal" -> {
                    val selected = item.selectedNormal != null
                    if (selected && item.selectedNormal == false && item.remark.isBlank()) false
                    else selected
                }
                "numeric" -> item.numericValue.toDoubleOrNull() != null
                else -> true
            }
            // requirePhoto + 异常 → 必须拍照
            val photoRequired = template.requirePhoto && item.selectedNormal == false &&
                item.photoLocalPath == null
            item.copy(
                isValid = baseValid && !photoRequired,
                remarkRequired = item.selectedNormal == false
            )
        }

        val hasErrors = validatedItems.any { !it.isValid }
        if (hasErrors) {
            val photoMissing = validatedItems.any {
                it.template.requirePhoto && it.selectedNormal == false && it.photoLocalPath == null
            }
            _uiState.update {
                (it as InspectionUiState.Form).copy(
                    items = validatedItems,
                    errorMessage = if (photoMissing)
                        "异常项且需要拍照的检查项必须先拍照再提交"
                    else
                        "异常项必须填写备注，或数值格式不正确"
                )
            }
            return
        }

        // ===== 构建请求 =====
        val today = java.time.LocalDate.now().dayOfMonth
        val results = validatedItems.map { item ->
            val template = item.template
            val (resultValue, isNormal) = when (template.itemType) {
                "normal_abnormal" -> {
                    val normal = item.selectedNormal ?: true
                    (if (normal) "正常" else "异常") to normal
                }
                "numeric" -> {
                    val numOk = item.numericValue.toDoubleOrNull() != null
                    if (numOk) {
                        val numVal = item.numericValue.toDouble()
                        val inRange = when {
                            template.normalMin != null && template.normalMax != null ->
                                numVal >= template.normalMin && numVal <= template.normalMax
                            else -> true
                        }
                        item.numericValue to inRange
                    } else {
                        item.numericValue to true
                    }
                }
                else -> "" to true
            }
            SaveRecordItem(
                day = today,
                itemName = template.itemName,
                resultValue = resultValue,
                remark = item.remark
            )
        }

        val request = SaveDailyRecordRequest(
            employeeId = employeeId,
            deviceModel = deviceModel,
            inspectionMonth = inspectionMonth,
            results = results
        )

        // ===== 发送保存请求 =====
        _uiState.update { (it as InspectionUiState.Form).copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            repository.saveDailyRecord(request).fold(
                onSuccess = { response ->
                    if (response.pendingPhotoItems.isEmpty()) {
                        // 无缺照片项 → 直接完成
                        _uiState.update {
                            (it as InspectionUiState.Form).copy(
                                isSubmitting = false,
                                submitSuccess = true
                            )
                        }
                    } else {
                        // 有缺照片项 → 进入阶段 2：上传照片
                        _uiState.update {
                            (it as InspectionUiState.Form).copy(
                                isSubmitting = false,
                                savedRecordIds = response.recordIds,
                                pendingPhotoItems = response.pendingPhotoItems,
                                photoUploadProgress = "0/${response.pendingPhotoItems.size} 项待拍照"
                            )
                        }
                        // 自动开始上传已有本地照片
                        uploadPendingPhotos()
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        (it as InspectionUiState.Form).copy(
                            isSubmitting = false,
                            errorMessage = error.message ?: "提交失败，请重试"
                        )
                    }
                }
            )
        }
    }

    /** 上传所有待处理的照片 */
    private fun uploadPendingPhotos() {
        val state = _uiState.value as? InspectionUiState.Form ?: return
        val employeeId = state.employeeId

        // 收集所有需要上传的照片：pendingPhotoItems 中的 missingItems
        val pendingMap = mutableMapOf<String, Int>() // "itemName" → recordId
        for (ppi in state.pendingPhotoItems) {
            for (itemName in ppi.missingItems) {
                pendingMap[itemName] = ppi.recordId
            }
        }

        if (pendingMap.isEmpty()) {
            _uiState.update { state.copy(isUploadingPhotos = false, submitSuccess = true) }
            return
        }

        _uiState.update { state.copy(isUploadingPhotos = true) }
        var uploaded = 0
        val total = pendingMap.size

        viewModelScope.launch {
            for ((itemName, recordId) in pendingMap) {
                // 找到该检查项对应的本地照片
                val itemIndex = state.items.indexOfFirst { it.template.itemName == itemName }
                if (itemIndex < 0) continue

                val item = state.items[itemIndex]
                val localPath = item.photoLocalPath

                if (localPath == null || !File(localPath).exists()) {
                    // 无本地照片 → 标记为待拍照，不上传
                    continue
                }

                // 更新上传状态
                val updatedItems = state.items.toMutableList()
                updatedItems[itemIndex] = updatedItems[itemIndex].copy(isPhotoUploading = true)
                _uiState.update {
                    (it as InspectionUiState.Form).copy(
                        items = updatedItems,
                        photoUploadProgress = "$uploaded/$total 正在上传..."
                    )
                }

                // 压缩后上传
                val compressedPath = compressImage(localPath)
                val uploadPath = compressedPath ?: localPath

                val result = repository.uploadPhoto(
                    filePath = uploadPath,
                    recordId = recordId,
                    itemName = itemName,
                    uploadedBy = employeeId
                )

                result.fold(
                    onSuccess = { response ->
                        uploaded++
                        val items2 = ( _uiState.value as? InspectionUiState.Form)?.items?.toMutableList() ?: return@fold
                        val idx = items2.indexOfFirst { it.template.itemName == itemName }
                        if (idx >= 0) {
                            items2[idx] = items2[idx].copy(
                                isPhotoUploading = false,
                                uploadedPhotoIds = items2[idx].uploadedPhotoIds + response.photoId
                            )
                        }
                        _uiState.update {
                            (it as InspectionUiState.Form).copy(
                                items = items2,
                                photoUploadProgress = "$uploaded/$total 已上传"
                            )
                        }
                    },
                    onFailure = { error ->
                        val items2 = ( _uiState.value as? InspectionUiState.Form)?.items?.toMutableList() ?: return@fold
                        val idx = items2.indexOfFirst { it.template.itemName == itemName }
                        if (idx >= 0) {
                            items2[idx] = items2[idx].copy(isPhotoUploading = false)
                        }
                        _uiState.update {
                            (it as InspectionUiState.Form).copy(items = items2)
                        }
                    }
                )

                // 清理压缩文件
                if (compressedPath != null && compressedPath != localPath) {
                    File(compressedPath).delete()
                }
            }

            // 所有上传完成
            _uiState.update {
                val s = it as InspectionUiState.Form
                s.copy(
                    isUploadingPhotos = false,
                    submitSuccess = true,
                    photoUploadProgress = "$uploaded/$total 上传完成"
                )
            }
        }
    }

    /** 跳过拍照，直接完成提交 */
    fun skipPhotoUpload() {
        _uiState.update {
            val s = it as? InspectionUiState.Form ?: return
            s.copy(isUploadingPhotos = false, submitSuccess = true)
        }
    }

    /** 压缩图片：最大宽度 1920px，JPEG quality 80 */
    private fun compressImage(filePath: String): String? {
        return try {
            val original = BitmapFactory.decodeFile(filePath) ?: return null
            val maxWidth = 1920
            val width: Int
            val height: Int
            if (original.width > maxWidth) {
                val ratio = maxWidth.toFloat() / original.width.toFloat()
                width = maxWidth
                height = (original.height * ratio).toInt()
            } else {
                width = original.width
                height = original.height
            }
            val resized = Bitmap.createScaledBitmap(original, width, height, true)
            if (resized != original) original.recycle()

            val compressedFile = File(filePath + "_compressed.jpg")
            FileOutputStream(compressedFile).use { out ->
                resized.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            resized.recycle()

            if (compressedFile.length() > 0) compressedFile.absolutePath else null
        } catch (e: Exception) {
            null // 压缩失败时返回 null，调用方使用原图
        }
    }

    /** 重试加载 */
    fun retry(deviceModel: String, employeeId: String, frequency: String = "日", periodKey: String = "") {
        loadTemplates(deviceModel, employeeId, frequency, periodKey)
    }
}
