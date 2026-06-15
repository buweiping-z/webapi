# 印章栏移回 Header 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将「承认」「确认」「担当」三个电子印章从 filters 工具栏移回 header 区域

**Architecture:** 纯前端 HTML/CSS 变更，移动3个 `.stamp-item` DOM 元素从 `#signatureCorner`（filters 栏）到 `.header` 内部，并调适配色和响应式样式。不涉及 JS 逻辑变更。

**Tech Stack:** 纯 HTML/CSS，无框架

---

## 文件结构

| 文件 | 操作 | 职责 |
|------|------|------|
| `html/index.html` | 修改 | HTML 元素移动 + CSS 样式调适 |

---

## 当前状态

```
.header
  └─ .header-content          ← 标题 "设备治工具点检表"
  └─ #printDeviceInfo         ← 打印专用（屏幕隐藏）

.filters#signatureCorner
  └─ 筛选控件 (设备选择、年月等)
  └─ .stamp-item × 3          ← 印章目前在这里（承认/确认/担当）
```

## 目标状态

```
.header
  └─ .header-content          ← 标题（flex: space-between）
       ├─ .header-title       ← icon + h1 标题组（左）
       └─ .header-stamps      ← 印章容器（右，新增）
            └─ .stamp-item × 3
  └─ #printDeviceInfo

.filters#signatureCorner
  └─ 筛选控件（不再包含印章）
```

---

### Task 1: 重构 header 布局 + 移动印章

**Files:**
- Modify: `html/index.html` (HTML 部分)

- [ ] **Step 1: 重构 `.header-content` 结构** — 将标题包裹为 `.header-title`，添加右侧 `.header-stamps`

在 `html/index.html` 第1172-1187行区域，将现有 `.header-content`：

```html
        <div class="header">
            <div class="header-content">
                <!-- Brand mark -->
                <div class="header-mark">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <rect x="3" y="3" width="18" height="18" rx="3"/>
                        <path d="M9 3v18"/>
                        <path d="M3 9h18"/>
                        <path d="M8 12l3 3 5-5"/>
                    </svg>
                </div>
                <h1>设备治工具点检表</h1>
            </div>
```

改为：

```html
        <div class="header">
            <div class="header-content">
                <div class="header-title">
                    <!-- Brand mark -->
                    <div class="header-mark">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                            <rect x="3" y="3" width="18" height="18" rx="3"/>
                            <path d="M9 3v18"/>
                            <path d="M3 9h18"/>
                            <path d="M8 12l3 3 5-5"/>
                        </svg>
                    </div>
                    <h1>设备治工具点检表</h1>
                </div>
                <!-- Signature stamps — right side of header -->
                <div class="header-stamps">
                    <div class="stamp-item">
                        <span class="stamp-role-label">承 认</span>
                        <div class="stamp-seal unsigned" id="stampApprover">
                            <span class="stamp-name" id="approverText">未签</span>
                        </div>
                    </div>
                    <div class="stamp-item">
                        <span class="stamp-role-label">确 认</span>
                        <div class="stamp-seal unsigned" id="stampConfirmer">
                            <span class="stamp-name" id="confirmerText">未签</span>
                        </div>
                    </div>
                    <div class="stamp-item">
                        <span class="stamp-role-label">担 当</span>
                        <div class="stamp-seal unsigned" id="stampOperator">
                            <span class="stamp-name" id="operatorText">未签</span>
                        </div>
                    </div>
                </div>
            </div>
```

- [ ] **Step 2: 从 `#signatureCorner` 中删除印章**

删除 `html/index.html` 中 `#signatureCorner` 内的三个 `.stamp-item` 块（原第1233-1250行附近）：

将：
```html
                <!-- Signature stamps -->
                <div class="stamp-item" style="margin-left:auto;">
                    <span class="stamp-role-label">承 认</span>
                    <div class="stamp-seal unsigned" id="stampApprover">
                        <span class="stamp-name" id="approverText">未签</span>
                    </div>
                </div>
                <div class="stamp-item">
                    <span class="stamp-role-label">确 认</span>
                    <div class="stamp-seal unsigned" id="stampConfirmer">
                        <span class="stamp-name" id="confirmerText">未签</span>
                    </div>
                </div>
                <div class="stamp-item">
                    <span class="stamp-role-label">担 当</span>
                    <div class="stamp-seal unsigned" id="stampOperator">
                        <span class="stamp-name" id="operatorText">未签</span>
                    </div>
                </div>
```

