# 异常备注 Tooltip + 超范围处理 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Web 端点击异常格显示备注 tooltip + 超范围数值红字；Android 端异常项备注必填 + 超范围数值可提交并闪烁警告。

**Architecture:** Web 前端纯 JS/CSS 实现 tooltip 和红字显示；Android 端修改 ViewModel 校验逻辑（异常→备注必填，超范围→不阻止但 isNormal=false），UI 层添加闪烁动画和动态标签。

**Tech Stack:** Vanilla JS/CSS（Web）、Kotlin + Jetpack Compose（Android）、后端无需修改

---

## 文件结构

| 操作 | 文件 | 职责 |
|------|------|------|
| 修改 | `html/index.html` | CSS tooltip + 红字样式、表格渲染 data 属性、tooltip JS |
| 修改 | `machine_check/.../ui/inspection/InspectionViewModel.kt` | State 字段 + 校验逻辑变更 |
| 修改 | `machine_check/.../ui/inspection/InspectionScreen.kt` | 动态备注标签 + 超范围闪烁动画 |

---

### Task 1: Web 前端 — 异常 Tooltip + 超范围红字

**Files:**
- Modify: `html/index.html`

- [ ] **Step 1: 添加 CSS 样式**

在 `</style>` 标签之前添加：

```css
/* ============================================================
   Out-of-range numeric result
   ============================================================ */
.result-outofrange {
    color: #DC2626 !important;
    font-weight: 700;
    cursor: default;
}

/* ============================================================
   Remark Tooltip
   ============================================================ */
.result-abnormal {
    cursor: pointer;
}

.remark-tooltip {
    position: absolute;
    z-index: 999;
    max-width: 220px;
    padding: 8px 12px;
    background: rgba(30, 41, 59, 0.92);
    color: #FFFFFF;
    font-size: 12px;
    font-family: var(--font-body);
    border-radius: var(--radius-xs);
    box-shadow: 0 4px 12px rgba(0,0,0,0.25);
    line-height: 1.5;
    pointer-events: none;
    animation: tooltipFadeIn 0.15s ease;
}

.remark-tooltip::after {
    content: '';
    position: absolute;
    bottom: -6px;
    left: 50%;
    transform: translateX(-50%);
    width: 0;
    height: 0;
    border-left: 6px solid transparent;
    border-right: 6px solid transparent;
    border-top: 6px solid rgba(30, 41, 59, 0.92);
}

.remark-tooltip.above::after {
    bottom: auto;
    top: -6px;
    border-top: none;
    border-bottom: 6px solid rgba(30, 41, 59, 0.92);
}

@keyframes tooltipFadeIn {
    from { opacity: 0; transform: translateY(4px); }
    to { opacity: 1; transform: translateY(0); }
}
```

- [ ] **Step 2: 修改 `renderReadOnlyTable()` — 存储 remark + 检测超范围**

找到表格渲染中生成单元格的代码（约第 2125-2136 行），当前为：

```javascript
                for (let day = 1; day <= daysInMonth; day++) {
                    const key = `${template.itemName}_${day}`;
                    const record = recordMap.get(key);
                    let value = record ? record.resultValue : '';
                    let cellClass = '';

                    if (value === '○') cellClass = 'result-normal';
                    if (value === '×') cellClass = 'result-abnormal';

                    // 关键修复：空值或未定义显示 "/"
                    let displayValue = (value && value !== '') ? value : '/';
                    html += `<td class="${cellClass}">${displayValue}</td>`;
                }
```

替换为：

```javascript
                for (let day = 1; day <= daysInMonth; day++) {
                    const key = `${template.itemName}_${day}`;
                    const record = recordMap.get(key);
                    let value = record ? record.resultValue : '';
                    let cellClass = '';
                    let remarkAttr = '';
                    let isNormalAttr = '';

                    if (record) {
                        isNormalAttr = ` data-isnormal="${record.isNormal}"`;
                        if (record.remark) {
                            remarkAttr = ` data-remark="${escapeHtml(record.remark)}"`;
                        } else {
                            remarkAttr = ` data-remark=""`;
                        }
                    }

                    if (value === '○') cellClass = 'result-normal';
                    if (value === '×') cellClass = 'result-abnormal';

                    // 超范围数值：isNormal=false 且值不是 ○/×/ 
                    if (record && !record.isNormal && value !== '○' && value !== '×' && value !== '/' && value !== '') {
                        cellClass += ' result-outofrange';
                    }

                    // 空值或未定义显示 "/"
                    let displayValue = (value && value !== '') ? value : '/';
                    html += `<td class="${cellClass}"${isNormalAttr}${remarkAttr}>${displayValue}</td>`;
                }
```

