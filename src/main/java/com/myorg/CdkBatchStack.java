package com.myorg;


import org.jetbrains.annotations.Nullable;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;

public class CdkBatchStack extends Stack {
    private Construct scope;
    private String id;
    private StackProps props;

    public CdkBatchStack( Construct scope,  String id,  StackProps props) {
        super(scope, id, props);
        this.scope = scope;
        this.id = id;
        this.props = props;
    }

    private val computeEnvironmentId = s"${getNode().getId()}-ComputeEnvironment"
    private val computeEnvironmentName = "TextractTraining-ComputeEnvironment"
    private val jobQueueId = s"${getNode().getId()}-JobQueue"
    private val jobQueueName = "TextractTraining-PriorityJobQueue"
    private val jobQueueMaxPriority = 10
    // Job
    private val jobDefinitionId = s"${getNode().getId()}-JobDefinition"
    private val jobParams = map( ("bucket",""), ("objectKey", ""), ("fileExtension", "")) // bucket, objectKey is required for each submitted job

    // Container to run
    private val containerRepositoryName = ""
    private val containerRunCommands = list("Ref::bucket", "Ref::objectKey", "Ref::fileExtension")

    // Role
    private val roleName = s"${getNode().getId()}-TextractTrainingRole"

    // Resources to create
    private val role = createRole()
    private val computeEnvironment = createComputeEnvironment()
    private val jobQueue = createJobQueue(computeEnvironment)
    private val jobDefinition = createJobDefinition()

    private def createComputeEnvironment(): ComputeEnvironment = {
        val vpc = lookupVpc()
        val image = EcsOptimizedImage.amazonLinux2(AmiHardwareType.GPU)

        val computeResources = ComputeResources.builder()
                .`type`(ComputeResourceType.SPOT)
                .bidPercentage(70)
                .minvCpus(1)
                .maxvCpus(2)
                .instanceTypes(list("p2.8xlarge")) // 8 gpus
                .allocationStrategy(AllocationStrategy.SPOT_CAPACITY_OPTIMIZED)
                .vpc(vpc)
                .image(image)
                .build()

        ComputeEnvironment.Builder.create(scope, computeEnvironmentId)
                .computeEnvironmentName(computeEnvironmentName)
                .computeResources(computeResources)
                .build()
    }

    private def createJobQueue(environment: ComputeEnvironment): JobQueue = {
        JobQueue.Builder.create(scope, jobQueueId)
                .jobQueueName(jobQueueName)
                .priority(jobQueueMaxPriority)
                .computeEnvironments(list(environment))
                .build()
    }

    private def createJobDefinition(): JobDefinition = {
        val repository = new Repository(scope, containerRepositoryName, RepositoryProps.builder().repositoryName(containerRepositoryName).build())
        val containerImage = ContainerImage.fromEcrRepository(repository) // e.g: docker image to run the job

        val container = JobDefinitionContainer.builder()
                .image(containerImage)
//      .instanceType(new InstanceType("p2.8xlarge"))
                .command(containerRunCommands)
                .vcpus(2)
                .memoryLimitMiB(8192)
                .gpuCount(4)
                .jobRole(role)
                .build()

        JobDefinition.Builder.create(scope, jobDefinitionId)
                .timeout(Duration.minutes(60))
                .container(container)
                .parameters(jobParams)
                .build()
    }

    private def createRole(): Role = {
        val role = new Role(this, roleName,
                RoleProps.builder()
                        .roleName(roleName)
                        .assumedBy(new ServicePrincipal("ec2.amazonaws.com"))
                        //        .managedPolicies(list(ManagedPolicy.fromAwsManagedPolicyName("service-role/AmazonElasticMapReduceforEC2Role")))
                        .build())
        role
    }

    private def lookupVpc(): IVpc = {
        Vpc.fromLookup(this, s"${getNode().getId()}-Vpc", VpcLookupOptions.builder().vpcId(vpcId).build())
    }
}
