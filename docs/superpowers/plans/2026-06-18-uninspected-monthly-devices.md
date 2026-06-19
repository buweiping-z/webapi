# 当月未点检设备清单 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Web 前端和 Android 端同时新增「当月完全未点检设备」清单功能，共用同一个后端 API。

**Architecture:** 后端新增 `GET /api/Inspection/uninspected-monthly` 端点，查询当月所有频率下零记录的设备并返回 `device_location`。Web 端新增面板始终可见，Android 端在 ScanScreen 步骤 3 标题右侧加 checkbox 展开列表（纯提醒，不跳转）。

**Tech Stack:** ASP.NET Core 8.0 + EF Core（后端）、vanilla HTML/JS/CSS（Web 前端）、Kotlin + Jetpack Compose + Retrofit（Android）

---

## 文件结构

| 操作 | 文件 | 职责 |
|------|------|------|
| 修改 | `Controllers/InspectionController.cs` | 新增 API Action + 请求 DTO |
| 修改 | `html/index.html` | 新增面板 HTML + CSS + JS 函数 |
| 修改 | `machine_check/.../data/network/ApiService.kt` | 新增 Retrofit 接口方法 |
| 修改 | `machine_check/.../data/models/Models.kt` | 新增响应数据类 |
| 修改 | `machine_check/.../data/repository/InspectionRepository.kt` | 新增仓库方法 |
| 修改 | `machine_check/.../ui/scan/ScanViewModel.kt` | 新增 state + 加载方法 |
| 修改 | `machine_check/.../ui/scan/ScanScreen.kt` | 新增 checkbox + 展开列表 UI |

---

### Task 1: 后端 — 新增 `uninspected-monthly` API

**Files:**
- Modify: `Controllers/InspectionController.cs`

- [ ] **Step 1: 添加响应 DTO**

在 `InspectionController.cs` 底部（其他 DTO 所在区域 `PagedResponse` 之后）添加：

```csharp
    public class UninspectedMonthlyResponse
    {
        public int Year { get; set; }
        public int Month { get; set; }
        public int TotalDevices { get; set; }
        public int UninspectedCount { get; set; }
        public List<UninspectedDeviceItem> UninspectedDevices { get; set; } = new();
    }

    public class UninspectedDeviceItem
    {
        public string DeviceModel { get; set; } = string.Empty;
        public string DeviceName { get; set; } = string.Empty;
        public string DeviceLocation { get; set; } = string.Empty;
    }
```

- [ ] **Step 2: 添加 API Action 方法**

在 `InspectionController` 类中 `GetMonthlySummary` 方法之后、`ImportPlan` 方法之前（约第 1161 行之后）插入：

```csharp
        // 获取当月完全未点检的设备清单
        [HttpGet("uninspected-monthly")]
        public async Task<IActionResult> GetUninspectedMonthly(int year, int month)
        {
            try
            {
                var monthStart = new DateTime(year, month, 1);
                var monthEnd = monthStart.AddMonths(1).AddTicks(-1);

                // 所有有模板的设备
                var allDeviceModels = await _context.InspectionTemplates
                    .Select(t => t.DeviceModel)
                    .Distinct()
                    .ToListAsync();

                // 当月有任意记录的设备
                var inspectedModels = await _context.InspectionRecords
                    .Where(r => r.InspectionTime >= monthStart
                             && r.InspectionTime <= monthEnd)
                    .Select(r => r.DeviceModel)
                    .Distinct()
                    .ToListAsync();

                var inspectedSet = new HashSet<string>(inspectedModels);

                // 当月完全无记录的设备
                var uninspectedModels = allDeviceModels
                    .Where(m => !inspectedSet.Contains(m))
                    .ToList();

                // JOIN devices 获取 location
                var uninspectedDevices = new List<UninspectedDeviceItem>();
                if (uninspectedModels.Count > 0)
                {
                    uninspectedDevices = await _context.Devices
                        .Where(d => uninspectedModels.Contains(d.DeviceModel))
                        .Select(d => new UninspectedDeviceItem
                        {
                            DeviceModel = d.DeviceModel,
                            DeviceName = d.DeviceName,
                            DeviceLocation = d.DeviceLocation ?? ""
                        })
                        .OrderBy(d => d.DeviceLocation)
                        .ThenBy(d => d.DeviceModel)
                        .ToListAsync();
                }

                return Ok(new UninspectedMonthlyResponse
                {
                    Year = year,
                    Month = month,
                    TotalDevices = allDeviceModels.Count,
                    UninspectedCount = uninspectedModels.Count,
                    UninspectedDevices = uninspectedDevices
                });
            }
            catch (Exception ex)
            {
                return StatusCode(500, new { error = ex.Message });
            }
        }
```

