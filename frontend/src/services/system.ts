import { apiFetch } from "./uma";

export async function getSystemConfig() {
  return apiFetch<Record<string, unknown>>("/api/system/config", ["system#view"]);
}

export async function updateSystemConfig() {
  return apiFetch<{ updated: boolean }>("/api/system/config", ["system#edit"], {
    method: "POST"
  });
}
