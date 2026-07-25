package com.sprout.demo.service;

import com.sprout.demo.gateway.PaymentGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentGateway paymentGateway;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    @Test
    void payReturnsSuccessfulResultWhenGatewayCharges() {
        when(paymentGateway.charge(100.0)).thenReturn(true);

        PaymentResult result = paymentService.pay("acc-1", 100.0);

        assertTrue(result.success());
        assertEquals("acc-1", result.accountId());
        assertEquals(100.0, result.amount());
    }

    @Test
    void payReturnsFailedResultWhenGatewayDeclines() {
        when(paymentGateway.charge(50.0)).thenReturn(false);

        PaymentResult result = paymentService.pay("acc-1", 50.0);

        assertFalse(result.success());
    }

    @Test
    void payRejectsNonPositiveAmount() {
        assertThrows(IllegalArgumentException.class, () -> paymentService.pay("acc-1", 0));
    }

    @Test
    void payRejectsBlankAccountId() {
        assertThrows(IllegalArgumentException.class, () -> paymentService.pay(" ", 10));
    }
}
