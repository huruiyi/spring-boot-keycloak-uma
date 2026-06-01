import { getAccessToken } from "./auth";
import { loadPermissionMap } from "./uma";

export type UiPermission = {
  code: string;
  name: string;
  type: "menu" | "button" | "page" | string;
  page: string;
  permission: string;
  sort: number;
  enabled: boolean;
};

type UiPermissionListResponse = {
  data: UiPermission[];
};

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:9000";

let uiPermissionsCache: UiPermission[] | null = null;

export async function loadUiPermissions() {
  if (uiPermissionsCache) {
    return uiPermissionsCache;
  }

  const token = await getAccessToken();
  const response = await fetch(`${apiBaseUrl}/api/ui-permissions`, {
    headers: {
      Authorization: `Bearer ${token}`
    }
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(`UI 权限清单加载失败：${response.status} ${text}`);
  }

  const json = (await response.json()) as UiPermissionListResponse;
  uiPermissionsCache = json.data.filter((item) => item.enabled);
  return uiPermissionsCache;
}

export async function loadUiPermissionMap(codes: string[]) {
  const catalog = await loadUiPermissions();
  const byCode = Object.fromEntries(catalog.map((item) => [item.code, item]));
  const permissions = Array.from(new Set(codes.map((code) => byCode[code]?.permission).filter(Boolean)));
  const permissionMap = await loadPermissionMap(permissions);

  return Object.fromEntries(
    codes.map((code) => {
      const permission = byCode[code]?.permission;
      return [code, permission ? Boolean(permissionMap[permission]) : false];
    })
  ) as Record<string, boolean>;
}
