package com.sprout.demo.gateway;

import com.sprout.core.annotation.Wireable;

@Wireable
public class PaymentGateway {

    public boolean charge(double amount) {
        return amount > 0;
    }
}
