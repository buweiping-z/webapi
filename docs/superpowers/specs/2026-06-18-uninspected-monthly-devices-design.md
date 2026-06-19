# 当月未点检设备清单 — 设计文档

**日期：** 2026-06-18  
**状态：** 已确认  
**关联：** webapi（后端 + Web 前端）、machine_check（Android 端）

---

## 1. 目标

1. **Web 前端**：在 index.html 新增「当月未点检设备清单」面板，列出当月一次都没有点检过的设备（含位置）。
2. **Android 端**：在 ScanScreen 步骤 3 卡片右侧新增「当月未点检」checkbox，勾选后展开 `device_location` 列表，供现场人员参考、前去扫码补检（纯提醒，不跳转）。

---

## 2. 核心定义

**「当月未点检」≡ 当月该设备在任意频率（日/周/月）下都没有任何 `inspection_records` 记录。**

不做必须/选择点检区分，也不做频率筛选——完全零记录即视为未检。

---

## 3. 后端：新增 API

### `GET /api/Inspection/uninspected-monthly`

**参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| year | int | 是 | 年份 |
| month | int | 是 | 月份 |

**SQL 逻辑：**

```sql
SELECT d.device_model, d.device_name, d.device_location
FROM devices d
WHERE EXISTS (
    SELECT 1 FROM inspection_templates t
    WHERE t.device_model = d.device_model
)
AND NOT EXISTS (
    SELECT 1 FROM inspection_records r
    WHERE r.device_model = d.device_model
      AND r.inspection_time >= @monthStart
      AND r.inspection_time <= @monthEnd
)
ORDER BY d.device_location, d.device_model
```

- 使用 `NOT EXISTS` 而非 `NOT IN`，避免子查询结果集过大时性能退化
- `monthStart` / `monthEnd` 由 C# 端计算后传入参数化查询

**返回格式：**

```json
{
  "year": 2026,
  "month": 6,
  "totalDevices": 50,
  "uninspectedCount": 3,
  "uninspectedDevices": [
    {
      "deviceModel": "CT-200",
      "deviceName": "CT-200",
      "deviceLocation": "dongtai-A区-冲压-1号"
    }
  ]
}
```

**实现位置：** `Controllers/InspectionController.cs`，新增 Action 方法。

---

## 4. Web 前端（index.html）

### 4.1 位置

在 `#frequencySummaryBar` 下方。

### 4.2 外观

- 卡片式面板，使用 `.summary-card.uninspected` 风格（浅红/警告色调）
- 标题：`⚠️ 当月未点检设备（共 N 台）`
- 内容：两列表格 — 设备名称 | 所在位置
- 0 台未检时显示绿色提示：`✅ 当月所有设备均已点检`

### 4.3 数据刷新

- `loadMonthlySummary()` 执行时同步调用新 API
- 独立函数 `loadUninspectedMonthly()` 负责获取并渲染

### 4.4 响应式

- 桌面端：始终可见
- 移动端（≤768px）：面板宽度自适应，字体缩小

---

## 5. Android 端（ScanScreen）

### 5.1 位置

步骤 3「点检频率」卡片内，与标题同一水平行：

```
┌──────────────────────────────────────────────────┐
│  步骤 3: 点检频率                     ┌──────────┐│
│                                       │☐ 当月未检 ││
│  ┌──────┐ ┌──────┐                   │  (3台)    ││
│  │ 日检  │ │ 周检  │                   └──────────┘│
│  └──────┘ └──────┘ ┌──────┐                       │
│                    │ 月检  │                       │
│                    └──────┘                       │
└──────────────────────────────────────────────────┘
```

### 5.2 交互

- **默认**：不勾选，列表隐藏
- **勾选后**：在频率卡片下方展开列表，每行格式：`📍 deviceLocation — deviceModel`
  ```
  📍 dongtai-A区-冲压-1号 — CT-200
  📍 dongtai-B区-焊接-3号 — CT-301
  📍 dongtai-C区-组装-2号 — CM-500
  ```
  不区分频率——因为"当月完全未检"指所有频率均无记录。
- **不跳转**，仅提示现场人员前去扫码补检
- **取消勾选**：列表收起

### 5.3 数据来源

- 新增 API：`GET /api/Inspection/uninspected-monthly?year=&month=`
- 在 `ApiService.kt` 新增接口方法
- 在 `ScanViewModel` 新增 `loadUninspectedMonthly()` 方法
- 工号验证通过后自动加载；从点检页返回 `ON_RESUME` 时刷新

### 5.4 显示条件

- 工号验证通过后 checkbox 才可用
- 无未检设备时 checkbox 显示 `☐ 当月未检（0台）` 并置灰

---

## 6. 数据流

```
┌──────────────┐     ┌──────────────────┐     ┌─────────────┐
│  MySQL       │────▶│  ASP.NET API     │────▶│  index.html │
│  devices     │     │  uninspected-    │     │  (Web 端)   │
│  records     │     │  monthly         │     └─────────────┘
│  templates   │     └──────────────────┘
└──────────────┘              │
                              │     ┌─────────────┐
                              └────▶│  ScanScreen  │
                                    │  (Android)   │
                                    └─────────────┘
```

同一 API 同时服务 Web 和 Android 端。

---

## 7. 边界情况

| 场景 | 处理 |
|------|------|
| 当月无任何设备 | 显示「暂无设备」 |
| 所有设备均已点检 | 显示绿色 ✅ 提示 |
| 网络错误 | API 失败时面板显示「加载失败」，不影响其他功能 |
| 当月有设备但全未点检 | 全部列出 |
| device_location 为空 | 显示「未配置位置」 |
| 设备在 devices 表中但 templates 为空 | 不纳入统计（`EXISTS` 过滤） |

---

## 8. 影响范围

### 后端
- `Controllers/InspectionController.cs`：新增 1 个 Action
- 无数据库 schema 变更

### Web 前端
- `html/index.html`：新增 HTML 面板 + CSS 样式 + JS 函数
- 无新依赖

### Android 端
- `data/network/ApiService.kt`：新增 1 个 Retrofit 方法
- `data/models/Models.kt`：新增响应数据类
- `data/repository/InspectionRepository.kt`：新增 1 个仓库方法
- `ui/scan/ScanViewModel.kt`：新增 state 字段 + 加载方法
- `ui/scan/ScanScreen.kt`：新增 checkbox + 展开列表 UI
- 无新依赖

---

## 9. 不做

- 列表中点击跳转（必须扫码完成点检）
- 按频率过滤（展示所有频率下均无记录的设备）
- 桌面端 checkbox（桌面端始终显示面板）
- 推送通知 / 后台提醒
