@echo off
title SavdoPRO MXIK proxy - YOPMANG
cd /d "%~dp0"
echo SavdoPRO MXIK proxy ishga tushirilmoqda...
node mxik-local-proxy.cjs
echo.
echo Proxy toxtadi. Yopish uchun istalgan tugmani bosing.
pause >nul
