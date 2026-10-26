@echo off
setlocal EnableExtensions
rem ============================================================
rem  StudyWorkplace 一键启动（隐藏后台进程，日志见 logs\，PID 见 run\）
rem  用法：双击本文件，或命令行 scripts\start.bat [build] [auto]
rem        build = 强制重新打包 jar 后再启动
rem        auto  = 跳过「按任意键确认」（供脚本/自动化调用）
rem ============================================================
cd /d "%~dp0.."
set "ROOT=%CD%"
set "JAR=%ROOT%\target\studyworkplace-1.0.0.jar"
set "OUTLOG=%ROOT%\logs\app.out.log"
set "ERRLOG=%ROOT%\logs\app.err.log"
set "PIDFILE=%ROOT%\run\app.pid"
if not exist "%ROOT%\logs" mkdir "%ROOT%\logs"
if not exist "%ROOT%\run"  mkdir "%ROOT%\run"

set "RC=0"
set "CONFIRM=1"
if /i "%~1"=="auto" set "CONFIRM="
if /i "%~2"=="auto" set "CONFIRM="
if defined WP_NOPAUSE set "CONFIRM="

rem ---- 已在运行？（按 8080 端口探测，兼容非脚本方式启动的实例）----
set "RUNPID="
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":8080 " ^| findstr LISTENING') do set "RUNPID=%%p"
if defined RUNPID (
  echo.
  echo ══════════════════════════════════════════════
  echo   StudyWorkplace 已在运行（PID %RUNPID%），无需重复启动
  goto :panel
)

rem ---- 构建（缺 jar 或显式传 build）----
if /i "%~1"=="build" set "NEEDBUILD=1"
if not exist "%JAR%" set "NEEDBUILD=1"
if defined NEEDBUILD (
  echo [构建] mvn package -DskipTests ...（首次约 1-2 分钟）
  call mvn -q package -DskipTests
  if errorlevel 1 (
    echo [失败] 构建未通过，请确认 JDK 17+ / Maven / MySQL 已就绪
    set "RC=1"
    goto :ask_exit
  )
)

rem ---- 后台启动（隐藏窗口；路径经环境变量传入 PowerShell，避免引号拼接问题）----
echo [启动] 正在后台启动 StudyWorkplace ...
set "WP_ROOT=%ROOT%"
set "WP_JAR=%JAR%"
powershell -NoProfile -Command "$p = Start-Process -FilePath 'java' -ArgumentList '-jar', $env:WP_JAR -WorkingDirectory $env:WP_ROOT -WindowStyle Hidden -RedirectStandardOutput ($env:WP_ROOT + '\logs\app.out.log') -RedirectStandardError ($env:WP_ROOT + '\logs\app.err.log') -PassThru; $p.Id | Out-File -Encoding ascii ($env:WP_ROOT + '\run\app.pid')"
if errorlevel 1 (
  echo [失败] java 进程无法拉起，请确认已安装 JDK 17+ 且 java 在 PATH 中
  set "RC=1"
  goto :ask_exit
)

rem ---- 就绪探测（最多 60 秒；用 ping 睡眠，避免 PATH 里的 coreutils timeout 抢占）----
set "WAITED=0"
:poll
powershell -NoProfile -Command "try { if ((Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8080/api/bootstrap' -TimeoutSec 2).StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 1 }"
if not errorlevel 1 goto :ready
set /a WAITED+=2
ping -n 3 127.0.0.1 >nul
if %WAITED% LSS 60 goto :poll
echo [失败] 60 秒内未就绪。请查看错误日志：
echo     %ERRLOG%
echo     %OUTLOG%
echo   （常见原因：MySQL 未启动 / 3306 不通 / 8080 被占用）
set "RC=1"
goto :ask_exit

:ready
set "PID2=未知"
if exist "%PIDFILE%" for /f "tokens=*" %%p in ('type "%PIDFILE%"') do set "PID2=%%p"
echo.
echo ══════════════════════════════════════════════
echo   StudyWorkplace 启动成功（PID %PID2%）

:panel
echo.
echo   【访问】
echo     本机:        http://127.0.0.1:8080/
for /f "tokens=2 delims=:" %%i in ('ipconfig ^| findstr /c:"IPv4"') do (
  for /f "tokens=*" %%j in ("%%i") do echo     手机/平板同 Wi-Fi:  http://%%j:8080/
)
echo     家长后台:    http://127.0.0.1:8080/#/admin （默认 PIN 8888，请尽快修改）
echo.
echo   【文件】
echo     运行日志:    %OUTLOG%
echo     错误日志:    %ERRLOG%
echo     进程号文件:  %PIDFILE%

:ask_exit
if not defined CONFIRM exit /b %RC%
echo.
echo ──────────────────────────────────────────────
echo 按任意键确认退出 ...
pause >nul
exit /b %RC%
