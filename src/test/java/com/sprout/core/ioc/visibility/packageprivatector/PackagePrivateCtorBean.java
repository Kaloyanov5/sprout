package com.sprout.core.ioc.visibility.packageprivatector;

import com.sprout.core.annotation.Logged;
import com.sprout.core.annotation.Wireable;

@Wireable
public class PackagePrivateCtorBean {

    PackagePrivateCtorBean() {
    }

    @Logged
    public String run() {
        return "ok";
    }
}
