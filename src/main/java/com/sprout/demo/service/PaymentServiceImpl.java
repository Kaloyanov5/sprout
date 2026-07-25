package com.sprout.demo.service;

import com.sprout.core.annotation.Wire;
import com.sprout.core.annotation.Wireable;
import com.sprout.demo.gateway.PaymentGateway;

@Wireable
public class PaymentServiceImpl implements PaymentService {

    @Wire
    private PaymentGateway paymentGateway;

    @Override
    public PaymentResult pay(String accountId, double amount) {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("accountId must not be blank");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }

        boolean charged = paymentGateway.charge(amount);
        return new PaymentResult(accountId, amount, charged);
    }
}
