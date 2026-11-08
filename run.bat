@echo off
rem Starts the console. To run queries from a file: run.bat demo.sql
rem Code page 866 is the standard Russian console encoding: Cyrillic works both when typing and printing.
setlocal
set JAVA=java
if defined JAVA_HOME set JAVA="%JAVA_HOME%\bin\java"
chcp 866 >nul
%JAVA% -Dfile.encoding=cp866 -Dstdout.encoding=cp866 -cp out Main %*
