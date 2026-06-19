# Bug 记录 — 图片点检功能

> 本文档记录图片点检功能开发与测试过程中发现的所有 bug。
> 进入项目后先阅读本文档，避免重复踩坑。

---

## Bug 1: SKBitmap.Decode 损坏图片导致服务崩溃

| 属性 | 值 |
|------|-----|
| **日期** | 2026-06-19 |
| **Commit** | `016cd8c` |
| **严重度** | P0 — 服务崩溃 |

**现象:** 上传魔法字节有效但内容损坏的图片（如 JPEG header + 垃圾数据）时，服务进程崩溃，HTTP 000。

**根因:** `PhotosController.cs` 中 `SKBitmap.Decode(ms)` 对损坏数据抛出未处理异常。SKCodec 有 try/catch 但 SKBitmap 没有。

**修复:** `SKBitmap.Decode` 包裹 try/catch，异常时返回 400 "无法解码图片"。

**教训:** 所有第三方库调用（尤其是图像/文件处理）都需要 try/catch，不能假设输入总是合法的。

---

## Bug 2: status 列 Data Truncated

| 属性 | 值 |
|------|-----|
| **日期** | 2026-06-19 |
| **Commit** | `73b396a` |
| **严重度** | P0 — 写入失败 |

**现象:** Android 提交点检时报 `Data truncated for column 'status' at row 1`。

**根因:** MySQL `inspection_records.status` 列为 `VARCHAR(10)`，而新状态值 `"pending_photo"` = 13 字符，写入被截断。

**修复:**
- 状态值缩短: `"pending_photo"` → `"pending"`
- 启动时 ALTER TABLE MODIFY status VARCHAR(20)
- AppDbContext Fluent API 加 HasMaxLength(20)

**教训:** 新增枚举/状态值时，先确认数据库列长度是否足够。不要假设 VARCHAR 默认够用。

---

## Bug 3: 两阶段提交流程 — 提交时不应要求已有照片

| 属性 | 值 |
|------|-----|
| **日期** | 2026-06-19 |
| **Commit** | `b909d16` |
| **严重度** | P1 — 用户体验阻塞 |

**现象:** Android 提交时校验 `requirePhoto && abnormal && photoLocalPath == null` → 阻止提交，但设计文档要求「先保存后拍照」的两阶段提交。

**根因:** 提交前验证把拍照作为前置条件（Phase 1），违背了 Phase 1 保存文字、Phase 2 上传照片的设计。

**修复:** 移除提交前的拍照强制校验，改为：
1. Phase 1: 提交文字结果 → `records/save` → 获取 `pendingPhotoItems`
2. Phase 2: 顶部 banner 显示缺照片项，逐项拍照→即时上传

**教训:** 多人协作时前后端的设计文档必须严格对齐。验证逻辑应放在正确的阶段。

---

## Bug 4: Android 照片上传路径错误 — content URI ≠ 文件路径

| 属性 | 值 |
|------|-----|
| **日期** | 2026-06-19 |
| **Commit** | `4a5191a` |
| **严重度** | P0 — 照片上传静默失败 |

**现象:** 拍照后上传显示 0/1，照片从未到达服务端。

**根因:** `FileProvider.getUriForFile()` 返回 content:// URI，`uri.path` 返回 `/cache/inspection_photos/photo_xxx.jpg`（content URI 路径），不是真实文件系统路径。`uploadPhoto()` 中 `File(filePath).exists()` 返回 false。

**修复:** 拍照前用 `photoFile.absolutePath` 保存真实路径（如 `/data/data/.../cache/inspection_photos/photo_xxx.jpg`），上传时使用此路径。

**教训:** Android `Uri.path` ≠ 文件系统路径。FileProvider 的 content URI 不能当文件路径用。拍照前就要保存好 `File.absolutePath`。

---

## Bug 5: 数值点检 isNormal 始终为 false

| 属性 | 值 |
|------|-----|
| **日期** | 2026-06-19 |
| **Commit** | `35c52d2` |
| **严重度** | P1 — 数据显示错误 |

**现象:** 前端表格中所有数值点检（温度 25.5、压力 0.8 等）都显示为红色异常。

**根因:** `SaveRecordItem` DTO 没有 `IsNormal` 字段。服务端 `records/save` 用 `saveValue == "正常"` 推导 `isNormal`，数值 `"25.5" != "正常"` → 永远 false。

**修复:**
- C# `SaveRecordItem` 加 `IsNormal` 属性
- SQL: `saveValue == "正常"` → `item.IsNormal`
- Android `SaveRecordItem` 加 `isNormal` 字段
- ViewModel 传 `isNormal = inRange`

**教训:** DTO 设计时不要依赖字符串匹配推导布尔值。显式字段比隐式推导可靠 100 倍。

---

