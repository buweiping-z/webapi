设备点检管理系统 - 性能优化实施指南
项目背景
设备点检管理系统，数据库表 inspection_records 和 inspection_results 预计增长到 10-100 万级。项目处于开发中阶段，无生产包袱，可以激进优化，不需要向后兼容。

核心问题
缺失索引 → 全表扫描

N+1 查询 → BuildFrequencyStats 对每个设备循环查库，SaveDailyRecord 逐条处理

无分页 → GetMonthlyRecords 返回整月全量数据

低效查询 → SELECT * 拉全列，内存分组，无 AsNoTracking

优化方案（按优先级）
P0：数据库索引（立即执行）
在 AppDbContext.OnModelCreating 中添加：

csharp
// 1. 唯一索引：支持批量 upsert，防止重复
entity.HasIndex(r => new { r.RecordId, r.ItemName })
    .IsUnique()
    .HasDatabaseName("uq_results_record_item");

// 2. 复合索引：月度时间范围查询
entity.HasIndex(r => new { r.DeviceModel, r.InspectionTime })
    .HasDatabaseName("idx_records_device_time");

// 3. 复合索引：签名按月查询
entity.HasIndex(s => new { s.DeviceModel, s.Year, s.Month })
    .HasDatabaseName("idx_signatures_device_period");
生成迁移：dotnet ef migrations add AddPerformanceIndexes

P0：全局 AsNoTracking
在 AppDbContext 构造函数中设置默认行为：

csharp
public AppDbContext(DbContextOptions<AppDbContext> options) : base(options)
{
    ChangeTracker.QueryTrackingBehavior = QueryTrackingBehavior.NoTracking;
}
需要更新的查询显式使用 .AsTracking()。

P0：BuildFrequencyStats 批量化重构
原问题：N 台设备 = 3N 次 SQL

解决方案：改为 3 次批量查询 + 内存分组

csharp
public async Task<FrequencyStatsResponse> BuildFrequencyStats(DateTime startDate, DateTime endDate)
{
    // 1. 一次查出所有设备型号
    var deviceModels = await _context.Devices
        .AsNoTracking()
        .Select(d => d.Model)
        .ToListAsync();
    
    if (!deviceModels.Any())
        return new FrequencyStatsResponse();

    // 2. 批量查模板（一次 SQL）
    var templates = await _context.Templates
        .AsNoTracking()
        .Where(t => deviceModels.Contains(t.DeviceModel))
        .ToDictionaryAsync(t => t.DeviceModel);

    // 3. 批量查 records（一次 SQL）
    var records = await _context.InspectionRecords
        .AsNoTracking()
        .Where(r => deviceModels.Contains(r.DeviceModel) 
                    && r.InspectionTime >= startDate 
                    && r.InspectionTime <= endDate)
        .ToListAsync();

    var recordIds = records.Select(r => r.Id).ToList();
    if (!recordIds.Any())
        return new FrequencyStatsResponse();

    // 4. 批量查 results（一次 SQL）
    var results = await _context.InspectionResults
        .AsNoTracking()
        .Where(r => recordIds.Contains(r.RecordId))
        .ToListAsync();

    // 5. 内存中按设备维度分组统计
    var stats = new Dictionary<string, DeviceFrequencyStat>();
    foreach (var device in deviceModels)
    {
        var deviceRecords = records.Where(r => r.DeviceModel == device).ToList();
        var deviceRecordIds = deviceRecords.Select(r => r.Id).ToHashSet();
        var deviceResults = results.Where(r => deviceRecordIds.Contains(r.RecordId)).ToList();
        
        // 统计逻辑...
    }
    
    return new FrequencyStatsResponse { Stats = stats.Values.ToList() };
}
P0：SaveDailyRecord 批量 Upsert
原问题：逐条查 records → 逐条查 results → 逐条 insert/update

解决方案：确保唯一索引 (record_id, item_name) 存在后，使用原生 SQL 批量 upsert

