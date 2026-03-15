@echo off
echo ======================================
echo   SongSong Performance Benchmark
echo ======================================
echo.

REM --- Configuration ---
set DELAY=5
set FILE_MB=20
set MAX_DAEMONS=4

if not "%1"=="" set FILE_MB=%1
if not "%2"=="" set MAX_DAEMONS=%2
if not "%3"=="" set DELAY=%3

echo Config: %FILE_MB% MB file, %MAX_DAEMONS% daemons, %DELAY% ms delay
echo.

REM --- Compile all sources ---
echo Compiling...
dir /s /b src\main\java\*.java > _sources.txt
javac -d bin @_sources.txt 2>&1
del _sources.txt

if errorlevel 1 (
    echo ERROR: Compilation failed!
    pause
    exit /b 1
)
echo Compilation OK.
echo.

REM --- Run benchmark ---
echo Starting benchmark (this may take 30-60 seconds)...
echo.
java -cp bin -Dsongsong.network.delay=%DELAY% edu.usth.songsong.benchmark.PerformanceBenchmark %FILE_MB% %MAX_DAEMONS%

echo.
echo Done! Open benchmark_chart.html in your browser to see the chart.
pause
