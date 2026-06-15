@echo off
chcp 65001 >nul
title 点检系统后端服务

echo ========================================
echo   点检系统后端服务
echo ========================================
echo.

REM 检查配置文件
if not exist appsettings.json (
    echo [错误] 找不到 appsettings.json 配置文件
    pause
    exit /b 1
)

REM 检查数据库连接
echo [1/2] 正在启动后端服务...
echo.

REM 启动服务
webapi.exe --urls=http://0.0.0.0:5039

echo.
echo [2/2] 服务已停止
pause