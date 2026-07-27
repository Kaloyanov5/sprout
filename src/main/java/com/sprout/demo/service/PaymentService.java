package com.sprout.demo.service;

import com.sprout.core.annotation.*;

public interface PaymentService {

    @Logged
    @Transacted
    @Retried
    PaymentResult pay(String accountId, double amount);
}
