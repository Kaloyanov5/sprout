package com.sprout.core.ioc.visibility.privatector;

import com.sprout.core.annotation.Logged;
import com.sprout.core.annotation.Wireable;

@Wireable
public class PrivateCtorBean {

    private PrivateCtorBean() {
    }

    @Logged
    public String run() {
        return "ok";
    }
}
