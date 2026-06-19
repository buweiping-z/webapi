using Microsoft.EntityFrameworkCore;
using webapi.Data;

namespace webapi.Services
{
    public class PhotoCleanupService : BackgroundService
    {
        private readonly IServiceScopeFactory _scopeFactory;
        private readonly IWebHostEnvironment _env;
        private readonly ILogger<PhotoCleanupService> _logger;

        public PhotoCleanupService(
            IServiceScopeFactory scopeFactory,
            IWebHostEnvironment env,
            ILogger<PhotoCleanupService> logger)
        {
            _scopeFactory = scopeFactory;
            _env = env;
            _logger = logger;
        }

        protected override async Task ExecuteAsync(CancellationToken ct)
        {
            while (!ct.IsCancellationRequested)
            {
                var now = DateTime.Now;
                // 每月 1 日凌晨 2:00 执行
                var nextRun = new DateTime(now.Year, now.Month, 1).AddMonths(1).AddHours(2);
                var delay = nextRun - now;

                _logger.LogInformation("PhotoCleanupService: 下次清理时间 {NextRun}", nextRun);

                try
                {
                    await Task.Delay(delay, ct);
                }
                catch (OperationCanceledException)
                {
                    break;
                }

                try
                {
                    using var scope = _scopeFactory.CreateScope();
                    var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
                    await CleanupPhotosAsync(db, _env.WebRootPath, olderThanMonths: 6);
                    _logger.LogInformation("PhotoCleanupService: 月度清理完成");
                }
                catch (Exception ex)
                {
                    _logger.LogError(ex, "PhotoCleanupService: 清理异常");
                }
            }
        }

        /// <summary>
        /// 清理指定月数之前的照片（DB 记录 + 文件），可被 Controller 手动调用
        /// </summary>
        public static async Task CleanupPhotosAsync(
            AppDbContext context, string webRootPath, int olderThanMonths)
        {
            var cutoffDate = DateTime.Now.AddMonths(-olderThanMonths);
            var photosRoot = Path.Combine(webRootPath, "photos");

            if (!Directory.Exists(photosRoot)) return;

            // Step 1: 删除数据库中的旧记录
            var deletedCount = await context.InspectionPhotos
                .Where(p => p.CreatedAt < cutoffDate)
                .ExecuteDeleteAsync();

            // Step 2: 清理文件系统 — 遍历所有 recordId 目录
            foreach (var yearDir in Directory.GetDirectories(photosRoot))
            {
                foreach (var monthDir in Directory.GetDirectories(yearDir))
                {
                    foreach (var recordDir in Directory.GetDirectories(monthDir))
                    {
                        var recordIdStr = Path.GetFileName(recordDir);
                        if (!int.TryParse(recordIdStr, out int recordId)) continue;

                        // 文件锁防竞态：尝试打开目录中任意文件确认没有并发写入
                        bool hasActiveUpload = false;
                        try
                        {
                            var files = Directory.GetFiles(recordDir);
                            foreach (var f in files)
                            {
                                using var fs = new FileStream(f, FileMode.Open, FileAccess.Read, FileShare.None);
                                break; // 能打开就说明没被占用
                            }
                        }
                        catch (IOException)
                        {
                            hasActiveUpload = true;
                        }

                        if (hasActiveUpload) continue;

                        // 检查此 record 是否还有任何照片在 DB 中
                        var hasPhotos = await context.InspectionPhotos
                            .AnyAsync(p => p.RecordId == recordId);

                        if (!hasPhotos)
                        {
                            try
                            {
                                Directory.Delete(recordDir, recursive: true);
                            }
                            catch (IOException)
                            {
                                // 文件被占用，跳过
                            }
                        }
                    }

                    // 清理空的月份目录
                    if (!Directory.EnumerateFileSystemEntries(monthDir).Any())
                    {
                        try { Directory.Delete(monthDir); }
                        catch (IOException) { }
                    }
                }

                // 清理空的年份目录
                if (!Directory.EnumerateFileSystemEntries(yearDir).Any())
                {
                    try { Directory.Delete(yearDir); }
                    catch (IOException) { }
                }
            }
        }
    }
}
