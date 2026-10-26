@echo off
setlocal EnableExtensions
rem ============================================================
rem  StudyWorkplace 一键停止
rem  优先按 8080 端口反查 PID，其次按 run\app.pid 兜底；
rem  结束前用 PowerShell 校验目标确为 java/javaw 进程，防止误杀。
rem  用法：双击本文件，或命令行 scripts\stop.bat [auto]
rem        auto = 跳过「按任意键确认」（供脚本/自动化调用）
rem ============================================================
cd /d "%~dp0.."
set "ROOT=%CD%"
set "PIDFILE=%ROOT%\run\app.pid"
set "RC=0"

set "CONFIRM=1"
if /i "%~1"=="auto" set "CONFIRM="
if defined WP_NOPAUSE set "CONFIRM="

set "PID="
if exist "%PIDFILE%" for /f "tokens=*" %%p in ('type "%PIDFILE%"') do set "PID=%%p"

set "PORTPID="
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":8080 " ^| findstr LISTENING') do set "PORTPID=%%p"

if defined PORTPID (
  set "TARGET=%PORTPID%"
  set "FROMPORT=1"
) else (
  set "TARGET=%PID%"
  set "FROMPORT="
)

if not defined TARGET (
  echo.
  echo ══════════════════════════════════════════════
  echo   StudyWorkplace 未在运行，无需停止
  goto :ask_exit
)

rem ---- 校验目标进程为 java/javaw，避免端口/PID 记录过期导致误杀 ----
set "WP_PID=%TARGET%"
powershell -NoProfile -Command "$c = Get-Process -Id $env:WP_PID -ErrorAction SilentlyContinue; if ($c -and $c.ProcessName -match '^(java|javaw)$') { exit 0 } else { exit 1 }"
if not errorlevel 1 goto :kill

if defined FROMPORT (
  echo [失败] 端口 8080 被非 java 程序占用（PID %TARGET%），为避免误杀不做处理
  echo   请人工确认后自行处理
  set "RC=1"
  goto :ask_exit
)
echo [提示] pid 文件记录的进程 %TARGET% 已退出或不是 java，仅清理过期记录
del /q "%PIDFILE%" 2>nul
goto :ask_exit

:kill
echo [停止] 结束 StudyWorkplace 进程树（PID %TARGET%）...
taskkill /PID %TARGET% /T /F >nul 2>&1
ping -n 3 127.0.0.1 >nul
set "STILL="
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":8080 " ^| findstr LISTENING') do set "STILL=%%p"
if defined STILL (
  echo [失败] 端口 8080 仍被 PID %STILL% 占用，请打开任务管理器结束 java 进程
  set "RC=1"
  goto :ask_exit
)
del /q "%PIDFILE%" 2>nul
echo.
echo ══════════════════════════════════════════════
echo   StudyWorkplace 已停止（PID %TARGET%），端口 8080 已释放
echo.
echo   【文件】
echo     运行日志留存: %ROOT%\logs\app.out.log
echo     错误日志留存: %ROOT%\logs\app.err.log

:ask_exit
if not defined CONFIRM exit /b %RC%
echo.
echo ──────────────────────────────────────────────
echo 按任意键确认退出 ...
pause >nul
exit /b %RC%
