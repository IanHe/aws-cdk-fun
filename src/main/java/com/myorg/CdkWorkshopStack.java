package com.myorg;

import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.apigateway.LambdaRestApi;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.constructs.Construct;

public class CdkWorkshopStack extends Stack {
    public CdkWorkshopStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public CdkWorkshopStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        final Function hello = Function.Builder.create(this, "HelloHandler")
                .runtime(Runtime.NODEJS_10_X)
                .code(Code.fromAsset("lambda"))
                .handler("hello.handler")
                .build();

        final HitCounter helloWithCounter = new HitCounter(this, "HelloHitCounter", HitCounterProps.build().downstream(hello).build());

        LambdaRestApi.Builder.create(this, "Endpoint").handler(helloWithCounter.getHandler()).build();
    }
}
