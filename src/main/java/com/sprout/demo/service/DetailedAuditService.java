package com.sprout.demo.service;

import com.sprout.core.annotation.Wireable;

@Wireable
public class DetailedAuditService extends AuditService {
    @Override public void audit() { }   // no annotation, as normal
}
