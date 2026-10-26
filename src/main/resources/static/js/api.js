// API 客户端：统一 {code,message} 错误语义（docs/02 壳层约定）。
const BASE = '/api';
let adminToken = null;

export class ApiError extends Error {
  constructor(code, message) { super(message); this.code = code; }
}

async function req(method, path, body, extraHeaders = {}) {
  const headers = { ...extraHeaders };
  if (adminToken) headers['X-Admin-Token'] = adminToken;
  const opt = { method, headers };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    opt.body = typeof body === 'string' ? body : JSON.stringify(body);
  }
  const res = await fetch(BASE + path, opt);
  const text = await res.text();
  let data;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  if (!res.ok) {
    const msg = (data && data.message) || `请求失败（${res.status}）`;
    throw new ApiError(res.status, msg);
  }
  return data;
}

export const api = {
  get: (p) => req('GET', p),
  post: (p, body = {}) => req('POST', p, body),
  patch: (p, body = {}) => req('PATCH', p, body),
  del: (p) => req('DELETE', p),
  raw: (method, text, p) => req(method, p, text),

  /** v2.4/M13.2：multipart 上传（佐证照片、电子书 PDF）。不手动设 Content-Type，交给浏览器带 boundary。 */
  async upload(path, formData) {
    const headers = {};
    if (adminToken) headers['X-Admin-Token'] = adminToken;
    const res = await fetch(BASE + path, { method: 'POST', headers, body: formData });
    const text = await res.text();
    let data;
    try { data = text ? JSON.parse(text) : null; } catch { data = text; }
    if (!res.ok) {
      const msg = (data && data.message) || `上传失败（${res.status}）`;
      throw new ApiError(res.status, msg);
    }
    return data;
  },

  setAdminToken(t) { adminToken = t; },
  get adminToken() { return adminToken; },

  /** 带 token 的后台请求；401 时清 token 让界面回 PIN 门。 */
  async admin(method, p, body) {
    try {
      return await req(method, '/admin' + p, body);
    } catch (e) {
      if (e.code === 401) adminToken = null;
      throw e;
    }
  },
};
