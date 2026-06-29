# 帽子云静态前端部署

后端仍部署在测试服务器，帽子云只托管 React/Vite 静态前端。

## 帽子云构建配置

仓库：`West121/west-hentor-capability`

分支：`main`

根目录：

```text
capability-replica/frontend
```

构建命令：

```bash
npm install && npm run build
```

输出目录：

```text
dist
```

环境变量：

```text
VITE_API_BASE_URL=http://203.110.232.128:8102
```

如果帽子云应用绑定的是 HTTPS 域名，浏览器可能会阻止 HTTPS 页面调用 HTTP 后端。生产使用时建议给后端也绑定 HTTPS 域名，例如 `https://api.example.com`，然后把 `VITE_API_BASE_URL` 改成这个 HTTPS 地址。

## 后端 CORS 配置

帽子云生成前端访问域名后，需要把这个域名加入后端 CORS 白名单。后端环境变量名：

```text
APP_CORS_ALLOWED_ORIGIN_PATTERNS=https://你的帽子云域名,http://localhost:*,http://127.0.0.1:*
```

改完后重启后端服务。

如果后端也配置了 HTTPS 域名，可以同时设置：

```text
APP_SERVER_ROOT_ADDRESS=https://api.example.com
APP_CORS_ALLOWED_ORIGIN_PATTERNS=https://你的帽子云域名,http://localhost:*,http://127.0.0.1:*
```

## 常见问题

- 登录接口 CORS 报错：后端没有放行帽子云前端域名。
- 浏览器提示 Mixed Content：HTTPS 前端正在调用 HTTP 后端，需要给后端配置 HTTPS，或改用同协议访问。
- 静态资源 404：根目录或输出目录设置错了，确认根目录是 `capability-replica/frontend`，输出目录是 `dist`。
