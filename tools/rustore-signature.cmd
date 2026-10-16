@echo off
rem ---------------------------------------------------------------------------
rem RuStore app signature helper (Windows, cmd.exe).
rem
rem Produces the two files asked by the "App signing key" window in RuStore
rem Console:
rem   rustore-out\pepk_out.zip    - app signing key, encrypted by PEPK
rem   rustore-out\uploadcert.pem  - upload key certificate (the key that
rem                                 signs the AAB, same as CI secrets)
rem
rem Usage:
rem   tools\rustore-signature.cmd --encryption-key=HEX_FROM_RUSTORE_WINDOW
rem
rem Optional:
rem   --keystore=app-signing.keystore       app signing key (created if absent)
rem   --alias=sign
rem   --upload-keystore=upload-keystore.jks upload key (the one used by CI)
rem   --upload-alias=upload
rem   --out=rustore-out
rem
rem Run it from cmd.exe or PowerShell, NOT from Git Bash: PEPK reads passwords
rem through the real console and fails under MinTTY.
rem Console output is ASCII on purpose: Cyrillic in .cmd files breaks on most
rem Windows code pages.
rem ---------------------------------------------------------------------------
setlocal EnableExtensions
set "ENCRYPTION_KEY="
set "KEYSTORE=app-signing.keystore"
set "ALIAS=sign"
set "UPLOAD_KEYSTORE=upload-keystore.jks"
set "UPLOAD_ALIAS=upload"
set "OUTDIR=rustore-out"

:parse
if "%~1"=="" goto parsed
for /f "tokens=1,* delims==" %%A in ("%~1") do (
    if /i "%%A"=="--encryption-key"  set "ENCRYPTION_KEY=%%B"
    if /i "%%A"=="--keystore"        set "KEYSTORE=%%B"
    if /i "%%A"=="--alias"           set "ALIAS=%%B"
    if /i "%%A"=="--upload-keystore" set "UPLOAD_KEYSTORE=%%B"
    if /i "%%A"=="--upload-alias"    set "UPLOAD_ALIAS=%%B"
    if /i "%%A"=="--out"             set "OUTDIR=%%B"
)
shift
goto parse
:parsed

rem --- 1. JDK (JAVA_HOME -> Android Studio JBR -> PATH) ----------------------
set "JAVA="
set "JAVACMD="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA=%JAVA_HOME%\bin\java.exe"
if not defined JAVA if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe" set "JAVA=%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe"
if not defined JAVA if exist "%LOCALAPPDATA%\Programs\Android Studio\jbr\bin\java.exe" set "JAVA=%LOCALAPPDATA%\Programs\Android Studio\jbr\bin\java.exe"
if not defined JAVA for /f "delims=" %%I in ('where java 2^>nul') do if not defined JAVA set "JAVA=%%I"
if not defined JAVA (
    echo ERROR: java.exe not found. Install JDK 11+ or Android Studio, or set JAVA_HOME.
    exit /b 1
)
for %%I in ("%JAVA%") do set "KEYTOOL=%%~dpIkeytool.exe"
if not exist "%KEYTOOL%" (
    echo ERROR: keytool.exe not found next to %JAVA% ^(a full JDK is required^).
    exit /b 1
)
echo [1/5] Java: %JAVA%
echo       keytool: %KEYTOOL%

rem --- 2. Arguments ---------------------------------------------------------
if not defined ENCRYPTION_KEY (
    echo ERROR: --encryption-key is required. Copy the command from the
    echo        "App signing key" window in RuStore Console and reuse its key.
    exit /b 1
)
set "PEPK="
if exist "%OUTDIR%\pepk.jar" set "PEPK=%OUTDIR%\pepk.jar"
if not defined PEPK if exist "pepk.jar" set "PEPK=pepk.jar"
if not defined PEPK if exist "%USERPROFILE%\pepk.jar" set "PEPK=%USERPROFILE%\pepk.jar"
if not defined PEPK (
    echo ERROR: pepk.jar not found. Download it with the button in the RuStore
    echo        signature window and put it into %OUTDIR%\ or next to this file.
    exit /b 1
)
echo [2/5] pepk.jar:      %PEPK%
echo       app signing:   %KEYSTORE% ^(alias %ALIAS%^)
echo       upload key:    %UPLOAD_KEYSTORE% ^(alias %UPLOAD_ALIAS%^)

