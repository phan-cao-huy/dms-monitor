export const API_BASE = 'https://dms.ky215.io.vn/api/v1/';
export const SERVER = 'https://dms.ky215.io.vn';

export class ApiError extends Error {
  constructor(message, status = 0) { super(message); this.name = 'ApiError'; this.status = status; }
}

export function createClient({fetcher = (...args) => fetch(...args), storage, onUnauthorized = () => {}} = {}) {
  let tokens = null;
  let refreshing = null;
  let generation = 0;
  try {
    const saved = JSON.parse(storage?.getItem('dms.web.session') || 'null');
    if (typeof saved?.access_token === 'string' && typeof saved?.refresh_token === 'string') tokens = saved;
  } catch { storage?.removeItem('dms.web.session'); }
  const persist = (value) => {
    generation++;
    tokens = value;
    try { value ? storage?.setItem('dms.web.session', JSON.stringify(value)) : storage?.removeItem('dms.web.session'); } catch {}
  };
  const send = async (path, options, token) => {
    if (!/^[a-zA-Z0-9]/.test(path) || path.includes('://') || path.includes('..')) throw new ApiError('Đường dẫn API không hợp lệ.');
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 20000);
    try {
      return await fetcher(API_BASE + path, {
        method: options.method || 'GET', credentials: 'omit', redirect: 'error', signal: controller.signal,
        headers: {Accept: 'application/json', ...(options.body !== undefined ? {'Content-Type': 'application/json'} : {}), ...(token ? {Authorization: `Bearer ${token}`} : {})},
        ...(options.body !== undefined ? {body: JSON.stringify(options.body)} : {}),
      });
    } catch (error) {
      throw new ApiError(error.name === 'AbortError' ? 'Server phản hồi quá lâu. Hãy thử lại.' : 'Không kết nối được server. Kiểm tra mạng hoặc quyền truy cập tunnel/CORS.');
    } finally { clearTimeout(timeout); }
  };
  const decode = async (response) => {
    if (response.status === 204) return null;
    let data;
    try { data = await response.json(); } catch { throw new ApiError(`Server trả nội dung không hợp lệ (HTTP ${response.status}).`, response.status); }
    if (!response.ok) {
      const detail = data.detail;
      const message = typeof detail === 'string' ? detail : Array.isArray(detail) ? detail.map(e => `${e.loc?.slice(1).join('.') || 'Dữ liệu'}: ${e.msg}`).join('; ') : `Yêu cầu thất bại (HTTP ${response.status}).`;
      throw new ApiError(message, response.status);
    }
    return data;
  };
  const refresh = async () => {
    if (!refreshing) {
      const currentGeneration = generation;
      const refreshToken = tokens?.refresh_token;
      refreshing = (async () => {
        if (!refreshToken) throw new ApiError('Phiên đăng nhập đã hết hạn.', 401);
        const response = await send('auth/refresh', {method:'POST', body:{refresh_token:refreshToken}}, null);
        if (response.status === 401) {
          if (generation === currentGeneration) { persist(null); onUnauthorized(); }
          throw new ApiError('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.', 401);
        }
        const next = await decode(response);
        if (generation !== currentGeneration) throw new ApiError('Phiên đăng nhập đã thay đổi.', 401);
        if (!next?.access_token || !next?.refresh_token) throw new ApiError('Phản hồi làm mới phiên không hợp lệ.');
        persist(next);
      })().finally(() => { refreshing = null; });
    }
    return refreshing;
  };
  return {
    get session() { return tokens; },
    setSession: persist,
    clearSession() { persist(null); },
    async request(path, options = {}) {
      const previousToken = tokens?.access_token;
      let response = await send(path, options, options.auth === false ? null : previousToken);
      if (response.status === 401 && options.auth !== false && tokens?.refresh_token) {
        if (tokens.access_token === previousToken) await refresh();
        response = await send(path, options, tokens?.access_token);
      }
      return decode(response);
    },
  };
}
