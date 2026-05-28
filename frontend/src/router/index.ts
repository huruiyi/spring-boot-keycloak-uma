import { createRouter, createWebHistory } from "vue-router";
import OrdersView from "../views/OrdersView.vue";
import SystemView from "../views/SystemView.vue";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/",
      redirect: "/orders"
    },
    {
      path: "/orders",
      component: OrdersView,
      meta: {
        title: "订单",
        subtitle: "进入页面先探测 order#view，按钮按需探测 create / approve。"
      }
    },
    {
      path: "/system",
      component: SystemView,
      meta: {
        title: "系统配置",
        subtitle: "菜单需要 system#view，保存按钮需要 system#edit。"
      }
    }
  ]
});

export default router;
