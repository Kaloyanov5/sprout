package com.sprout.core.ioc.visibility.packageprivatemethod;

import com.sprout.core.annotation.Logged;
import com.sprout.core.annotation.Wireable;

@Wireable
public class PackagePrivateMethodBean {

    @Logged
    String run() {
        return "ok";
    }
}
