<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { RouterLink, RouterView, useRoute, useRouter } from "vue-router";
import { LogOut, PackageCheck, Settings, ShieldCheck } from "lucide-vue-next";
import { keycloak } from "./services/auth";
import { isRptCacheEnabled, loadPermissionMap, setRptCacheEnabled } from "./services/uma";
import { loadUiPermissions, type UiPermission } from "./services/uiPermissions";

const route = useRoute();
const router = useRouter();
const username = computed(() => keycloak.tokenParsed?.preferred_username ?? "unknown");
const menuPermissions = ref<Record<string, boolean>>({});
const permissionLoading = ref(true);
const rptCacheEnabled = ref(isRptCacheEnabled());
const viewReloadKey = ref(0);

const pageMeta = {
  orders: {
    path: "/orders",
    icon: PackageCheck
  },
  system: {
    path: "/system",
    icon: Settings
  }
} as const;

type MenuItem = UiPermission & {
  path: string;
  label: string;
  icon: typeof PackageCheck;
};

const menus = ref<MenuItem[]>([]);

const visibleMenus = computed(() => menus.value.filter((menu) => menuPermissions.value[menu.permission]));

function logout() {
  keycloak.logout({ redirectUri: window.location.origin });
}

async function loadMenus() {
  permissionLoading.value = true;
  const catalog = await loadUiPermissions();
  menus.value = catalog
    .filter((item) => item.type === "menu" && item.page in pageMeta)
    .map((item) => {
      const meta = pageMeta[item.page as keyof typeof pageMeta];
      return {
        ...item,
        path: meta.path,
        label: item.name.replace(/菜单$/, ""),
        icon: meta.icon
      };
    });
  menuPermissions.value = await loadPermissionMap(menus.value.map((menu) => menu.permission));
  permissionLoading.value = false;

  const currentMenu = menus.value.find((menu) => route.path.startsWith(menu.path));
  if (currentMenu && !menuPermissions.value[currentMenu.permission]) {
    const firstMenu = visibleMenus.value[0];
    if (firstMenu) {
      await router.replace(firstMenu.path);
    }
  }
}

async function toggleRptCache() {
  const enabled = !rptCacheEnabled.value;
  rptCacheEnabled.value = enabled;
  setRptCacheEnabled(enabled);
  viewReloadKey.value += 1;
  await loadMenus();
}

onMounted(async () => {
  await loadMenus();
});
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <ShieldCheck :size="24" />
        <div>
          <strong>UMA Demo</strong>
          <span>按需权限加载</span>
        </div>
      </div>

      <nav class="nav">
        <span v-if="permissionLoading" class="nav-placeholder">加载权限...</span>
        <RouterLink
          v-for="menu in visibleMenus"
          :key="menu.path"
          :to="menu.path"
          :class="{ active: route.path.startsWith(menu.path) }"
        >
          <component :is="menu.icon" :size="18" />
          <span>{{ menu.label }}</span>
        </RouterLink>
      </nav>
    </aside>

    <main class="main">
      <header class="topbar">
        <div>
          <h1>{{ route.meta.title }}</h1>
          <p>{{ route.meta.subtitle }}</p>
        </div>
        <div class="user-box">
          <button
            class="cache-toggle"
            :class="{ enabled: rptCacheEnabled }"
            type="button"
            :aria-pressed="rptCacheEnabled"
            title="切换 RPT 缓存。关闭后每次权限探测和接口调用都会重新请求 Keycloak。"
            @click="toggleRptCache"
          >
            <span>RPT Cache</span>
            <strong>{{ rptCacheEnabled ? "ON" : "OFF" }}</strong>
          </button>
          <span>{{ username }}</span>
          <button class="icon-button" title="退出登录" @click="logout">
            <LogOut :size="18" />
          </button>
        </div>
      </header>

      <RouterView :key="`${route.fullPath}:${viewReloadKey}`" />
    </main>
  </div>
</template>
