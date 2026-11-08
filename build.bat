@echo off
rem Compiles all sources from src to out for Java 8. Requires a JDK (javac on PATH or JAVA_HOME set).
setlocal
set JAVAC=javac
if defined JAVA_HOME set JAVAC="%JAVA_HOME%\bin\javac"
if exist out rmdir /s /q out
mkdir out
%JAVAC% -encoding UTF-8 -source 8 -target 8 -Xlint:-options -d out src\*.java || exit /b 1
echo Build OK. Run: run.bat
