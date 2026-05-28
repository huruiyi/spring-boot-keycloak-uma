import { apiFetch } from "./uma";

export type Order = {
  id: number;
  customer: string;
  amount: number;
  status: string;
};

export async function listOrders() {
  return apiFetch<{ data: Order[] }>("/api/orders", ["order#view"]);
}

export async function createOrder() {
  return apiFetch<{ created: boolean }>("/api/orders", ["order#create"], {
    method: "POST",
    body: JSON.stringify({
      customer: "新客户",
      amount: 668.8,
      status: "PENDING"
    })
  });
}

export async function approveOrder() {
  return apiFetch<{ approved: boolean }>("/api/orders/approve", ["order#approve"], {
    method: "POST"
  });
}