- [ ] **Step 3: 编译验证**

```bash
dotnet build
```

预期：Build succeeded，无编译错误。

- [ ] **Step 4: 运行并手动测试 API**

```bash
dotnet run
```

用浏览器访问：`http://localhost:5039/api/Inspection/uninspected-monthly?year=2026&month=6`

预期返回 JSON，包含 `totalDevices`、`uninspectedCount`、`uninspectedDevices` 字段。

- [ ] **Step 5: Commit**

```bash
git add Controllers/InspectionController.cs
git commit -m "feat: add uninspected-monthly API endpoint

Returns devices with zero inspection records in the given month,
joined with devices table to include device_location.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: Web 前端 — 新增「当月未点检设备」面板

**Files:**
- Modify: `html/index.html`

- [ ] **Step 1: 添加 CSS 样式**

在 `</style>` 之前（约第 1199 行 `@media (max-width: 480px)` 结束 `}` 之后）添加：

```css
/* ============================================================
   Uninspected Monthly Panel
   ============================================================ */
#uninspectedMonthlyPanel {
    margin: 12px 0;
    padding: 18px 22px;
    background: var(--danger-light);
    border: 1px solid var(--danger-border);
    border-radius: var(--radius);
    box-shadow: var(--shadow-xs);
}

#uninspectedMonthlyPanel.all-clear {
    background: var(--success-light);
    border-color: var(--success-border);
}

.panel-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 12px;
    font-family: var(--font-heading);
    font-size: 15px;
    font-weight: 600;
    color: #991B1B;
}

#uninspectedMonthlyPanel.all-clear .panel-header {
    color: #065F46;
}

.uninspected-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 13px;
}

.uninspected-table th {
    background: rgba(239,68,68,0.08);
    font-family: var(--font-heading);
    font-weight: 600;
    font-size: 11px;
    color: #991B1B;
    text-transform: uppercase;
    letter-spacing: 0.5px;
}

.uninspected-table td {
    color: #7F1D1D;
}

/* mobile */
@media (max-width: 768px) {
    #uninspectedMonthlyPanel {
        padding: 12px 14px;
    }
    .uninspected-table {
        font-size: 11px;
    }
    .uninspected-table th, .uninspected-table td {
        padding: 5px 3px;
    }
}
```

- [ ] **Step 2: 添加 HTML 面板**

在 `#frequencySummaryBar` 关闭 `</div>` 之后、`</div>`（content-body 结束）之前（约第 1389 行）添加：

```html
            <!-- Uninspected Monthly Panel -->
            <div id="uninspectedMonthlyPanel" style="display:none;">
                <div class="panel-header" id="uninspectedPanelHeader">
                    ⚠️ 当月未点检设备
                </div>
                <div id="uninspectedPanelContent"></div>
            </div>
```

- [ ] **Step 3: 添加 JS 函数**

在 `loadMonthlySummary` 函数之后（约第 1773 行之后）添加：

