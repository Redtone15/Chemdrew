@echo off
setlocal
cd /d "%~dp0"
call build.bat
if errorlevel 1 exit /b 1
if not exist build\javafx-cache mkdir build\javafx-cache
dir /s /b src\test\java\*.java > build\test-sources.txt 2>nul
if not exist build\test-classes mkdir build\test-classes
javac -encoding UTF-8 -d build\test-classes --module-path "%~dp0lib" --add-modules javafx.controls -cp "%~dp0build\classes" @build\test-sources.txt
if errorlevel 1 exit /b 1
if not exist build\test-output mkdir build\test-output
if exist build\test-output\dialog-export.pdf del /q build\test-output\dialog-export.pdf

echo Running canvas gesture and hex-grid tests...
java -Djavafx.cachedir="%~dp0build\javafx-cache" -Dprism.order=sw --module-path "%~dp0lib" --add-modules javafx.controls -cp "%~dp0build\classes;%~dp0build\test-classes" com.molview.app.CanvasInteractionTest
if errorlevel 1 exit /b 1

echo Running dialog and preview tests...
java -Djavafx.cachedir="%~dp0build\javafx-cache" -Dprism.order=sw --module-path "%~dp0lib" --add-modules javafx.controls -cp "%~dp0build\classes;%~dp0build\test-classes" com.molview.app.UiSmokeTest
if errorlevel 1 exit /b 1

echo Running inspector render test...
java -Djavafx.cachedir="%~dp0build\javafx-cache" -Dprism.order=sw --module-path "%~dp0lib" --add-modules javafx.controls -cp "%~dp0build\classes;%~dp0build\test-classes" com.molview.app.InspectorSmokeTest
if errorlevel 1 exit /b 1

echo All UI smoke tests completed.
endlocal
