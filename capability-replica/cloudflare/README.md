# Cloudflare Deployment

This folder contains the HTTPS API proxy used when the React frontend is hosted on Cloudflare Pages and the Spring Boot backend remains on the test server.

## Current URLs

- Pages production: `https://west-hentor-capability.pages.dev`
- Latest deployment: `https://df61cc35.west-hentor-capability.pages.dev`
- API proxy Worker: `https://west-hentor-capability-api.west-hentor-capability-api.workers.dev`
- Backend origin: `http://203.110.232.128:8102`

The Worker uses `http://203.110.232.128.sslip.io:8102` instead of the raw IP because Cloudflare Workers returns `error code: 1003` when proxying to the raw IP host.

## Deploy API Proxy

```bash
cd capability-replica/cloudflare
npx wrangler deploy --config wrangler-api.toml
```

## Deploy Frontend to Pages

```bash
cd capability-replica/frontend
VITE_API_BASE_URL=https://west-hentor-capability-api.west-hentor-capability-api.workers.dev npm run build
npx wrangler pages deploy dist --project-name west-hentor-capability --branch main --commit-dirty=true
```

The frontend is a static Vite build. If using Cloudflare Pages Git integration instead of manual `wrangler pages deploy`, configure:

- Root directory: `capability-replica/frontend`
- Build command: `npm install && npm run build`
- Output directory: `dist`
- Environment variable: `VITE_API_BASE_URL=https://west-hentor-capability-api.west-hentor-capability-api.workers.dev`
