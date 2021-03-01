package com.myorg;

import software.amazon.awscdk.core.App;

import java.util.Arrays;

public class MyCdkApp {
    public static void main(final String[] args) {
        App app = new App();

//        new CdkWorkshopStack(app, "CdkWorkshopStack");
        new CdkBatchStack(app, "CdkBatchStack", null, "VpcId");
        app.synth();
    }
}
