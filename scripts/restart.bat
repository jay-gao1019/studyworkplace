@echo off
setlocal EnableExtensions
rem ============================================================
rem  StudyWorkplace 一键重启（先停后起；只在结束时确认一次按键）
rem  用法：双击本文件，或命令行 scripts\restart.bat [build] [auto]
rem        build = 重新打包 jar 后再启动；auto = 跳过按键确认
rem ============================================================
cd /d "%~dp0.."
set "RC=0"

set "CONFIRM=1"
if /i "%~1"=="auto" set "CONFIRM="
if /i "%~2"=="auto" set "CONFIRM="
set "WP_NOPAUSE=1"

echo ════════ 重启流程：先停止旧进程，再启动新进程 ════════
echo.
call "%~dp0stop.bat" auto
if errorlevel 1 (
  echo [失败] 旧进程未能干净停止，已中止重启（可人工处理后重试）
  set "RC=1"
  goto :end
)
echo.
call "%~dp0start.bat" %*
if errorlevel 1 (
  echo [失败] 新进程未能启动，请查看上方提示与 logs\ 目录日志
  set "RC=1"
  goto :end
)

:end
if not defined CONFIRM exit /b %RC%
echo.
echo ──────────────────────────────────────────────
echo 按任意键确认退出 ...
pause >nul
exit /b %RC%
