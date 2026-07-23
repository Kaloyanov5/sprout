package com.sprout.demo.gateway;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentGatewayTest {

    private final PaymentGateway paymentGateway = new PaymentGateway();

    @Test
    void chargesPositiveAmount() {
        assertTrue(paymentGateway.charge(10.0));
    }

    @Test
    void rejectsNonPositiveAmount() {
        assertFalse(paymentGateway.charge(0));
        assertFalse(paymentGateway.charge(-5));
    }
}
