@echo off
echo ========================================
echo   Smart Library Agent 一键启动
echo ========================================
echo.

if "%DASHSCOPE_API_KEY%"=="" (
    echo [警告] DASHSCOPE_API_KEY 未设置！
    set /p DASHSCOPE_API_KEY="请输入百炼 API Key: "
)
if "%SILICONFLOW_API_KEY%"=="" (
    echo [警告] SILICONFLOW_API_KEY 未设置！
    set /p SILICONFLOW_API_KEY="请输入硅基流动 API Key: "
)

echo.
echo 正在启动服务器...
echo 启动后浏览器打开 http://localhost:8080 即可使用
echo.

start "" http://localhost:8080
mvn spring-boot:run -Dspring-boot.run.profiles=dev
