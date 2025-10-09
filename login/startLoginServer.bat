@echo off
title L2Journey - LoginServer
color 02
:start
echo Iniciando Loginserver.
echo ------------------------------------------------------------------------------

java -server -Dfile.encoding=UTF-8 -Dorg.slf4j.simpleLogger.log.com.zaxxer.hikari=warn -XX:+UseZGC -Xms128m -Xmx256m -Dlogback.configurationFile=./configuration/logback.xml -cp ./../libs/*;Loginserver.jar com.l2journey.loginserver.LoginServer

if ERRORLEVEL 2 goto restart
if ERRORLEVEL 1 goto error
goto end

:restart
echo.
echo Admin Restarted Login Server.
echo.
goto start

:error
echo.
echo Login Server parou inesperadamente!
echo.

:end
echo.
echo Login Server Terminou.
echo.
pause