csharp
public async Task SaveDailyRecord(SaveDailyRecordRequest request)
{
    using var transaction = await _context.Database.BeginTransactionAsync();
    
    try
    {
        // 1. 确保 inspection_record 存在
        var recordId = await GetOrCreateRecord(request);
        
        // 2. 批量 upsert results
        var sql = @"
            INSERT INTO inspection_results (record_id, item_name, value, unit, normal_range, status, updated_at)
            VALUES ({0}, {1}, {2}, {3}, {4}, {5}, NOW())
            ON DUPLICATE KEY UPDATE
                value = VALUES(value),
                unit = VALUES(unit),
                normal_range = VALUES(normal_range),
                status = VALUES(status),
                updated_at = NOW()";
        
        foreach (var item in request.Items)
        {
            await _context.Database.ExecuteSqlRawAsync(sql, 
                recordId, item.ItemName, item.Value, item.Unit, item.NormalRange, item.Status);
        }
        
        await transaction.CommitAsync();
    }
    catch
    {
        await transaction.RollbackAsync();
        throw;
    }
}

private async Task<int> GetOrCreateRecord(SaveDailyRecordRequest request)
{
    var existing = await _context.InspectionRecords
        .AsTracking()
        .FirstOrDefaultAsync(r => r.DeviceModel == request.DeviceModel 
                                   && r.InspectionTime == request.InspectionTime);
    
    if (existing != null)
        return existing.Id;
    
    var record = new InspectionRecord
    {
        DeviceModel = request.DeviceModel,
        InspectionTime = request.InspectionTime,
        EmployeeId = request.EmployeeId
    };
    
    _context.InspectionRecords.Add(record);
    await _context.SaveChangesAsync();
    return record.Id;
}
P1：分页标准化
创建通用分页响应 DTO：

csharp
public class PagedResponse<T>
{
    public List<T> Items { get; set; } = new();
    public int Page { get; set; }
    public int PageSize { get; set; }
    public int TotalCount { get; set; }
    public int TotalPages => (int)Math.Ceiling(TotalCount / (double)PageSize);
}
改造 GetMonthlyRecords：

csharp
public async Task<PagedResponse<MonthlyRecordDto>> GetMonthlyRecords(
    string deviceModel, 
    DateTime startDate, 
    DateTime endDate,
    int page = 1, 
    int pageSize = 100)
{
    var query = _context.InspectionRecords
        .AsNoTracking()
        .Where(r => r.DeviceModel == deviceModel 
                    && r.InspectionTime >= startDate 
                    && r.InspectionTime <= endDate);
    
    var totalCount = await query.CountAsync();
    
    var items = await query
        .OrderByDescending(r => r.InspectionTime)
        .ThenByDescending(r => r.Id)  // 确保排序稳定
        .Skip((page - 1) * pageSize)
        .Take(pageSize)
        .Select(r => new MonthlyRecordDto
        {
            Id = r.Id,
            InspectionTime = r.InspectionTime,
            EmployeeId = r.EmployeeId,
            EmployeeName = r.Employee.Name
        })
        .ToListAsync();
    
    return new PagedResponse<MonthlyRecordDto>
    {
        Items = items,
        Page = page,
        PageSize = pageSize,
        TotalCount = totalCount
    };
}
P1：DTO 投影优化
所有只读接口改为返回 DTO，避免 SELECT *：

csharp
// 定义 DTO
public class DeviceDto 
{
    public string Model { get; set; }
    public string Name { get; set; }
}

// Controller 返回 DTO，EF 自动优化查询
public async Task<List<DeviceDto>> GetDevices()
{
    return await _context.Devices
        .AsNoTracking()
        .Select(d => new DeviceDto
        {
            Model = d.Model,
            Name = d.Name
        })
        .ToListAsync();
}
P1：GetDailyOperators 窗口函数优化
使用 MySQL 8.0+ 的 ROW_NUMBER() 窗口函数：

