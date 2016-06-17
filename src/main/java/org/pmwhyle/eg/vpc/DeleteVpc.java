package org.pmwhyle.eg.vpc;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.ecs.AmazonECSClient;
import com.amazonaws.services.ecs.model.DeleteClusterRequest;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.identitymanagement.model.*;

import java.util.List;
import java.util.stream.Collectors;

import static org.pmwhyle.eg.vpc.CreateVpc.*;
import static org.pmwhyle.eg.vpc.NewCreateKeyPairs.getPrivateKeyFile;
import static org.pmwhyle.eg.vpc.NewCreateProfiles.pathFor;

/**
 * Delete all the resources associated with the VPC set up through CreateVpc.
 */
class DeleteVpc {

    private final AmazonEC2Client ec2Client;
    private final AmazonIdentityManagementClient iamClient;
    private final AmazonECSClient ecsClient;

    private final Tag idTag;
    private final String id;

    /**
     * An instance that will delete the resources that are identified by the
     * provided id.
     *
     * @param id the identifier of the resources to be deleted
     */
    DeleteVpc(String id) {
        ec2Client = new AmazonEC2Client();
        iamClient = new AmazonIdentityManagementClient();
        ecsClient = new AmazonECSClient();
        idTag = new Tag("eg-id").withValue(id);
        this.id = id;
    }

    /**
     * Proceed and delete the resources identified in this instance.
     */
    void execute() {

        List<String> vpcIds = getVpcIds(ec2Client, idTag);

        deleteInstances(ec2Client, PRIVATE_TAG, idTag);

        List<String> publicInstances = deleteInstances(ec2Client, PUBLIC_TAG, idTag);

        updateMainRouteTableToRemoveNat(ec2Client, vpcIds, publicInstances);

        deleteRouteTables(ec2Client, idTag);

        deleteInternetGateways(ec2Client, idTag);

        deleteKeyPair(ec2Client, id);

        deleteInstanceProfile(iamClient, pathFor(id));

        deleteRole(iamClient, pathFor(id));

        deleteSecurityGroup(ec2Client, idTag);

        deleteSubnets(ec2Client, idTag);

        deleteVpc(ec2Client, vpcIds);

        deleteCluster(ecsClient, id);

        System.exit(0);
    }

    private void deleteCluster(AmazonECSClient ecsClient, String id) {
        ecsClient.deleteCluster(new DeleteClusterRequest().withCluster(id));
    }

    private static void updateMainRouteTableToRemoveNat(AmazonEC2Client client, List<String> vpcIds, List<String> publicInstances) {
        System.out.print("\nRemoving NAT from route table");
        try {
            vpcIds.forEach(vpcId -> client.describeRouteTables(
                    new DescribeRouteTablesRequest()
                            .withFilters(
                                    new Filter("vpc-id").withValues(vpcId),
                                    new Filter("association.main").withValues("true"))
            ).getRouteTables().forEach(routeTable ->
                    client.deleteRoute(new DeleteRouteRequest()
                            .withRouteTableId(routeTable.getRouteTableId())
                            .withDestinationCidrBlock("0.0.0.0/0"))));
        } catch (AmazonServiceException ase) {
            //ignore
        }
    }

    private static void deleteVpc(AmazonEC2Client client, List<String> vpcIds) {
        System.out.print("\nDeleting VPC");
        vpcIds.forEach(vpcId -> client.deleteVpc(new DeleteVpcRequest(vpcId)));
    }

    private static List<String> getVpcIds(AmazonEC2Client client, Tag... tags) {
        return client.describeVpcs(
                new DescribeVpcsRequest()
                        .withFilters(getFiltersForTags(tags)))
                .getVpcs().stream().map(Vpc::getVpcId).collect(Collectors.toList());
    }

    private static void deleteSubnets(AmazonEC2Client client, Tag... tags) {
        System.out.print("\nDeleting subnets");
        client.describeSubnets(
                new DescribeSubnetsRequest()
                        .withFilters(getFiltersForTags(tags)))
                .getSubnets()
                .forEach(sn ->
                        client.deleteSubnet(
                                new DeleteSubnetRequest(sn.getSubnetId())));
    }

    private static void deleteSecurityGroup(AmazonEC2Client client, Tag... tags) {
        System.out.print("\nDeleting security groups");
        int retryCount = 0;
        boolean deleted = false;
        while (!deleted) {
            try {
                client.describeSecurityGroups(
                        new DescribeSecurityGroupsRequest()
                                .withFilters(getFiltersForTags(tags)))
                        .getSecurityGroups()
                        .forEach(sg ->
                                client.deleteSecurityGroup(
                                        new DeleteSecurityGroupRequest()
                                                .withGroupId(sg.getGroupId())));
                deleted = true;
            } catch (AmazonServiceException ase) {
                if (retryCount < 20) {
                    pause(1000);
                    retryCount++;
                } else {
                    throw ase;
                }
            }
        }
    }