同时在 script 最前面添加 HTML 转义辅助函数（在 API 配置之后、全局变量之前）：

```javascript
        // HTML 转义
        function escapeHtml(text) {
            const div = document.createElement('div');
            div.textContent = text;
            return div.innerHTML;
        }
```

- [ ] **Step 3: 添加 tooltip JS 逻辑**

在 `renderReadOnlyTable` 函数之后添加 tooltip 事件处理：

```javascript
        // ==================== 异常备注 Tooltip ====================
        let currentTooltip = null;

        function showRemarkTooltip(cell) {
            hideRemarkTooltip();

            const remark = cell.getAttribute('data-remark');
            const remarkText = remark && remark !== '' ? remark : '无备注';

            const tooltip = document.createElement('div');
            tooltip.className = 'remark-tooltip';
            tooltip.textContent = '备注：' + remarkText;
            document.body.appendChild(tooltip);

            // 定位：默认在单元格上方
            const cellRect = cell.getBoundingClientRect();
            const tooltipRect = tooltip.getBoundingClientRect();

            let top = cellRect.top - tooltipRect.height - 8;
            let left = cellRect.left + (cellRect.width - tooltipRect.width) / 2;

            // 如果上方空间不足，显示在下方
            if (top < 8) {
                top = cellRect.bottom + 8;
                tooltip.classList.add('above');
            }

            // 防止水平溢出
            if (left < 8) left = 8;
            if (left + tooltipRect.width > window.innerWidth - 8) {
                left = window.innerWidth - tooltipRect.width - 8;
            }

            tooltip.style.top = top + 'px';
            tooltip.style.left = left + 'px';

            currentTooltip = tooltip;
        }

        function hideRemarkTooltip() {
            if (currentTooltip) {
                currentTooltip.remove();
                currentTooltip = null;
            }
        }

        // 事件委托：点击表格中的异常格
        document.getElementById('tableContainer').addEventListener('click', function(e) {
            const cell = e.target.closest('td.result-abnormal');
            if (!cell) {
                hideRemarkTooltip();
                return;
            }
            if (!cell.hasAttribute('data-remark')) return;

            // 如果点击的是已经显示 tooltip 的格，关闭
            if (currentTooltip && currentTooltip._sourceCell === cell) {
                hideRemarkTooltip();
                return;
            }

            showRemarkTooltip(cell);
            if (currentTooltip) currentTooltip._sourceCell = cell;
        });

        // 点击页面其他地方关闭 tooltip
        document.addEventListener('click', function(e) {
            if (!e.target.closest('td.result-abnormal')) {
                hideRemarkTooltip();
            }
        });
```

- [ ] **Step 4: 编译验证**

```bash
cd C:\Users\33215\source\repos\webapi && dotnet build
```

- [ ] **Step 5: 提交**

```bash
git add html/index.html
git commit -m "feat: add remark tooltip on abnormal cell click + out-of-range red text

Clicking × cells shows remark in a tooltip bubble. Numeric values
with isNormal=false are displayed in red bold font.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: Android ViewModel — 状态字段 + 校验逻辑变更

**Files:**
- Modify: `machine_check/app/src/main/java/com/machine_check/inspection/ui/inspection/InspectionViewModel.kt`

- [ ] **Step 1: 扩展 `InspectionItemState`**

在 `isValid` 字段后添加新字段：

```kotlin
data class InspectionItemState(
    val template: InspectionTemplate,
    val selectedNormal: Boolean? = null,
    val numericValue: String = "",
    val remark: String = "",
    val isValid: Boolean = true,
    val remarkRequired: Boolean = false,   // 异常时备注变为必填
    val isOutOfRange: Boolean = false       // numeric 超范围标记
)
```

- [ ] **Step 2: 修改 `onNormalAbnormalChanged()` — 异常时标记备注必填**

将当前方法：

```kotlin
    fun onNormalAbnormalChanged(itemIndex: Int, isNormal: Boolean) {
        val state = _uiState.value as? InspectionUiState.Form ?: return
        val updatedItems = state.items.toMutableList()
        updatedItems[itemIndex] = updatedItems[itemIndex].copy(
            selectedNormal = isNormal,
            isValid = true
        )
        _uiState.update { (it as InspectionUiState.Form).copy(items = updatedItems) }
    }
