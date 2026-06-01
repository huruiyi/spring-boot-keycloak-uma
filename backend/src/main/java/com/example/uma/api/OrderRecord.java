package com.example.uma.api;

import com.example.uma.api.dto.OrderDto;

import java.math.BigDecimal;

public record OrderRecord(
    int id,
    String customer,
    BigDecimal amount,
    String status,
    String owner,
    String department,
    String tenant
) {

  public OrderDto toDto() {
    return new OrderDto(id, customer, amount, status);
  }
}
