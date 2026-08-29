# Windows Server 2026 deployment

This package serves the React frontend and proxies the backend through the same Nginx origin. The browser therefore calls `/api/...` on the current site and does not require a separate HTTP API origin.

## 1. Prerequisites

- Windows Server 2026 x64
- Java 17 x64 (`java -version`)
- Nginx for Windows x64
- Network access from the server to SQL Server TCP 1433
- Inbound TCP 80, and TCP 443 when HTTPS is configured

Nginx for Windows runs as a console application. For automatic startup, wrap Nginx and the backend with NSSM or WinSW after manual verification.

## 2. Directory layout

Extract the delivery package so these paths exist:

```text
D:\apps\capability\
  backend\capability-replica.jar
  config\application-prod.yml
  config\backend.env.example.ps1
  frontend\dist\index.html
  scripts\start-backend.ps1
  scripts\stop-backend.ps1
  scripts\verify-deployment.ps1

D:\apps\nginx\
  nginx.exe
  conf\nginx.conf
```

## 3. Configure SQL Server

Copy:

```powershell
Copy-Item D:\apps\capability\config\backend.env.example.ps1 D:\apps\capability\config\backend.env.ps1
notepad D:\apps\capability\config\backend.env.ps1
```

Fill in the JDBC URL, username and password. The real `backend.env.ps1` is a secret and must not be added to Git or sent with the source package.

The database schema and production data must already exist. The application uses `ddl-auto: none` and will not create or alter production tables.

## 4. Install and configure Nginx

1. Download and unzip Nginx to `D:\apps\nginx`.
2. Replace `D:\apps\nginx\conf\nginx.conf` with the provided `nginx.conf`.
3. Nginx paths must use forward slashes, for example `D:/apps/capability/frontend/dist`.
4. Validate and start it from an Administrator command prompt:

```bat
cd /d D:\apps\nginx
nginx.exe -t
start nginx.exe
```

After changing configuration:

```bat
cd /d D:\apps\nginx
nginx.exe -t
nginx.exe -s reload
```

Graceful stop:

```bat
cd /d D:\apps\nginx
nginx.exe -s quit
```

## 5. Start the backend

Run PowerShell as Administrator:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
& D:\apps\capability\scripts\start-backend.ps1
```

The JVM is limited to `-Xmx3584m`, below 4 GB. Spring Boot listens only on `127.0.0.1:9901`; users access it through Nginx.

To stop it:

```powershell
& D:\apps\capability\scripts\stop-backend.ps1
```

## 6. Verify

```powershell
& D:\apps\capability\scripts\verify-deployment.ps1 -PublicBaseUrl http://127.0.0.1
```

Then open `http://SERVER_IP/login`. The default replicated login is `admin`; use the password provided separately for the target database.

## 7. HTTPS and domain name

When a domain is ready, set `server_name` in `nginx.conf`, add an HTTPS `server` block with the certificate, and set `APP_SERVER_ROOT_ADDRESS` in `backend.env.ps1` to the public HTTPS origin ending in `/`.

Because this frontend package was built with `VITE_API_BASE_URL=/`, API requests remain same-origin after switching from HTTP to HTTPS. No frontend rebuild is needed for a domain-only change.

## 8. Common checks

```powershell
Get-Process java, nginx -ErrorAction SilentlyContinue
Test-NetConnection SQLSERVER_HOST -Port 1433
Get-Content D:\apps\capability\logs\backend.err.log -Tail 100
Get-Content D:\apps\nginx\logs\error.log -Tail 100
```

If `/login` returns 404 after refresh, confirm the `location /` block still contains `try_files $uri $uri/ /index.html;`. If login returns 502, check that Java is listening on `127.0.0.1:9901` and inspect the backend error log.
