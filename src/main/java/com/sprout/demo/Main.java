package com.sprout.demo;

import com.sprout.core.ioc.BeanContainer;
import com.sprout.demo.service.PaymentService;

public class Main {


    public static void main(String[] args) {
        BeanContainer container = new BeanContainer();
        container.start("com.sprout.demo");
        PaymentService psv = container.getBean(PaymentService.class);
        System.out.println(psv.pay("1234", 50));
    }
}
