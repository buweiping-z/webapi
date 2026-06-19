# 异常备注显示 + 超范围处理 — 设计文档

**日期：** 2026-06-18  
**状态：** 已确认  
**关联：** webapi（后端 + Web 前端）、machine_check（Android 端）

---

## 1. Web 前端：异常单元格点击显示备注 Tooltip

### 1.1 触发方式

- 点击表格中 `result-abnormal`（×）单元格 → 显示 tooltip 气泡
- 再次点击同一格或点击其他格 → 关闭
- 仅 `×`（异常）格可点击，`○`（正常）和 `/`（未检）不响应

### 1.2 Tooltip 样式

- 深色半透明背景 + 白色文字
- 小三角箭头指向源单元格
- 显示内容：`备注：xxx`（无备注时显示「无备注」）
- 自动定位在单元格旁边（优先上方，空间不足时下方）

### 1.3 实现要点

- 渲染表格时把 `remark` 存入 `data-remark` 属性
- JS 通过事件委托监听 `td.result-abnormal` 的 click
- CSS 控制 tooltip 的显示/隐藏/定位

### 1.4 超范围数值红字

- 单元格 `isNormal === false` 且值不是 ○/×/ → 超范围数值
- 新增 CSS `.result-outofrange { color: #DC2626; font-weight: 700; }`
- 渲染时检测并应用此类

---

## 2. Android：异常必须备注 + 超范围处理

### 2.1 异常项备注必填

**交互：**
- 点击「✗ 异常」→ 备注标签从「备注（可选）」变为「备注（必填）」+ 红色
- 点击「✓ 正常」→ 恢复「备注（可选）」

**提交校验：**
- 遍历所有项：如果 `selectedNormal == false` 且 `remark` 为空 → 该卡片红框 + `isValid = false`
- 全局提示：「异常项必须填写备注」
- 阻止提交直到所有异常项都有备注

### 2.2 Numeric 超范围处理

**逻辑变更：**
- **旧逻辑：** 超范围 → 无法提交（校验失败）
- **新逻辑：** 超范围 → 可以提交，但 `isNormal = false`，remark 可选

**提交校验变更：**
- Numeric 项只需 `numericValue` 非空且为有效数字
- 不再校验是否在 `normalMin ~ normalMax` 范围内
- 超范围提交时 `resultValue = 数值文本`，`isNormal = false`

**超范围视觉：**
- 输入框边框变红、字体变红
- **边框闪烁 3 次**：用 `Animatable` 或状态切换实现红色脉冲动画（约 1 秒完成）
- 每次闪烁：红色 → 透明 → 红色（交替 3 次）

### 2.3 备注必填 vs 可选对照表

| 类型 | 状态 | 备注 |
|------|------|------|
| normal_abnormal → ✓ 正常 | 正常 | 可选 |
| normal_abnormal → ✗ 异常 | 异常 | **必填** |
| numeric → 范围内 | 正常 | 可选 |
| numeric → 超范围 | 异常 | 可选 |

---

## 3. 数据流

```
┌──────────────┐     ┌──────────────────┐     ┌─────────────┐
│  Android App │────▶│  POST submit-full│────▶│  MySQL      │
│              │     │  isNormal=false  │     │  results    │
│ 异常+备注    │     │  resultValue=数值 │     │  + remark   │
│ 超范围       │     └──────────────────┘     └─────────────┘
└──────────────┘                                       │
                                                       │
┌──────────────┐     ┌──────────────────┐              │
│  index.html  │◀────│  GET records/   │◀─────────────┘
│              │     │  monthly        │
│ tooltip 备注 │     └──────────────────┘
│ 红字超范围   │
└──────────────┘
```

---

## 4. 影响范围

### Web 前端
- `html/index.html`：
  - 新增 CSS：`.result-outofrange`、`.remark-tooltip`
  - 修改 `renderReadOnlyTable()`：存储 data 属性 + 检测超范围
  - 新增 JS：tooltip 显示/隐藏逻辑

### Android
- `InspectionScreen.kt`：
  - 异常选择后备注标签变必填
  - 超范围闪烁动画
- `InspectionViewModel.kt`：
  - 修改 `onNormalAbnormalChanged()`：标记备注必填
  - 修改 `submitInspection()`：校验逻辑变更
  - 超范围 isNormal=false 处理

### 后端
- 无需修改（`submit-full` 已支持 `isNormal=false` + `resultValue=数值`）

---

## 5. 边界情况

| 场景 | 处理 |
|------|------|
| 点击非异常格 | tooltip 关闭 |
| 备注为空字符串 | tooltip 显示「无备注」 |
| 异常项无备注提交 | 阻止 + 提示 |
| numeric 值为空 | 提交阻止（与其他项一致） |
| numeric 值非数字 | 提交阻止 |
| 超范围闪烁动画未完成时切换值 | 取消动画，以新值状态为准 |
| 异常 → 切换回正常 | 备注恢复可选 |
