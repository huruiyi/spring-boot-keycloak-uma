import { createApp } from "vue";
import App from "./App.vue";
import router from "./router";
import { initAuth } from "./services/auth";
import "./styles.css";

async function bootstrap() {
  await initAuth();
  createApp(App).use(router).mount("#app");
}

bootstrap().catch((error) => {
  document.body.innerHTML = `<pre style="padding:24px;color:#b91c1c">${String(error)}</pre>`;
});
