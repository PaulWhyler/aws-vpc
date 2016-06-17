package org.pmwhyle.eg.vpc;

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;
import com.google.common.base.Charsets;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.pmwhyle.eg.vpc.AMIImages.imageIdFor;

/**
 */
class NewCreateInstances {

    private static final String CLUSTER_DEFN_TEMPLATE = "#!/bin/bash\n" +
            "echo ECS_CLUSTER=%s >> /etc/ecs/ecs.config\n";

    private static RunInstancesRequest createInstanceRequest(String keyName) {
        RunInstancesRequest pub;
        pub = new RunInstancesRequest()
                .withInstanceType(InstanceType.T2Micro)
                .withKeyName(keyName)
                .withMinCount(1)
                .withMaxCount(1);
        return pub;
    }

    private static RunInstancesRequest createAccessInstanceRequest(AmazonEC2Client client, String securityGroup, String subnetId, String keyName) {
        return createInstanceRequest(keyName)
                .withImageId(imageIdFor(client, AMIImages.NATImage))
                .withNetworkInterfaces(
                        new InstanceNetworkInterfaceSpecification()
                                .withAssociatePublicIpAddress(true)
                                .withDeviceIndex(0)
                                .withGroups(securityGroup)
                                .withSubnetId(subnetId)
                );
    }

    private static RunInstancesRequest createEcsInstanceRequest(AmazonEC2Client client, String subnet, String securityGroup, String keyName, String ecsInstanceProfileArn, String clusterName) {
        return createInstanceRequest(keyName)
                .withIamInstanceProfile(
                        new IamInstanceProfileSpecification()
                                .withArn(ecsInstanceProfileArn))
                .withImageId(imageIdFor(client, AMIImages.ECSImage))
                .withUserData(Base64.getEncoder().encodeToString(String.format(CLUSTER_DEFN_TEMPLATE, clusterName).getBytes(Charsets.UTF_8)))
                .withNetworkInterfaces(
                        new InstanceNetworkInterfaceSpecification()
                                .withAssociatePublicIpAddress(false)
                                .withDeviceIndex(0)
                                .withSubnetId(subnet)
                                .withGroups(securityGroup));
    }


    static String createAccessInstance(AmazonEC2Client client, Map<String, String> subnets, String accessSecurityGroup, String keyName, Tag... tags) {
        System.out.print("\nCreating public instance");

        List<Filter> filters = CreateVpc.getFiltersForTags(tags);
        filters.add(new Filter("instance-state-name").withValues("running", "pending", "stopped", "shutting-down", "stopping"));
        List<Reservation> existing = client.describeInstances(
                new DescribeInstancesRequest().withFilters(filters))
                .getReservations();

        if (hasNonTerminatedInstance(existing))
            return getFromReservationOfSingleInstance(existing, Instance::getInstanceId);

        RunInstancesResult pub = client.runInstances(
                createAccessInstanceRequest(client, accessSecurityGroup, subnets.values().iterator().next(), keyName));
        client.createTags(
                new CreateTagsRequest()
                        .withResources(pub.getReservation().getInstances().stream().map(Instance::getInstanceId).collect(Collectors.toList()))
                        .withTags(tags));
        pub.getReservation().getInstances().forEach(
                instance -> client.modifyInstanceAttribute(
                        new ModifyInstanceAttributeRequest()
                                .withInstanceId(instance.getInstanceId())
                                .withSourceDestCheck(false)));
        return getFromReservationOfSingleInstance(pub.getReservation(), Instance::getInstanceId);
    }

    private static String getFromReservationOfSingleInstance(Reservation reservation, Function<Instance,String> toGet) {
        return toGet.apply(reservation.getInstances().get(0));
    }

    private static String getFromReservationOfSingleInstance(List<Reservation> reservations, Function<Instance, String> toGet) {
        return toGet.apply(reservations.get(0).getInstances().get(0));
    }

    private static boolean hasNonTerminatedInstance(List<Reservation> existing) {
        return existing.size() == 1 &&
                existing.get(0).getInstances().size() == 1;
    }

    static List<Reservation> createEcsInstances(AmazonEC2Client client, String clusterName, Map<String, String> subnets, String privateSecurityGroup, String instanceProfileArn, String keyName, Tag... tags) {
        System.out.print("\nCreating ECS instances");
        List<Reservation> existing = client.describeInstances(
                new DescribeInstancesRequest().
                        withFilters(CreateVpc.getFiltersForTags(tags)))
                .getReservations();

        if (existing.stream().mapToInt(r -> r.getInstances().size()).sum() == 1)  // TODO temporary limit of 1?
            return existing;

        List<Reservation> priv = subnets.values().stream().skip(1).limit(1)  // TODO temporary limit of 1?
                .map(subnet -> createEcsInstanceRequest(client, subnet, privateSecurityGroup, keyName, instanceProfileArn, clusterName))
                .map(client::runInstances)
                .map(RunInstancesResult::getReservation)
                .collect(Collectors.toList());
        priv.forEach(r -> client.createTags(
                new CreateTagsRequest(
                        r.getInstances()
                                .stream()
                                .map(Instance::getInstanceId)
                                .collect(Collectors.toList()),
                        Arrays.asList(tags))));
        return priv;
    }

    private static String getPublicIpAddress(AmazonEC2Client ec2Client, String publicInstanceId) {
        return getFromReservationOfSingleInstance(
                ec2Client.describeInstances(
                        new DescribeInstancesRequest().withInstanceIds(publicInstanceId))
                        .getReservations(),
                Instance::getPublicIpAddress);

    }

    static String getPublicIp(AmazonEC2Client ec2Client, String publicInstanceId) {
        String publicIp = getPublicIpAddress(ec2Client, publicInstanceId);
        while (publicIp == null) {
            CreateVpc.pause(1000);
            publicIp = getPublicIpAddress(ec2Client, publicInstanceId);
        }
        return publicIp;
    }
}