rem --- 3. App signing key ---------------------------------------------------
echo [3/5] App signing key
if not exist "%OUTDIR%" mkdir "%OUTDIR%"
if exist "%KEYSTORE%" (
    echo       already exists, left untouched: %KEYSTORE%
) else (
    echo       creating a NEW app signing key - RuStore will re-sign APKs with it
    "%KEYTOOL%" -genkeypair -v -keystore "%KEYSTORE%" -alias "%ALIAS%" -keyalg RSA -keysize 4096 -validity 10000 -storetype PKCS12 -dname "CN=Converter Plus, O=Converter Plus, C=RU"
    if errorlevel 1 (
        echo ERROR: keytool -genkeypair failed.
        exit /b 1
    )
    echo.
    echo       *** BACK UP %KEYSTORE% AND ITS PASSWORD IN TWO PLACES. ***
    echo       *** Losing it means you can never update the app again. ***
    echo.
)

rem --- 3b. Alias check ---------------------------------------------------------
rem PEPK fails with "No key with alias" only AFTER the password prompt, so check
rem the alias in advance: read the key entries and look for the needed alias.
echo [3b/5] Checking alias "%ALIAS%" in %KEYSTORE%
set "ALIASES_FILE=%TEMP%\rustore-aliases.txt"
"%KEYTOOL%" -list -keystore "%KEYSTORE%" > "%ALIASES_FILE%" 2>&1
if errorlevel 1 (
    echo       WARNING: could not read the alias list, continuing anyway
) else (
    findstr /R /C:"^%ALIAS%," "%ALIASES_FILE%" >nul 2>&1
    if errorlevel 1 (
        echo ERROR: the key has no entry with alias "%ALIAS%". Entries present:
        type "%ALIASES_FILE%"
        echo        rerun with  --alias=^<name from the list above^>
        exit /b 1
    )
    echo       alias found
)

rem --- 4. PEPK -> encrypted ZIP --------------------------------------------
echo [4/5] Encrypting the app signing key with PEPK
echo       enter the keystore password and the key password when asked
if exist "%OUTDIR%\pepk_out.zip" del /q "%OUTDIR%\pepk_out.zip"
"%JAVA%" -jar "%PEPK%" --keystore="%KEYSTORE%" --alias="%ALIAS%" --output="%OUTDIR%\pepk_out.zip" --encryptionkey="%ENCRYPTION_KEY%" --include-cert
if not exist "%OUTDIR%\pepk_out.zip" (
    echo ERROR: PEPK did not produce %OUTDIR%\pepk_out.zip
    echo        if the tool said "Error: null", run this script from cmd.exe
    echo        or PowerShell instead of Git Bash.
    exit /b 1
)
for %%I in ("%OUTDIR%\pepk_out.zip") do set "ZIP_BYTES=%%~zI"
echo       done: %OUTDIR%\pepk_out.zip ^(%ZIP_BYTES% bytes, console limit is 100 KB^)
if defined ZIP_BYTES if %ZIP_BYTES% GTR 102400 echo       WARNING: archive is bigger than 100 KB.

rem --- 5. Upload key certificate -------------------------------------------
echo [5/5] Upload key certificate
if not exist "%UPLOAD_KEYSTORE%" (
    echo ERROR: upload key not found: %UPLOAD_KEYSTORE%
    echo        this is the keystore that signs the AAB ^(CI secrets^).
    echo        Create it once with:
    echo          keytool -genkeypair -v -keystore %UPLOAD_KEYSTORE% -alias %UPLOAD_ALIAS% -keyalg RSA -keysize 4096 -validity 10000
    exit /b 1
)
if exist "%OUTDIR%\uploadcert.pem" del /q "%OUTDIR%\uploadcert.pem"
"%KEYTOOL%" -exportcert -alias "%UPLOAD_ALIAS%" -keystore "%UPLOAD_KEYSTORE%" -rfc -file "%OUTDIR%\uploadcert.pem"
if not exist "%OUTDIR%\uploadcert.pem" (
    echo ERROR: failed to export the upload key certificate.
    exit /b 1
)
echo       done: %OUTDIR%\uploadcert.pem
echo.
echo --------------------------------------------------------------------------
echo Now, in the "App signing key" window of RuStore Console:
echo   1. add the ZIP archive      ^<- %OUTDIR%\pepk_out.zip
echo   2. add the PEM certificate  ^<- %OUTDIR%\uploadcert.pem
echo   3. click "Send signature" and wait for the "signature uploaded" state.
echo   4. only then upload the AAB, signed by %UPLOAD_KEYSTORE%.
echo.
echo Keep %KEYSTORE% and its password in two places; plain %OUTDIR% is
echo git-ignored, never commit it.
echo --------------------------------------------------------------------------
endlocal
