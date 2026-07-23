package com.sprout.core.ioc;

import com.sprout.demo.service.PaymentResult;
import com.sprout.demo.service.PaymentService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeanContainerTest {

    @Test
    void scansInstantiatesAndWiresBeansFromPackage() {
        BeanContainer container = new BeanContainer();
        container.start("com.sprout.demo");

        PaymentService paymentService = container.getBean(PaymentService.class);
        assertNotNull(paymentService, "PaymentService should be discovered and instantiated");

        // If @Wire failed to inject the PaymentGateway field, this throws NPE instead of returning.
        PaymentResult result = paymentService.pay("acc-1", 25.0);
        assertTrue(result.success());
    }

    @Test
    void getBeanReturnsNullForUnmanagedType() {
        BeanContainer container = new BeanContainer();
        container.start("com.sprout.demo");

        assertNull(container.getBean(String.class));
    }
}
