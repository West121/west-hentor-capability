const BACKEND_ORIGIN = 'http://203.110.232.128.sslip.io:8102';

export default {
  async fetch(request) {
    const url = new URL(request.url);

    if (request.method === 'OPTIONS') {
      return new Response(null, { status: 204, headers: corsHeaders(request) });
    }

    const targetUrl = new URL(url.pathname + url.search, BACKEND_ORIGIN);
    const headers = new Headers(request.headers);
    headers.delete('Host');
    headers.set('X-Forwarded-Proto', 'https');

    const upstream = await fetch(targetUrl, {
      method: request.method,
      headers,
      body: ['GET', 'HEAD'].includes(request.method) ? undefined : request.body,
      redirect: 'manual',
    });

    const responseHeaders = new Headers(upstream.headers);
    for (const [key, value] of Object.entries(corsHeaders(request))) {
      responseHeaders.set(key, value);
    }
    responseHeaders.delete('content-security-policy');

    return new Response(upstream.body, {
      status: upstream.status,
      statusText: upstream.statusText,
      headers: responseHeaders,
    });
  },
};

function corsHeaders(request) {
  const origin = request.headers.get('Origin') || '*';
  return {
    'Access-Control-Allow-Origin': origin,
    'Access-Control-Allow-Methods': 'GET,HEAD,POST,PUT,PATCH,DELETE,OPTIONS',
    'Access-Control-Allow-Headers': request.headers.get('Access-Control-Request-Headers') || 'Authorization,Content-Type,X-Requested-With',
    'Access-Control-Max-Age': '86400',
    Vary: 'Origin',
  };
}