```

改为：

```kotlin
    fun onNormalAbnormalChanged(itemIndex: Int, isNormal: Boolean) {
        val state = _uiState.value as? InspectionUiState.Form ?: return
        val updatedItems = state.items.toMutableList()
        // 选择「异常」时备注变为必填，选择「正常」时恢复可选
        updatedItems[itemIndex] = updatedItems[itemIndex].copy(
            selectedNormal = isNormal,
            isValid = true,
            remarkRequired = !isNormal
        )
        _uiState.update { (it as InspectionUiState.Form).copy(items = updatedItems) }
    }
```

- [ ] **Step 3: 修改 `onNumericValueChanged()` — 检测超范围**

将当前方法：

```kotlin
    fun onNumericValueChanged(itemIndex: Int, value: String) {
        val state = _uiState.value as? InspectionUiState.Form ?: return
        val updatedItems = state.items.toMutableList()
        updatedItems[itemIndex] = updatedItems[itemIndex].copy(
            numericValue = value,
            isValid = true
        )
        _uiState.update { (it as InspectionUiState.Form).copy(items = updatedItems) }
    }
```

改为：

```kotlin
    fun onNumericValueChanged(itemIndex: Int, value: String) {
        val state = _uiState.value as? InspectionUiState.Form ?: return
        val numValue = value.toDoubleOrNull()
        val template = state.items[itemIndex].template
        // 检测是否超范围
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
```

- [ ] **Step 4: 重写 `submitInspection()` 校验逻辑**

将当前的验证和构建请求代码块（约第 136-181 行）替换为：

```kotlin
        // ===== 验证 =====
        val validatedItems = state.items.mapIndexed { index, item ->
            val template = item.template
            val isValid = when (template.itemType) {
                "normal_abnormal" -> {
                    // 必须选择正常或异常
                    val selected = item.selectedNormal != null
                    // 如果选择了异常，备注必填
                    if (selected && item.selectedNormal == false && item.remark.isBlank()) {
                        false
                    } else {
                        selected
                    }
                }
                "numeric" -> {
                    // numeric: 必须填写有效数字（不论是否超范围）
                    item.numericValue.toDoubleOrNull() != null
                }
                else -> true
            }
            item.copy(
                isValid = isValid,
                remarkRequired = item.selectedNormal == false
            )
        }

        val hasErrors = validatedItems.any { !it.isValid }
        if (hasErrors) {
            _uiState.update {
                (it as InspectionUiState.Form).copy(
                    items = validatedItems,
                    errorMessage = "异常项必须填写备注，或数值格式不正确"
                )
            }
            return
        }

        // ===== 构建请求 =====
        val results = validatedItems.map { item ->
            val template = item.template
            val (resultValue, isNormal) = when (template.itemType) {
                "normal_abnormal" -> {
                    val normal = item.selectedNormal ?: true
                    (if (normal) "正常" else "异常") to normal
                }
                "numeric" -> {
                    // 超范围数值：resultValue = 数值，isNormal = false
                    val numOk = item.numericValue.toDoubleOrNull() != null
                    if (numOk) {
                        val inRange = when {
                            template.normalMin != null && template.normalMax != null ->
                                item.numericValue.toDouble() in template.normalMin..template.normalMax
                            else -> true
                        }
                        item.numericValue to inRange
                    } else {
                        item.numericValue to true
                    }
                }
                else -> "" to true
            }
            InspectionResultItem(
                itemName = template.itemName,
                resultValue = resultValue,
                isNormal = isNormal,
                remark = item.remark
            )
        }
```

- [ ] **Step 5: 编译验证**

```bash
cd C:\Users\33215\source\repos\webapi\machine_check && ./gradlew :app:assembleDebug
```

预期: BUILD SUCCESSFUL

- [ ] **Step 6: 提交**

```bash
cd C:\Users\33215\source\repos\webapi\machine_check
git add app/src/main/java/com/machine_check/inspection/ui/inspection/InspectionViewModel.kt
git commit -m "feat(android): abnormal requires remark + out-of-range allowed with isNormal=false

- remarkRequired flag set when 'abnormal' selected
- Submit validation: abnormal items must have remark
- Numeric out-of-range values can submit with isNormal=false
- isOutOfRange flag for UI animation

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: Android UI — 动态备注标签 + 超范围闪烁

**Files:**
- Modify: `machine_check/app/src/main/java/com/machine_check/inspection/ui/inspection/InspectionScreen.kt`

- [ ] **Step 1: 修改 `InspectionItemCard` — 动态备注标签**

找到备注输入框代码（约第 282-288 行）：

```kotlin
            // 备注输入
            OutlinedTextField(
                value = itemState.remark,
                onValueChange = onRemarkChanged,
                label = { Text("备注（可选）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
```

改为：

```kotlin
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
```

- [ ] **Step 2: 修改 `NumericInput` — 超范围红框闪烁动画**

需要添加 import：

```kotlin
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
```

修改 `NumericInput` 函数签名，添加 `isOutOfRange` 参数：

```kotlin
@Composable
private fun NumericInput(
    value: String,
    onValueChanged: (String) -> Unit,
    unit: String?,
    normalMin: Double?,
    normalMax: Double?,
    isOutOfRange: Boolean = false
)
```

在 `InspectionItemCard` 中调用 `NumericInput` 时传入新参数：

```kotlin
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
```

在 `NumericInput` 内部，修改范围判断逻辑和闪烁动画：

```kotlin
    // 判断当前值是否在正常范围内
    val numericValue = value.toDoubleOrNull()
    val isInRange = when {
        value.isBlank() -> null
        numericValue == null -> false
        isOutOfRange -> false                         // 超范围
        normalMin != null && normalMax != null ->
            numericValue >= normalMin && numericValue <= normalMax
        else -> true
    }

    // 超范围闪烁状态
    var flashCount by remember { mutableStateOf(0) }
    var isFlashing by remember { mutableStateOf(false) }

    // 检测到新超范围时触发闪烁
    LaunchedEffect(isOutOfRange) {
        if (isOutOfRange) {
            flashCount = 0
            isFlashing = true
            repeat(6) {  // 3 次闪烁 = 6 次切换（红/透明）
                flashCount++
                delay(150)
            }
            isFlashing = false
        }
    }

    // 闪烁中的边框颜色
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

    val textColor = if (isOutOfRange) Color(0xFFF44336) else Color.Unspecified
```

然后在 `OutlinedTextField` 中使用 `flashBorderColor` 和 `textColor`：

```kotlin
        OutlinedTextField(
            value = value,
            onValueChange = onValueChanged,
            label = { ... },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = isOutOfRange,
            supportingText = if (isOutOfRange) {
                { Text("数值超出正常范围，将作为异常记录", color = Color(0xFFF44336)) }
            } else null,
            textStyle = LocalTextStyle.current.copy(color = textColor),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = flashBorderColor,
                unfocusedBorderColor = flashBorderColor
            )
        )
```

需要额外添加 import：

```kotlin
import androidx.compose.material3.LocalTextStyle
```

- [ ] **Step 3: 编译验证**

```bash
cd C:\Users\33215\source\repos\webapi\machine_check && ./gradlew :app:assembleDebug
```

预期: BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
cd C:\Users\33215\source\repos\webapi\machine_check
git add app/src/main/java/com/machine_check/inspection/ui/inspection/InspectionScreen.kt
git commit -m "feat(android): dynamic remark label + out-of-range red flash animation

- Remark label changes to required (red) when abnormal selected
- Out-of-range numeric values: red border + font, 3-flash pulse
- Out-of-range values can submit as abnormal records

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## 完成验证

全部 Task 完成后：

```bash
# 1. Web 前端测试
# 浏览器打开点检表 → 点击 × 单元格 → 确认 tooltip 显示备注
# → 确认超范围数值红字显示

# 2. Android 测试
# 选择「✗ 异常」→ 确认备注标签变红必填
# → 不填备注提交 → 确认被阻止
# → 填备注提交 → 确认通过
# → numeric 输入超范围值 → 确认红框闪烁3次 → 提交成功（isNormal=false）
```