```javascript
        // 加载当月完全未点检的设备清单
        async function loadUninspectedMonthly() {
            const yearSelect = document.getElementById('yearSelect');
            const monthSelect = document.getElementById('monthSelect');
            if (!yearSelect || !monthSelect) return;

            const year = parseInt(yearSelect.value);
            const month = parseInt(monthSelect.value);
            if (!year || !month) return;

            const panel = document.getElementById('uninspectedMonthlyPanel');
            const header = document.getElementById('uninspectedPanelHeader');
            const content = document.getElementById('uninspectedPanelContent');

            try {
                const response = await axios.get(`${API_BASE_URL}/api/Inspection/uninspected-monthly`, {
                    params: { year, month }
                });
                const data = response.data;

                if (!panel) return;
                panel.style.display = 'block';

                if (data.uninspectedCount === 0) {
                    panel.classList.add('all-clear');
                    header.innerHTML = '✅ 当月所有设备均已点检';
                    content.innerHTML = '';
                } else {
                    panel.classList.remove('all-clear');
                    header.innerHTML = `⚠️ 当月未点检设备（共 ${data.uninspectedCount} 台）`;

                    let html = '<div style="overflow-x:auto;"><table class="uninspected-table">';
                    html += '<thead><tr><th style="min-width:120px;">设备名称</th><th style="min-width:200px;">所在位置</th></tr></thead>';
                    html += '<tbody>';
                    data.uninspectedDevices.forEach(d => {
                        const loc = d.deviceLocation || '未配置位置';
                        html += `<tr><td>${d.deviceModel}</td><td>${loc}</td></tr>`;
                    });
                    html += '</tbody></table></div>';
                    content.innerHTML = html;
                }
            } catch (error) {
                console.error('加载未点检设备清单失败:', error);
                if (panel) panel.style.display = 'none';
            }
        }
```

- [ ] **Step 4: 在 `loadMonthlySummary()` 末尾追加调用**

在 `loadMonthlySummary` 函数的 `catch` 块之前（约第 1763 行 `} catch (error) {` 之前），`freqBar` 显示逻辑之后添加一行：

```javascript
            // 加载当月完全未点检设备清单
            loadUninspectedMonthly();
```

即在 `if (freqBar) freqBar.style.display = 'block';` 和 `fillFrequencyRow` 调用之后、`} catch (error) {` 之前。

- [ ] **Step 5: 验证**

```bash
cd html && python -m http.server 8080
```

浏览器打开 `http://localhost:8080/`，登录后选择年份月份点击查询，确认频率摘要下方出现「当月未点检设备」面板。

- [ ] **Step 6: Commit**

```bash
git add html/index.html
git commit -m "feat: add uninspected monthly device panel to web frontend

Displays devices with zero inspection records in the current month
below the frequency summary bar. Green when all clear, red with
device list otherwise.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: Android — API + Model + Repository

**Files:**
- Modify: `machine_check/app/src/main/java/com/machine_check/inspection/data/network/ApiService.kt`
- Modify: `machine_check/app/src/main/java/com/machine_check/inspection/data/models/Models.kt`
- Modify: `machine_check/app/src/main/java/com/machine_check/inspection/data/repository/InspectionRepository.kt`

- [ ] **Step 1: 添加响应数据类**

在 `Models.kt` 文件末尾添加：

```kotlin
/**
 * 当月完全未点检设备信息（跨所有频率，零记录）
 */
data class UninspectedMonthlyDevice(
    val deviceModel: String,
    val deviceName: String,
    val deviceLocation: String
)

/**
 * 当月未点检设备列表响应
 */
data class UninspectedMonthlyResponse(
    val year: Int,
    val month: Int,
    val totalDevices: Int,
    val uninspectedCount: Int,
    val uninspectedDevices: List<UninspectedMonthlyDevice>
)
```

- [ ] **Step 2: 添加 API 接口方法**

在 `ApiService.kt` 末尾（`getUninspectedMandatoryLocations()` 之后、`}` 之前）添加：

```kotlin
    /** 获取当月完全未点检的设备清单（所有频率均无记录） */
    @GET("/api/Inspection/uninspected-monthly")
    suspend fun getUninspectedMonthly(
        @Query("year") year: Int,
        @Query("month") month: Int
    ): Response<UninspectedMonthlyResponse>
