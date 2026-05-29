import Keycloak from "keycloak-js";

export const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL ?? "http://localhost:8080",
  realm: import.meta.env.VITE_KEYCLOAK_REALM ?? "demo",
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? "demo-frontend"
});

export async function initAuth() {
  const authenticated = await keycloak.init({
    onLoad: "login-required",
    pkceMethod: "S256",
    checkLoginIframe: false
  });

  if (!authenticated) {
    await keycloak.login();
  }
}

export async function getAccessToken() {
  await keycloak.updateToken(30);
  if (!keycloak.token) {
    throw new Error("No Keycloak access token");
  }
  return keycloak.token;
}
