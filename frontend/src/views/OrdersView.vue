<script setup lang="ts">
import {onMounted, ref} from "vue";
import {Check, CirclePlus, RefreshCw} from "lucide-vue-next";
import {approveOrder, createOrder, listOrders, type Order} from "../services/orders";
import {loadUiPermissionMap} from "../services/uiPermissions";

const orders = ref<Order[]>([]);
const loading = ref(false);
const permissionLoading = ref(true);
const message = ref("");
const error = ref("");
const permissions = ref<Record<string, boolean>>({});
const orderUiPermissionCodes = [
  "menu.orders",
  "button.orders.refresh",
  "button.orders.create",
  "button.orders.approve"
];

async function run(action: () => Promise<unknown>, ok: string) {
  error.value = "";
  message.value = "";
  loading.value = true;
  try {
    await action();
    message.value = ok;
    if (permissions.value["menu.orders"]) {
      const result = await listOrders();
      orders.value = result.data;
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e);
  } finally {
    loading.value = false;
  }
}

async function load() {
  if (!permissions.value["menu.orders"]) {
    error.value = "当前用户没有订单查看权限";
    return;
  }

  await run(async () => {
    const result = await listOrders();
    orders.value = result.data;
  }, "订单已刷新");
}

onMounted(async () => {
  permissionLoading.value = true;
  permissions.value = await loadUiPermissionMap(orderUiPermissionCodes);
  permissionLoading.value = false;
  await load();
});
</script>

<template>
  <section class="toolbar">
    <div>
      <h2>订单列表</h2>
      <span>菜单和按钮由 UMA RPT 探测结果控制，接口仍会二次校验。</span>
    </div>
    <div class="actions">
      <button v-if="permissions['button.orders.refresh']" class="button secondary" :disabled="loading || permissionLoading" @click="load">
        <RefreshCw :size="17"/>
        刷新
      </button>
      <button
          v-if="permissions['button.orders.create']"
          class="button"
          :disabled="loading || permissionLoading"
          @click="run(createOrder, '订单已创建')"
      >
        <CirclePlus :size="17"/>
        新建
      </button>
      <button
          v-if="permissions['button.orders.approve']"
          class="button success"
          :disabled="loading || permissionLoading"
          @click="run(approveOrder, '订单已审批')"
      >
        <Check :size="17"/>
        审批
      </button>
    </div>
  </section>

  <section class="permission-strip">
    <span :class="{ enabled: permissions['menu.orders'] }">menu.orders</span>
    <span :class="{ enabled: permissions['button.orders.refresh'] }">button.orders.refresh</span>
    <span :class="{ enabled: permissions['button.orders.create'] }">button.orders.create</span>
    <span :class="{ enabled: permissions['button.orders.approve'] }">button.orders.approve</span>
  </section>

  <p v-if="message" class="notice success-text">{{ message }}</p>
  <p v-if="error" class="notice error-text">{{ error }}</p>

  <section v-if="permissions['menu.orders']" class="table-panel">
    <table>
      <thead>
      <tr>
        <th>ID</th>
        <th>客户</th>
        <th>金额</th>
        <th>状态</th>
      </tr>
      </thead>
      <tbody>
      <tr v-for="order in orders" :key="order.id">
        <td>{{ order.id }}</td>
        <td>{{ order.customer }}</td>
        <td>{{ order.amount }}</td>
        <td>
          <span class="status">{{ order.status }}</span>
        </td>
      </tr>
      </tbody>
    </table>
  </section>
  <section v-else-if="!permissionLoading" class="empty-state">
    当前用户没有 `order#view`，不会发起订单列表请求。
  </section>
</template>