```

同时在 import 区域（自动 import 或手动添加）确认有 `@Query` 的 import。

- [ ] **Step 3: 添加仓库方法**

在 `InspectionRepository.kt` 末尾（`getFrequenciesAvailable` 之后、类的 `}` 之前）添加：

```kotlin
    /** 获取当月完全未点检的设备清单 */
    suspend fun getUninspectedMonthly(year: Int, month: Int): Result<UninspectedMonthlyResponse> {
        return try {
            val response = api.getUninspectedMonthly(year, month)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("获取未点检清单失败: 服务端返回了空响应体"))
                }
            } else {
                Result.failure(Exception("获取未点检清单失败: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("网络连接失败: ${e.message ?: e.toString()}"))
        }
    }
```

在文件顶部 import 区域确认有 `import com.machine_check.inspection.data.models.UninspectedMonthlyResponse`（Kotlin 文件同 package 下自动可见，无需显式 import）。

- [ ] **Step 4: 编译验证**

```bash
cd machine_check && ./gradlew :app:assembleDebug
```

预期：BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**

```bash
cd machine_check
git add app/src/main/java/com/machine_check/inspection/data/network/ApiService.kt
git add app/src/main/java/com/machine_check/inspection/data/models/Models.kt
git add app/src/main/java/com/machine_check/inspection/data/repository/InspectionRepository.kt
git commit -m "feat(android): add uninspected-monthly API + model + repository

Add Retrofit API method, response data classes, and repository
wrapper for the new uninspected-monthly endpoint.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: Android — ViewModel 状态 + 加载方法

**Files:**
- Modify: `machine_check/app/src/main/java/com/machine_check/inspection/ui/scan/ScanViewModel.kt`

- [ ] **Step 1: 添加 state 字段**

在 `ScanUiState` data class 末尾（`isLoadingUninspected` 之后）添加：

```kotlin
    // 当月完全未点检设备清单（checkbox 勾选后显示）
    val showUninspectedMonthly: Boolean = false,
    val uninspectedMonthlyList: List<UninspectedMonthlyDevice> = emptyList(),
    val isLoadingUninspectedMonthly: Boolean = false,
    val uninspectedMonthlyCount: Int = 0
```

同时在文件顶部添加 import：
```kotlin
import com.machine_check.inspection.data.models.UninspectedMonthlyDevice
```

- [ ] **Step 2: 添加加载方法 + 切换方法**

在 `ScanViewModel` 类中（`loadUninspectedList()` 之后，私有方法区域之前）添加：

```kotlin
    /** 切换「当月未点检」checkbox 并加载数据 */
    fun toggleUninspectedMonthly() {
        val current = _uiState.value.showUninspectedMonthly
        if (!current) {
            // 首次展开时加载数据
            loadUninspectedMonthlyData()
        }
        _uiState.update { it.copy(showUninspectedMonthly = !current) }
    }

    /** 从后端加载当月完全未点检的设备清单 */
    private fun loadUninspectedMonthlyData() {
        val now = java.util.Calendar.getInstance()
        val year = now.get(java.util.Calendar.YEAR)
        val month = now.get(java.util.Calendar.MONTH) + 1

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingUninspectedMonthly = true) }
            repository.getUninspectedMonthly(year, month).fold(
                onSuccess = { result ->
                    _uiState.update {
                        it.copy(
                            isLoadingUninspectedMonthly = false,
                            uninspectedMonthlyList = result.uninspectedDevices,
                            uninspectedMonthlyCount = result.uninspectedCount
                        )
                    }
                },
                onFailure = {
                    _uiState.update {
                        it.copy(isLoadingUninspectedMonthly = false)
                    }
                }
            )
        }
    }
```

- [ ] **Step 3: 在 `ON_RESUME` 时刷新（已有逻辑追加）**

在 `ScanScreen.kt` 的 `DisposableEffect` 中 `ON_RESUME` 分支已有 `viewModel.refreshFrequencies()` 和 `viewModel.loadUninspectedList()`。但 `loadUninspectedMonthlyData` 是按需加载的（勾选时触发），不需要在 `ON_RESUME` 中自动调用。但如果 checkbox 已展开，从点检页返回时应刷新数据。

修改 `ScanViewModel`，新增一个公共方法供 `ON_RESUME` 调用：

```kotlin
    /** 从点检页返回时刷新数据 */
    fun refreshAfterInspection() {
        refreshFrequencies()
        loadUninspectedList()
        // 如果当月未点检列表已展开，刷新
        if (_uiState.value.showUninspectedMonthly) {
            loadUninspectedMonthlyData()
        }
    }
```

然后在 `ScanScreen.kt` 的 `DisposableEffect` 中将 `ON_RESUME` 回调改为调用 `viewModel.refreshAfterInspection()`：

```kotlin
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAfterInspection()
            }
```

- [ ] **Step 4: 编译验证**

```bash
cd machine_check && ./gradlew :app:assembleDebug
```

预期：BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/machine_check/inspection/ui/scan/ScanViewModel.kt
git add app/src/main/java/com/machine_check/inspection/ui/scan/ScanScreen.kt
git commit -m "feat(android): add ViewModel state + methods for uninspected monthly

Add showUninspectedMonthly toggle, uninspectedMonthlyList state,
and loadUninspectedMonthlyData method with ON_RESUME refresh.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: Android — Checkbox + 展开列表 UI

**Files:**
- Modify: `machine_check/app/src/main/java/com/machine_check/inspection/ui/scan/ScanScreen.kt`

- [ ] **Step 1: 修改步骤 3 卡片 — 标题行添加 checkbox**

将步骤 3「点检频率」卡片的标题行部分从：

```kotlin
                        Text(
                            text = "步骤 3: 点检频率",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
```

改为：

```kotlin
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "步骤 3: 点检频率",
                                style = MaterialTheme.typography.titleMedium
                            )
                            // 当月未点检 checkbox — 工号验证通过后才可用
                            Row(
                                verticalAlignment = Alignment.CenterVertically
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
```

- [ ] **Step 2: 添加展开的未点检设备列表**

在步骤 3 FilterChip 的 `Row` 结束之后、步骤 3 卡片 `Column` 的 `}` 之前（即步骤 3 卡片末尾），添加：

```kotlin

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
```

注意需要在文件顶部添加 `HorizontalDivider` 的 import：
```kotlin
import androidx.compose.material3.HorizontalDivider
```

- [ ] **Step 3: 编译验证**

```bash
cd machine_check && ./gradlew :app:assembleDebug
```

预期：BUILD SUCCESSFUL。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/machine_check/inspection/ui/scan/ScanScreen.kt
git commit -m "feat(android): add uninspected monthly checkbox + expandable list

Checkbox placed right of step 3 title, aligns with monthly chip.
When checked, shows device_location list (read-only reminder).
Does not navigate — scanning required for actual inspection.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## 完成验证

全部 Task 完成后：

```bash
# 1. 后端测试
curl "http://localhost:5039/api/Inspection/uninspected-monthly?year=2026&month=6"

# 2. Web 前端测试
# 浏览器打开 http://localhost:8080/，登录 → 选年/月 → 查询 → 确认面板显示

# 3. Android 测试
# 模拟器/真机运行，扫码工号 → 确认步骤 3 卡片右侧出现 checkbox
# → 勾选 → 确认展开 location 列表 → 取消勾选 → 列表收起
```
