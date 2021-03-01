package com.myorg;


import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.batch.*;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.VpcLookupOptions;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecr.RepositoryProps;
import software.amazon.awscdk.services.ecs.AmiHardwareType;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.EcsOptimizedImage;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.RoleProps;
import software.amazon.awscdk.services.iam.ServicePrincipal;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CdkBatchStack extends Stack {
    private String nodeId = getNode().getId();
    private String computeEnvironmentId = nodeId + "-ComputeEnvironment";
    private String computeEnvironmentName = "TextractTraining-ComputeEnvironment";
    private String jobQueueId = nodeId + "-JobQueue";
    private String jobQueueName = "TextractTraining-PriorityJobQueue";
    private int jobQueueMaxPriority = 10;
    // Job
    private String jobDefinitionId = nodeId + "-JobDefinition";
    private Map<String, String> jobParams = new HashMap() {{
        put("bucket", "");
        put("objectKey", "");
        put("fileExtension", "");
    }}; // bucket, objectKey is required for each submitted job

    // Container to run
    private String containerRepositoryName = "";
    private List containerRunCommands = Arrays.asList("Ref::bucket", "Ref::objectKey", "Ref::fileExtension");

    // Role
    private String roleName = nodeId + "-TextractTrainingRole";

    public CdkBatchStack(Construct scope, String id, StackProps props, String vpcId) {
        super(scope, id, props);
        // Resources to create
        Role role = createRole();
        ComputeEnvironment computeEnvironment = createComputeEnvironment(scope, vpcId);
        JobQueue jobQueue = createJobQueue(scope);
        JobDefinition jobDefinition = createJobDefinition(scope, role);
    }

    private ComputeEnvironment createComputeEnvironment(Construct scope, String vpcId) {
        IVpc vpc = lookupVpc(vpcId);
        EcsOptimizedImage image = EcsOptimizedImage.amazonLinux2(AmiHardwareType.GPU);

        ComputeResources computeResources = ComputeResources.builder()
                .type(ComputeResourceType.SPOT)
                .bidPercentage(70)
                .minvCpus(1)
                .maxvCpus(2)
                .instanceTypes(Arrays.asList(new InstanceType("p2.8xlarge"))) // 8 gpus
                .allocationStrategy(AllocationStrategy.SPOT_CAPACITY_OPTIMIZED)
                .vpc(vpc)
                .image(image)
                .build();

        return ComputeEnvironment.Builder.create(scope, computeEnvironmentId)
                .computeEnvironmentName(computeEnvironmentName)
                .computeResources(computeResources)
                .build();
    }

    private JobQueue createJobQueue(Construct scope) {
        return JobQueue.Builder.create(scope, jobQueueId)
                .jobQueueName(jobQueueName)
                .priority(jobQueueMaxPriority)
//                .computeEnvironments(Arrays.asList(environment))
                .build();
    }

    private JobDefinition createJobDefinition(Construct scope, Role role) {
        Repository repository = new Repository(scope, containerRepositoryName, RepositoryProps.builder().repositoryName(containerRepositoryName).build());
        ContainerImage containerImage = ContainerImage.fromEcrRepository(repository);// e.g: docker image to run the job

        JobDefinitionContainer container = JobDefinitionContainer.builder()
                .image(containerImage)
//      .instanceType(new InstanceType("p2.8xlarge"))
                .command(containerRunCommands)
                .vcpus(2)
                .memoryLimitMiB(8192)
                .gpuCount(4)
                .jobRole(role)
                .build();

        return JobDefinition.Builder.create(scope, jobDefinitionId)
                .timeout(Duration.minutes(60))
                .container(container)
                .parameters(jobParams)
                .build();
    }

    private Role createRole() {
        Role role = new Role(this, roleName,
                RoleProps.builder()
                        .roleName(roleName)
                        .assumedBy(new ServicePrincipal("ec2.amazonaws.com"))
                        //        .managedPolicies(list(ManagedPolicy.fromAwsManagedPolicyName("service-role/AmazonElasticMapReduceforEC2Role")))
                        .build());
        return role;
    }

    private IVpc lookupVpc(String vpcId) {
        return Vpc.fromLookup(this, nodeId + "-Vpc", VpcLookupOptions.builder().vpcId(vpcId).build());
    }
}
