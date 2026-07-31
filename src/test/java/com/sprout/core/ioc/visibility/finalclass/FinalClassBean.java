package com.sprout.core.ioc.visibility.finalclass;

import com.sprout.core.annotation.Logged;
import com.sprout.core.annotation.Wireable;

@Wireable
public final class FinalClassBean {

    @Logged
    public String run() {
        return "ok";
    }
}
