package com.example.uma.api.dto;

import java.math.BigDecimal;

public record OrderDto(int id, String customer, BigDecimal amount, String status) {
}
