package com.sprout.demo.service;

import com.sprout.core.annotation.Wireable;

@Wireable
public class Sub extends Base {
    @Override public void audit() { }   // no annotation, as normal
}
