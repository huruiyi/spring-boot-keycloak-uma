<script setup lang="ts">
import { onMounted, ref } from "vue";
import { Save } from "lucide-vue-next";
import { getSystemConfig, updateSystemConfig } from "../services/system";
import { loadUiPermissionMap } from "../services/uiPermissions";

const config = ref<Record<string, unknown>>({});
const loading = ref(false);
const permissionLoading = ref(true);
const error = ref("");
const message = ref("");
const permissions = ref<Record<string, boolean>>({});
const systemUiPermissionCodes = ["menu.system", "button.system.save"];

async function load() {
  if (!permissions.value["menu.system"]) {
    error.value = "当前用户没有系统配置查看权限";
    return;
  }

  error.value = "";
  loading.value = true;
  try {
    config.value = await getSystemConfig();
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e);
  } finally {
    loading.value = false;
  }
}

async function save() {
  if (!permissions.value["button.system.save"]) {
    error.value = "当前用户没有系统配置保存权限";
    return;
  }

  error.value = "";
  message.value = "";
  loading.value = true;
  try {
    await updateSystemConfig();
    message.value = "系统配置已更新";
    await load();
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e);
  } finally {
    loading.value = false;
  }
}

onMounted(async () => {
  permissionLoading.value = true;
  permissions.value = await loadUiPermissionMap(systemUiPermissionCodes);
  permissionLoading.value = false;
  await load();
});
</script>

<template>
  <section class="toolbar">
    <div>
      <h2>系统配置</h2>
      <span>只有能申请 `system#view` 的用户会看到此菜单，保存按钮需要 `system#edit`。</span>
    </div>
    <button v-if="permissions['button.system.save']" class="button" :disabled="loading || permissionLoading" @click="save">
      <Save :size="17" />
      保存
    </button>
  </section>

  <section class="permission-strip">
    <span :class="{ enabled: permissions['menu.system'] }">menu.system</span>
    <span :class="{ enabled: permissions['button.system.save'] }">button.system.save</span>
  </section>

  <p v-if="message" class="notice success-text">{{ message }}</p>
  <p v-if="error" class="notice error-text">{{ error }}</p>

  <section v-if="permissions['menu.system']" class="details">
    <dl>
      <template v-for="(value, key) in config" :key="key">
        <dt>{{ key }}</dt>
        <dd>{{ value }}</dd>
      </template>
    </dl>
  </section>
  <section v-else-if="!permissionLoading" class="empty-state">
    当前用户没有 `system#view`，不会发起系统配置请求。
  </section>
</template>
