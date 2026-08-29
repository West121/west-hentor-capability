# Copy this file to backend.env.ps1 and fill in the real SQL Server values.
# backend.env.ps1 is excluded by .gitignore and must not be committed.

$env:SPRING_DATASOURCE_URL = "jdbc:sqlserver://SQLSERVER_HOST:1433;databaseName=sgsmineralscapability;encrypt=true;trustServerCertificate=true"
$env:SPRING_DATASOURCE_USERNAME = "SQLSERVER_USERNAME"
$env:SPRING_DATASOURCE_PASSWORD = "SQLSERVER_PASSWORD"

$env:SERVER_ADDRESS = "127.0.0.1"
$env:SERVER_PORT = "9901"
$env:APP_SERVER_ROOT_ADDRESS = "https://cnrrtcms.sgs.net/"
# 浏览器访问 HTTPS 域名时，Origin 仍会经 Nginx 转发到后端；必须把外部域名加入白名单。
# 不要使用 *，系统会发送认证头，CORS 应只信任实际部署域名。
$env:APP_CORS_ALLOWED_ORIGIN_PATTERNS = "https://cnrrtcms.sgs.net,http://cnrrtcms.sgs.net,http://localhost:*,http://127.0.0.1:*,http://10.169.1.7:*"
