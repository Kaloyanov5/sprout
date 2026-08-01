package com.sprout.core.ioc.visibility.finalmethod;

import com.sprout.core.annotation.Logged;
import com.sprout.core.annotation.Wireable;

@Wireable
public class FinalMethodBean {

    @Logged
    public final String run() {
        return "ok";
    }
}
