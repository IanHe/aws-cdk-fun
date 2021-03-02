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
import software.amazon.awscdk.services.ecr.IRepository;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecr.RepositoryProps;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.EcsOptimizedImage;
import software.amazon.awscdk.services.iam.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CdkBatchStack extends Stack {
    private String nodeId = getNode().getId() + "1";
    private String computeEnvironmentId = nodeId + "-ComputeEnvironment";
    private String computeEnvironmentName = nodeId + "-ComputeEnvironment";
    private String jobQueueId = nodeId + "-JobQueue";
    private String jobQueueName = "TextractTraining-PriorityJobQueue";
    private int jobQueueMaxPriority = 10;
    private Duration containerTimeout = Duration.minutes(6);
    // Job
    private String jobDefinitionId = nodeId + "-JobDefinition";
    private Map<String, String> jobParams = new HashMap() {{
        put("bucket", "");
        put("objectKey", "");
        put("fileExtension", "");
    }}; // bucket, objectKey is required for each submitted job

    // Container to run
    private String containerRepositoryName = "my-repo";
    private List containerRunCommands = Arrays.asList("Ref::bucket", "Ref::objectKey", "Ref::fileExtension");

    // Role
    private String roleName = nodeId + "-TextractTrainingRole";

    public CdkBatchStack(Construct scope, String id, String vpcId, StackProps props) {
        super(scope, id, props);
        // Resources to create
        Role role = createRole();
        ComputeEnvironment computeEnvironment = createComputeEnvironment(vpcId);
        JobQueue jobQueue = createJobQueue(computeEnvironment);
        JobDefinition jobDefinition = createJobDefinition(role);
    }

    private ComputeEnvironment createComputeEnvironment(String vpcId) {
        IVpc vpc = lookupVpc(vpcId);
        EcsOptimizedImage image = EcsOptimizedImage.amazonLinux2();//EcsOptimizedImage.amazonLinux2(AmiHardwareType.GPU);

        IRole serviceRole = Role.fromRoleArn(this, nodeId + "-ComputeEnvRole", "arn:aws:iam::aws:policy/service-role/AWSBatchServiceRole");

        ComputeResources computeResources = ComputeResources.builder()
                .type(ComputeResourceType.SPOT)
                .bidPercentage(70)
                .minvCpus(1)
                .maxvCpus(1)
                .instanceTypes(Arrays.asList(new InstanceType("a1.medium"))) // p2.8xlarge - 8 gpus
                .allocationStrategy(AllocationStrategy.SPOT_CAPACITY_OPTIMIZED)
                .vpc(vpc)
                .image(image)
                .build();

        return ComputeEnvironment.Builder.create(this, computeEnvironmentId)
                .computeEnvironmentName(computeEnvironmentName)
                .computeResources(computeResources)
                .serviceRole(serviceRole)
                .build();
    }

    private JobQueue createJobQueue(IComputeEnvironment environment) {
        JobQueueComputeEnvironment jobQueueEnv = JobQueueComputeEnvironment.builder().computeEnvironment(environment).order(1).build();
        return JobQueue.Builder.create(this, jobQueueId)
                .jobQueueName(jobQueueName)
                .priority(jobQueueMaxPriority)
                .computeEnvironments(Arrays.asList(jobQueueEnv))
                .build();
    }

    private JobDefinition createJobDefinition(Role role) {
        IRepository repository = Repository.fromRepositoryName(this, nodeId + "-JobRepo", containerRepositoryName);
        ContainerImage containerImage = ContainerImage.fromEcrRepository(repository);// e.g: docker image to run the job
//        ContainerImage containerImage = ContainerImage.fromRegistry("public.ecr.aws/amazonlinux/amazonlinux:latest");
        JobDefinitionContainer container = JobDefinitionContainer.builder()
                .image(containerImage)
//      .instanceType(new InstanceType("a1.medium")) // p2.8xlarge
                .command(containerRunCommands)
                .vcpus(1)
                .memoryLimitMiB(2048)
//                .gpuCount(4)
                .jobRole(role)
                .build();

        return JobDefinition.Builder.create(this, jobDefinitionId)
                .timeout(containerTimeout)
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
