@echo off
echo Limpando compilacoes antigas (pasta bin)...
if exist bin rmdir /s /q bin
mkdir bin

echo Definindo o classpath para incluir a pasta lib...
REM O ponto e virgula (;) eh o separador de classpath no Windows.
REM O asterisco (*) em lib/* inclui todos os arquivos .jar dentro da pasta lib.
set CP=.;lib/*

echo Compilando todos os arquivos Java...
REM -d bin: Especifica o diretorio de saida para os arquivos .class
REM -cp "%CP%": Especifica o classpath para a compilacao
REM -encoding UTF-8: Especifica a codificacao dos arquivos fonte
javac -encoding UTF-8 -d bin -cp "%CP%" common/*.java client/*.java server/*.java

if %errorlevel% neq 0 (
    echo.
    echo ERRO NA COMPILACAO! Verifique as mensagens acima.
    pause
    exit /b %errorlevel%
)

echo.
echo Compilacao concluida com sucesso! Arquivos .class estao em ./bin
echo.
echo -------------------------------------------------------------------
echo PARA EXECUTAR O SERVIDOR (a partir da raiz do projeto):
echo java -cp "bin;lib/*" server.Server
echo.
echo PARA EXECUTAR O CLIENTE (a partir da raiz do projeto):
echo java -cp "bin;lib/*" client.ClientGUI
echo -------------------------------------------------------------------
echo.
pause
