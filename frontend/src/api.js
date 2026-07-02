// Thin fetch wrapper around the Spring Boot REST API. Same-origin in production;
// during `npm run dev` calls are proxied to the backend (see vite.config.js).
export async function api(path, opts = {}) {
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...opts,
  });
  if (!res.ok) {
    let msg = res.statusText;
    try {
      const body = await res.json();
      msg = body.detail || body.message || msg;
    } catch {
      /* ignore parse errors */
    }
    throw new Error(msg);
  }
  return res.status === 204 ? null : res.json();
}
