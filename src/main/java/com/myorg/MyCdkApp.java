package com.myorg;

import software.amazon.awscdk.core.App;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.StackProps;

import java.util.Arrays;

public class MyCdkApp {
    public static void main(final String[] args) {
        App app = new App();

//        new CdkWorkshopStack(app, "CdkWorkshopStack");
        new CdkBatchStack(app, "Test1CdkBatchStack",  "vpc-73b38114", StackProps.builder().env(makeEnv("796022917205", "ap-southeast-2")).build());
        app.synth();
    }

    private static Environment makeEnv(String account, String region) {
        return Environment.builder().account(account).region(region).build();
    }
}
