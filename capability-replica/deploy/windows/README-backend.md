# Capability Backend for Windows Server 2026

## Package layout

```text
D:\apps\capability\
  backend\capability-replica.jar
  config\application-prod.yml
  config\backend.env.example.ps1
  scripts\start-backend.ps1
  scripts\stop-backend.ps1
  scripts\verify-backend.ps1
```

## Requirements

- Windows Server 2026 x64
- Java 17 x64 on `PATH`
- Network access to the SQL Server instance on TCP 1433
- Imported `sgsmineralscapability` database schema and data

## Configure and start

```powershell
Copy-Item D:\apps\capability\config\backend.env.example.ps1 D:\apps\capability\config\backend.env.ps1
notepad D:\apps\capability\config\backend.env.ps1
Set-ExecutionPolicy -Scope Process Bypass
& D:\apps\capability\scripts\start-backend.ps1
& D:\apps\capability\scripts\verify-backend.ps1
```

Set the JDBC URL, user name and password in `backend.env.ps1`. Do not commit this file. The application binds to `127.0.0.1:9901` by default and has a JVM maximum heap of 3584 MB.

Stop it with:

```powershell
& D:\apps\capability\scripts\stop-backend.ps1
```

Logs are written to `D:\apps\capability\logs`.
