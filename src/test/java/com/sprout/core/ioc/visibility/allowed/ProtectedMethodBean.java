package com.sprout.core.ioc.visibility.allowed;

import com.sprout.core.annotation.Logged;
import com.sprout.core.annotation.Wireable;

@Wireable
public class ProtectedMethodBean {

    @Logged
    protected String run() {
        return "ok";
    }
}
