package com.sprout.demo.service;

public record PaymentResult(String accountId, double amount, boolean success) {
}
