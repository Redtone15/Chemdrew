@echo off
setlocal
cd /d "%~dp0"
if not exist build\classes mkdir build\classes
if not exist build\test-classes mkdir build\test-classes
if not exist build\sources.txt (
  dir /s /b src\main\java\*.java > build\sources.txt
) else (
  dir /s /b src\main\java\*.java > build\sources.txt
)
echo Compiling MolView Studio...
javac -encoding UTF-8 -d build\classes --module-path "%~dp0lib" --add-modules javafx.controls @build\sources.txt
if errorlevel 1 (
  echo Build failed.
  exit /b 1
)
xcopy /E /I /Y /Q src\main\resources build\classes >nul
jar --create --file build\MolViewStudio.jar --main-class com.molview.app.MolViewApp -C build\classes .
echo Build complete: build\MolViewStudio.jar
endlocal
