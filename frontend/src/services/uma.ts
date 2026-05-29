import { getAccessToken } from "./auth";

type RptResponse = {
  access_token: string;
  expires_in: number;
};

type RptCacheEntry = {
  token: string;
  expMs: number;
};

const rptCache = new Map<string, RptCacheEntry>();
const deniedCache = new Map<string, number>();
const expirySkewMs = 10_000;
const deniedCacheMs = 30_000;
const rptCacheStorageKey = "uma-demo:rpt-cache-enabled";

const keycloakBaseUrl = import.meta.env.VITE_KEYCLOAK_URL ?? "http://localhost:8080";
const realm = import.meta.env.VITE_KEYCLOAK_REALM ?? "demo";
const audience = import.meta.env.VITE_KEYCLOAK_AUDIENCE ?? "demo-api";
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:9000";

let rptCacheEnabled = localStorage.getItem(rptCacheStorageKey) !== "false";

function cacheKey(permissions: string[]) {
  return permissions.slice().sort().join("|");
}

export function isRptCacheEnabled() {
  return rptCacheEnabled;
}

export function setRptCacheEnabled(enabled: boolean) {
  rptCacheEnabled = enabled;
  localStorage.setItem(rptCacheStorageKey, String(enabled));
  clearRptCache();
}

export function clearRptCache() {
  rptCache.clear();
  deniedCache.clear();
}

export async function getRpt(permissions: string[]) {
  const key = cacheKey(permissions);

  if (rptCacheEnabled) {
    const cached = rptCache.get(key);
    if (cached && cached.expMs - expirySkewMs > Date.now()) {
      return cached.token;
    }

    const deniedUntil = deniedCache.get(key);
    if (deniedUntil && deniedUntil > Date.now()) {
      throw new Error("当前用户没有该操作权限");
    }
  }

  const accessToken = await getAccessToken();
  const body = new URLSearchParams();
  body.set("grant_type", "urn:ietf:params:oauth:grant-type:uma-ticket");
  body.set("audience", audience);
  permissions.forEach((permission) => body.append("permission", permission));

  const response = await fetch(`${keycloakBaseUrl}/realms/${realm}/protocol/openid-connect/token`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/x-www-form-urlencoded"
    },
    body
  });

  if (!response.ok) {
    const text = await response.text();
    if (response.status === 403 || text.includes("not_authorized") || text.includes("access_denied")) {
      if (rptCacheEnabled) {
        deniedCache.set(key, Date.now() + deniedCacheMs);
      }
      throw new Error("当前用户没有该操作权限");
    }
    throw new Error(`RPT 请求失败：${response.status} ${text}`);
  }

  const json = (await response.json()) as RptResponse;
  if (rptCacheEnabled) {
    rptCache.set(key, {
      token: json.access_token,
      expMs: Date.now() + json.expires_in * 1000
    });
  }
  return json.access_token;
}

export async function canRequestPermission(permission: string) {
  try {
    await getRpt([permission]);
    return true;
  } catch {
    return false;
  }
}

export async function loadPermissionMap(permissions: string[]) {
  const entries = await Promise.all(
    permissions.map(async (permission) => [permission, await canRequestPermission(permission)] as const)
  );
  return Object.fromEntries(entries) as Record<string, boolean>;
}

export async function apiFetch<T>(url: string, permissions: string[], init: RequestInit = {}) {
  const rpt = await getRpt(permissions);
  const headers = new Headers(init.headers);
  headers.set("Authorization", `Bearer ${rpt}`);
  if (init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(`${apiBaseUrl}${url}`, {
    ...init,
    headers
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(`${response.status} ${text}`);
  }

  return (await response.json()) as T;
}
