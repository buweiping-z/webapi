using Microsoft.EntityFrameworkCore;
using Pomelo.EntityFrameworkCore.MySql;
using webapi.Data;

var builder = WebApplication.CreateBuilder(args);

// 固定监听地址（生产环境无 launchSettings.json 时默认 5000，这里统一为 5039）
builder.WebHost.UseUrls("http://0.0.0.0:5039");

// 1. 配置 CORS 策略 (放在 AddDbContext 之前或之后都可以)
builder.Services.AddCors(options =>
{
    options.AddPolicy("AllowAll",
        policy =>
        {
            policy.AllowAnyOrigin()
                  .AllowAnyMethod()
                  .AllowAnyHeader();
        });
});

// 2. 配置数据库上下文
builder.Services.AddDbContext<AppDbContext>(options =>
    options.UseMySql(builder.Configuration.GetConnectionString("DefaultConnection"),
    ServerVersion.AutoDetect(builder.Configuration.GetConnectionString("DefaultConnection"))));

// 3. 添加控制器 + Swagger 支持
builder.Services.AddControllers();
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();

var app = builder.Build();

// 4. 启动时自动应用数据库迁移（其他电脑无需手动 dotnet ef database update）
using (var scope = app.Services.CreateScope())
{
    var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
    db.Database.Migrate();
}

// 5. 使用 CORS 中间件
app.UseCors("AllowAll");

// 6. 静态文件服务 — 从 html/ 目录提供前端页面
app.UseDefaultFiles(new DefaultFilesOptions
{
    DefaultFileNames = { "index.html" }
});
app.UseStaticFiles(new StaticFileOptions
{
    FileProvider = new Microsoft.Extensions.FileProviders.PhysicalFileProvider(
        Path.Combine(app.Environment.ContentRootPath, "html"))
});

// 7. 配置管道
if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseHttpsRedirection();
app.UseAuthorization();
app.MapControllers();

app.Run();