## Bug 6: 周检/月检 frequency 硬编码为"日"

| 属性 | 值 |
|------|-----|
| **日期** | 2026-06-19 |
| **Commit** | `1001917` |
| **严重度** | P1 — 频率状态错误 |

**现象:** 周检和月检完成后，`frequencies-available` 仍显示 available=true（未置灰）。

**根因:**
1. `records/save` 硬编码 `var frequency = "日"`，`SaveDailyRecordRequest` 没有 `Frequency` 字段
2. `periodKey` 全部使用 `yyyy-MM-dd` 格式，未调用 `GeneratePeriodKey(frequency, date)`
3. 周检被存为 `frequency="日", periodKey="2026-06-19"`，`IsPeriodAllNormal(device, "周", "2026-W25")` 找不到记录

**修复:**
- C#/Android `SaveDailyRecordRequest` 加 `Frequency` 字段
- `records/save` 用 `request.Frequency` 替代硬编码
- 所有 `periodKey` 生成改用 `GeneratePeriodKey(frequency, date)`

**教训:** 任何涉及「频率」的功能都要确认三处一致：模板 frequency、record frequency、periodKey 格式。日/周/月三者的 periodKey 格式不同。

---

## Bug 7: 正常点检格也显示照片标记 📷

| 属性 | 值 |
|------|-----|
| **日期** | 2026-06-19 |
| **Commit** | `0d100c0` |
| **严重度** | P2 — 视觉干扰 |

**现象:** 正常（○）的格子也显示 📷 照片标记。

**根因:** 前端渲染时只要有照片就显示标记，未区分正常/异常。

**修复:** 加条件 `!record.isNormal`，仅异常格显示照片标记。

**教训:** 照片是故障追溯用的——正常时不需要看照片，不要增加视觉噪音。

---

## Bug 8: 填表时已拍照 → 提交后重复要求拍照

| 属性 | 值 |
|------|-----|
| **日期** | 2026-06-19 |
| **Commit** | `df918d5` |
| **严重度** | P1 — 用户体验差 |

**现象:** 填表时选了"异常"并拍照，提交后 banner 仍显示"📷 拍照"按钮，要再拍一次。

**根因:** 提交后收到 `pendingPhotoItems`，但未检查哪些项已有本地照片（`photoLocalPath`），全部要求重新拍照。

**修复:** 收到 `pendingPhotoItems` 后遍历 missingItems，已设置 `photoLocalPath` 的自动压缩上传（`autoUploadExistingPhoto`），不弹拍照界面。

**教训:** UI 状态与数据状态需要双向同步。本地已有数据时优先使用，不要强迫用户重复操作。

---

## Bug 9: 重检时旧照片覆盖新上传

| 属性 | 值 |
|------|-----|
| **日期** | 2026-06-19 |
| **Commit** | `d42a22d` |
| **严重度** | P0 — 数据丢失（新照片未上传） |

**现象:** 第一次异常→拍照→上传正常。第二次重检同一项异常→拍新照片→提交，但新照片未上传到服务端。

**根因:** `records/save` 计算 `pendingPhotoItems` 时查询 DB 已有照片。重检时旧照片还在 DB 中 → `missingItems` 为空 → 标记为 `submitted` → Android 端不触发新上传。

**修复:** 重检时先删除异常+requirePhoto 项的旧照片（DB 记录 + 磁盘文件），再标记为 pending，强制重新上传。

**教训:** 任何涉及「覆盖」「重检」「重新提交」的逻辑，都要先清理旧数据再检查。缓存/已有数据是最常见的漏网之鱼。

---

## 总结

| Bug | 严重度 | 类别 | 关键词 |
|-----|--------|------|--------|
| 1 | P0 | 异常处理 | SkiaSharp, try/catch, 崩溃 |
| 2 | P0 | 数据库 | VARCHAR 长度, Data truncated |
| 3 | P1 | 架构 | 两阶段提交, 校验时机 |
| 4 | P0 | Android | URI vs 文件路径, FileProvider |
| 5 | P1 | DTO | 布尔推导, 字符串匹配 |
| 6 | P1 | 频率 | 硬编码, periodKey 格式 |
| 7 | P2 | 前端 | 条件渲染, 视觉噪音 |
| 8 | P1 | UX | 本地状态, 重复操作 |
| 9 | P0 | 数据完整性 | 重检覆盖, 旧数据清理 |

**高频根因模式:**
1. **硬编码** — 频率硬编码、periodKey 硬编码、DTO 缺字段
2. **路径混淆** — Android URI ≠ 文件路径
3. **旧数据未清理** — 重检/重新提交时残留数据导致逻辑跳过
4. **字符串推导** — 用字符串匹配代替显式字段传递布尔值
5. **假设输入合法** — 没给第三方库调用加 try/catch
