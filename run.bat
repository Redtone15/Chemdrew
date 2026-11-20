@echo off
setlocal
cd /d "%~dp0"
call build.bat
if errorlevel 1 exit /b 1
if not exist build\javafx-cache mkdir build\javafx-cache
java -Djavafx.cachedir="%~dp0build\javafx-cache" --module-path "%~dp0lib" --add-modules javafx.controls -cp "%~dp0build\classes" com.molview.app.MolViewApp %*
endlocal