删除（不留空行）。

- [ ] **Step 3: 验证页面正常渲染**

用浏览器打开 `html/index.html`：
1. 确认 header 左侧为 icon + 标题，右侧为三个印章（承认/确认/担当）
2. 确认印章显示为"未签"状态（白色半透明边框，在深色背景上可见）
3. 确认 filters 栏不再显示印章
4. 登录后选择设备和月份，确认印章签名/取消签名仍然正常工作
5. 确认导出 PDF 中印章仍然正常显示

---

### Task 2: 添加 header 印章 CSS 样式

**Files:**
- Modify: `html/index.html` (CSS 部分)

- [ ] **Step 1: 在 `:root` 块之后、`.stamp-item` 定义之前，添加 `.header-stamps` 容器样式**

在 `html/index.html` 第197行（`/* Signature Corner — Stamps/Seals (inside filters bar) */` 注释行）之前插入：

```css
        /* ============================================================
           Header Layout — title left, stamps right
           ============================================================ */
        .header-content {
            justify-content: space-between;
        }

        .header-title {
            display: flex;
            align-items: center;
            gap: 16px;
        }

        /* ============================================================
           Header Stamps — right side of header
           ============================================================ */
        .header-stamps {
            display: flex;
            align-items: center;
            gap: 28px;
            flex-shrink: 0;
        }

        .header .stamp-role-label {
            color: rgba(255, 255, 255, 0.80);
        }
```

- [ ] **Step 2: 更新响应式 CSS**

在 `@media (max-width: 1024px)` 块中（约第1094行），在现有 `.header-content { flex-direction: column; gap: 10px; }` 规则中追加 `.header-stamps` 和 `.header-title`：

将：
```css
            .header-content { flex-direction: column; gap: 10px; }
```

改为：
```css
            .header-content { flex-direction: column; gap: 10px; align-items: center; }
            .header-stamps { gap: 20px; }
```

在 `@media (max-width: 768px)` 块中，将现有 `.stamp-role-label` 规则：

```css
            .stamp-role-label { font-size: 11px; color: var(--text-secondary); }
```

改为：

```css
            .stamp-role-label { font-size: 11px; color: var(--text-secondary); }
            .header .stamp-role-label { font-size: 11px; color: rgba(255, 255, 255, 0.80); }
            .header-stamps { gap: 14px; }
```

在 `@media (max-width: 480px)` 块中，将现有 `.stamp-role-label` 规则：

```css
            .stamp-role-label { font-size: 10px; letter-spacing: 2px; }
```

改为：

```css
            .stamp-role-label { font-size: 10px; letter-spacing: 2px; }
            .header .stamp-role-label { font-size: 10px; letter-spacing: 2px; color: rgba(255, 255, 255, 0.80); }
            .header-stamps { gap: 10px; }
```

- [ ] **Step 3: 更新 Print 样式中的印章标签颜色**

在 `@media print` 块中（约第1040行），将：

```css
            .stamp-role-label {
                font-size: 9px;
                color: #555 !important;
            }
```

改为：

```css
            .stamp-role-label {
                font-size: 9px;
                color: #555 !important;
            }
            .header .stamp-role-label {
                color: #555 !important;
            }
```

- [ ] **Step 4: 综合验证**

```bash
cd html && python -m http.server 8080
```

浏览器访问 `http://localhost:8080`：

**屏幕显示验证：**
1. 页面加载后，header 深色背景左侧显示 icon + 标题，右侧显示三个电子印章
2. 印章标签文字（承认/确认/担当）为白色半透明，在深色背景上清晰可见
3. 印章显示为"未签"状态（白色半透明圆形边框）
4. filters 栏不再有印章
5. 缩放至 1024px 以下 → header 内容纵向排列，标题和印章各占一行
6. 缩放至 768px/480px → 印章等比例缩小

**功能验证：**
1. 登录（担当角色），选择设备+月份，点击查询
2. 点击「盖章签名」→ 对应印章应显示签名者姓名（红色），而非"未签"
3. 退出登录 → 所有印章恢复"未签"状态
4. 导出 PDF → PDF 中印章在标题右侧正常显示

**打印验证：**
1. `Ctrl+P` 打印预览 → 印章标签颜色为 `#555`（非白色），印章边框正常
