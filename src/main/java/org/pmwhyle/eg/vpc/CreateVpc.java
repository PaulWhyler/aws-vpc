package org.pmwhyle.eg.vpc;

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.ecs.AmazonECSClient;
import com.amazonaws.services.ecs.model.CreateClusterRequest;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 */
public class CreateVpc {


    static final Tag PUBLIC_TAG = new Tag("eg-access").withValue("public-access");
    static final Tag PRIVATE_TAG = new Tag("eg-access").withValue("private-access");

    private final AmazonEC2Client ec2Client;
    private final AmazonIdentityManagementClient iamClient;
    private final AmazonECSClient ecsClient;
    private final String id;
    private final Tag idTag;

    private CreateVpc(String id) {
        this.id = id;
        this.idTag = new Tag("eg-id").withValue(id);

        ec2Client = new AmazonEC2Client();
        iamClient = new AmazonIdentityManagementClient();
        ecsClient = new AmazonECSClient();
    }

    public static void main(String[] args) {

        if (args.length != 1 && args.length != 2) {
            usage();
            System.exit(22);
        }

        if (args.length > 1 && "delete".equals(args[1])) {
            DeleteVpc deleteVpc = new DeleteVpc(args[0]);
            deleteVpc.execute();
            System.exit(0);
        }

        CreateVpc createVpc = new CreateVpc(args[0]);
        VpcInfo vpcInfo = createVpc.execute();

        System.out.println(vpcInfo);

        System.exit(0);
    }

    private static void usage() {
        StringBuilder sb = new StringBuilder();
        sb.append("create-vpc takes either a single argument that will identify resources created,");
        sb.append("\n or that identifying argument plus the word 'delete' to remove all resources");
        sb.append("\n identified by the id");
        sb.append("\n\nFor example,");
        sb.append("\n\n\tjava -jar build/libs/create-vpc.jar my-eg");
        sb.append("\n\nwill create a set of resources identified by 'eg-id' tags of 'my-eg'.");
        sb.append("\n\n\tjava -jar build/libs/create-vpc.jar my-eg delete");
        sb.append("\n\nwill delete those same resources.");
        System.out.println(sb);
    }

    private VpcInfo execute() {

        createCluster(ecsClient, id);

        String vpcId = NewCreateVpc.createVpc(ec2Client, idTag);

        Map<String, String> subnets = NewCreateSubnet.getSubnetId(ec2Client, vpcId, idTag);

        String accessSecurityGroup = NewCreateSecurityGroups.getPublicSecurityGroup(ec2Client, vpcId, idTag, PUBLIC_TAG);

        String privateSecurityGroup = NewCreateSecurityGroups.getPrivateSecurityGroup(ec2Client, vpcId, idTag, PRIVATE_TAG);

        String roleName = NewCreateProfiles.getEcsRole(iamClient, id);

        String instanceProfileArn = NewCreateProfiles.getInstanceProfile(iamClient, roleName, id);

        String keyName = NewCreateKeyPairs.createAndWriteKeyPair(ec2Client, id);

        String publicSubnetId = subnets.values().iterator().next();  // to pick whichever comes up

        String internetGatewayId = NewCreateRouteTables.createAndAttachInternetGateway(ec2Client, vpcId, idTag);

        NewCreateRouteTables.createAndAttachInternetRouteTable(ec2Client, vpcId, internetGatewayId, publicSubnetId, idTag, PUBLIC_TAG);

        String publicInstanceId = NewCreateInstances.createAccessInstance(ec2Client, subnets, accessSecurityGroup, keyName, idTag, PUBLIC_TAG);

        NewCreateRouteTables.updateMainRouteTableForNat(ec2Client, vpcId, publicInstanceId);

        List<Reservation> priv = NewCreateInstances.createEcsInstances(ec2Client, id, subnets, privateSecurityGroup, instanceProfileArn, keyName, idTag, PRIVATE_TAG);

        System.out.print("\nGathering details");

        String publicIp = NewCreateInstances.getPublicIp(ec2Client, publicInstanceId);

        List<String> privateIps = getPrivateIps(priv);

        return new VpcInfo(id, publicIp, privateIps);
    }

    private List<String> getPrivateIps(List<Reservation> priv) {
        return priv.stream()
                .flatMap(r -> r.getInstances().stream())
                .map(i -> i.getNetworkInterfaces().get(0).getPrivateIpAddress())
                .collect(Collectors.toList());
    }

    static void pause(int time) {
        try {
            System.out.print(".");
            Thread.sleep(time);
        } catch (InterruptedException e) {
            // ignore
        }
    }

    private static String createCluster(AmazonECSClient ecsClient, String clusterName) {
        System.out.print("\nCreating ECS cluster");
        return ecsClient.createCluster(
                new CreateClusterRequest()
                        .withClusterName(clusterName))
                .getCluster()
                .getClusterArn();
    }


    static List<Filter> getFiltersForTags(Tag... tags) {
        return Arrays.stream(tags)
                .map(CreateVpc::getFilterForTag)
                .collect(Collectors.toList());
    }

    static Filter getFilterForTag(Tag tag) {
        return new Filter("tag:" + tag.getKey()).withValues(tag.getValue());
    }

    static void tagResources(AmazonEC2Client client, String resource, Tag... tags) {
        client.createTags(
                new CreateTagsRequest()
                        .withResources(resource)
                        .withTags(tags));
    }

    static void tagResources(AmazonEC2Client client, Tag tag, String... resources) {
        client.createTags(
                new CreateTagsRequest()
                        .withResources(resources)
                        .withTags(tag));
    }

    static class DefectException extends RuntimeException {
        DefectException(Exception e) {
            super("Got something wrong, you shouldn't see this.", e);
        }

        DefectException(String s, JsonProcessingException e) {
            super(s, e);
        }
    }

}
