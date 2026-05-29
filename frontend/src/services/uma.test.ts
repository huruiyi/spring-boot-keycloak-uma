import { beforeEach, describe, expect, test, vi } from "vitest";

const getAccessToken = vi.fn();

vi.mock("./auth", () => ({
  getAccessToken
}));

function installLocalStorage() {
  const values = new Map<string, string>();

  vi.stubGlobal("localStorage", {
    getItem: vi.fn((key: string) => values.get(key) ?? null),
    setItem: vi.fn((key: string, value: string) => values.set(key, value)),
    removeItem: vi.fn((key: string) => values.delete(key)),
    clear: vi.fn(() => values.clear())
  });
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json"
    }
  });
}

async function loadUmaModule() {
  vi.resetModules();
  installLocalStorage();
  getAccessToken.mockResolvedValue("access-token");
  return import("./uma");
}

beforeEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  getAccessToken.mockReset();
});

describe("getRpt", () => {
  test("caches successful RPT requests by permission set", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ access_token: "rpt-token", expires_in: 60 }));
    vi.stubGlobal("fetch", fetchMock);

    const { getRpt } = await loadUmaModule();

    await expect(getRpt(["order#view"])).resolves.toBe("rpt-token");
    await expect(getRpt(["order#view"])).resolves.toBe("rpt-token");

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(getAccessToken).toHaveBeenCalledTimes(1);
  });

  test("caches denied permission probes for a short interval", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response("not_authorized", { status: 403 }));
    vi.stubGlobal("fetch", fetchMock);

    const { getRpt } = await loadUmaModule();

    await expect(getRpt(["order#approve"])).rejects.toThrow();
    await expect(getRpt(["order#approve"])).rejects.toThrow();

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(getAccessToken).toHaveBeenCalledTimes(1);
  });
});

describe("apiFetch", () => {
  test("uses the RPT as the backend Authorization header", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ access_token: "rpt-token", expires_in: 60 }))
      .mockResolvedValueOnce(jsonResponse({ data: [] }));
    vi.stubGlobal("fetch", fetchMock);

    const { apiFetch } = await loadUmaModule();

    await expect(apiFetch("/api/orders", ["order#view"])).resolves.toEqual({ data: [] });

    const backendRequest = fetchMock.mock.calls[1];
    const headers = backendRequest[1]?.headers as Headers;

    expect(backendRequest[0]).toBe("http://localhost:9000/api/orders");
    expect(headers.get("Authorization")).toBe("Bearer rpt-token");
  });
});
