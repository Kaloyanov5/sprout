package com.sprout.demo.service;

import com.sprout.core.annotation.Logged;
import com.sprout.core.annotation.Wireable;

import java.io.Serializable;

@Wireable
public class ReportService implements Serializable {
    @Logged
    public void generate() { System.out.println("generating"); }
}