csharp
public async Task<List<DailyOperatorResult>> GetDailyOperators(
    string deviceModel, 
    DateTime startDate, 
    DateTime endDate)
{
    var sql = @"
        SELECT day, employee_id FROM (
            SELECT 
                DAY(inspection_time) as day,
                employee_id,
                ROW_NUMBER() OVER (PARTITION BY DAY(inspection_time) ORDER BY inspection_time DESC) as rn
            FROM inspection_records
            WHERE device_model = {0} 
                AND inspection_time >= {1} 
                AND inspection_time <= {2}
        ) t WHERE rn = 1";
    
    return await _context.Database
        .SqlQueryRaw<DailyOperatorResult>(sql, deviceModel, startDate, endDate)
        .ToListAsync();
}
P2（可选）：软删除和审计字段
添加基类和全局过滤器：

csharp
public interface IAuditable
{
    DateTime CreatedAt { get; set; }
    DateTime? UpdatedAt { get; set; }
    bool IsDeleted { get; set; }
}

public class InspectionRecord : IAuditable
{
    // ... existing properties ...
    public DateTime CreatedAt { get; set; }
    public DateTime? UpdatedAt { get; set; }
    public bool IsDeleted { get; set; }
}

// AppDbContext.OnModelCreating
modelBuilder.Entity<InspectionRecord>().HasQueryFilter(e => !e.IsDeleted);
实施顺序
顺序	任务	预计时间	依赖
1	添加数据库索引	10min	无
2	设置全局 AsNoTracking	5min	无
3	重构 BuildFrequencyStats	2h	无
4	重构 SaveDailyRecord（加唯一索引后）	3h	任务1
5	实现分页 + PagedResponse	2h	无
6	所有接口改为 DTO 返回	4h	无
7	窗口函数优化 GetDailyOperators	1h	MySQL 8.0+
8	软删除 + 审计字段（可选）	2h	无
验证方法
迁移验证：dotnet ef database update 成功执行

索引验证：在 MySQL 中执行 SHOW INDEX FROM inspection_results 确认唯一索引已创建

SQL 日志验证：在 appsettings.Development.json 开启日志，对比优化前后的 SQL 执行次数

json
{
  "Logging": {
    "LogLevel": {
      "Microsoft.EntityFrameworkCore.Database.Command": "Information"
    }
  }
}
关键接口测试：通过 Swagger 测试 GetMonthlyRecords、GetFrequencySummary、SaveDailyRecord

性能验证：使用 10 万级测试数据，确认每个接口响应时间 < 500ms

注意事项
唯一索引前提：添加 uq_results_record_item 前，确保现有数据没有重复的 (record_id, item_name)。如有重复，先清理数据。

AsTracking 场景：只有需要更新/删除的查询才用 .AsTracking()，其他查询依赖全局 NoTracking。

分页排序稳定：必须同时按 inspection_time DESC, id DESC 排序，避免分页结果重复或遗漏。

窗口函数要求：ROW_NUMBER() 需要 MySQL 8.0+，检查数据库版本：SELECT VERSION();

事务处理：SaveDailyRecord 的批量操作必须包在 BeginTransactionAsync 中，保证原子性。

预期效果
优化项	优化前	优化后
BuildFrequencyStats（100 设备）	~300 次 SQL	3 次 SQL
SaveDailyRecord（20 条 item）	~40 次 SQL + 40 次往返	1 次事务 + N 条批量 SQL
GetMonthlyRecords	全表扫描 + 无分页	索引扫描 + 分页
所有 GET 接口	SELECT * 全列	投影到 DTO 最小列
整体预期：API 响应时间降低 80-95%，数据库负载降低 90%+。

回滚方案
如果出现问题，按以下顺序回滚：

删除迁移文件，回滚数据库：dotnet ef database update LastGoodMigration

注释掉 AppDbContext 中的全局 AsNoTracking 设置

恢复原始 Controller 代码（使用版本控制回退）