    private static void deleteRole(AmazonIdentityManagementClient client, String pathPrefix) {
        System.out.print("\nDeleting Roles");
        client.listRoles(
                new ListRolesRequest()
                        .withPathPrefix(pathPrefix))
                .getRoles()
                .forEach(r -> {
                    client.listAttachedRolePolicies(
                            new ListAttachedRolePoliciesRequest()
                                    .withRoleName(r.getRoleName())).getAttachedPolicies()
                            .forEach(rp -> client.detachRolePolicy(
                                    new DetachRolePolicyRequest()
                                            .withPolicyArn(rp.getPolicyArn())
                                            .withRoleName(r.getRoleName())));
                    client.deleteRole(new DeleteRoleRequest().withRoleName(r.getRoleName()));
                });
    }

    private static void deleteInstanceProfile(AmazonIdentityManagementClient client, String pathPrefix) {
        System.out.print("\nDeleting instance profiles");
        client.listInstanceProfiles(
                new ListInstanceProfilesRequest()
                        .withPathPrefix(pathPrefix))
                .getInstanceProfiles()
                .forEach(ip -> {
                    ip.getRoles().forEach(r ->
                            client.removeRoleFromInstanceProfile(
                                    new RemoveRoleFromInstanceProfileRequest()
                                            .withInstanceProfileName(ip.getInstanceProfileName())
                                            .withRoleName(r.getRoleName())));
                    client.deleteInstanceProfile(
                            new DeleteInstanceProfileRequest()
                                    .withInstanceProfileName(ip.getInstanceProfileName()));
                });
    }

    private static void deleteKeyPair(AmazonEC2Client client, String id) {
        System.out.print("\nDeleting key pairs");
        client.deleteKeyPair(new DeleteKeyPairRequest(id));
        getPrivateKeyFile(id).delete();
    }

    private static void deleteInternetGateways(AmazonEC2Client client, Tag... tags) {
        System.out.print("\nDeleting internet gateway - may take a while");
        int retryCount = 0;
        boolean deleted = false;
        while (!deleted) {
            try {
                client.describeInternetGateways(
                        new DescribeInternetGatewaysRequest()
                                .withFilters(getFiltersForTags(tags)))
                        .getInternetGateways().stream()
                        .forEach(ig -> {
                            ig.getAttachments()
                                    .forEach(at ->
                                            client.detachInternetGateway(
                                                    new DetachInternetGatewayRequest()
                                                            .withVpcId(at.getVpcId())
                                                            .withInternetGatewayId(ig.getInternetGatewayId())));
                            client.deleteInternetGateway(
                                    new DeleteInternetGatewayRequest()
                                            .withInternetGatewayId(ig.getInternetGatewayId()));
                        });
                deleted = true;
            } catch (AmazonServiceException ase) {
                if (retryCount < 20) {
                    pause(5000);
                    retryCount++;
                } else {
                    throw ase;
                }
            }
        }
    }

    private static void deleteRouteTables(AmazonEC2Client client, Tag... tags) {
        client.describeRouteTables(
                new DescribeRouteTablesRequest()
                        .withFilters(getFiltersForTags(tags)))
                .getRouteTables().stream()
                .forEach(rt -> {
                    rt.getAssociations()
                            .forEach(as -> client.disassociateRouteTable(
                                    new DisassociateRouteTableRequest()
                                            .withAssociationId(as.getRouteTableAssociationId())));
                    client.deleteRouteTable(
                            new DeleteRouteTableRequest()
                                    .withRouteTableId(rt.getRouteTableId()));
                });
    }

    private static List<String> deleteInstances(AmazonEC2Client client, Tag... tags) {
        System.out.print("\nDeleting instances");
        List<Filter> filters = getFiltersForTags(tags);
        filters.add(new Filter("instance-state-name").withValues("running", "pending", "stopped", "stopping"));
        List<String> instanceIds = client.describeInstances(
                new DescribeInstancesRequest()
                        .withFilters(filters))
                .getReservations()
                .stream()
                .flatMap(r -> r.getInstances().stream())
                .map(Instance::getInstanceId)
                .collect(Collectors.toList());

        if (!instanceIds.isEmpty()) {
            client.deleteTags(new DeleteTagsRequest(instanceIds).withTags(tags));
            client.terminateInstances(new TerminateInstancesRequest(instanceIds));
        }

        return instanceIds;
    }

}
