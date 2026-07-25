package com.sprout.demo.service;

import com.sprout.core.annotation.*;

public interface PaymentService {

    @Logged
    @MyTransactional
    @MyRetry
    PaymentResult pay(String accountId, double amount);
}
