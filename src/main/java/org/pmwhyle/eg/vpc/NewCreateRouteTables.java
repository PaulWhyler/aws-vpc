package org.pmwhyle.eg.vpc;

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;

import java.util.List;
import java.util.Optional;

import static org.pmwhyle.eg.vpc.CreateVpc.pause;

/**
 */
public class NewCreateRouteTables {

    static String createAndAttachInternetGateway(AmazonEC2Client client, String vpcId, Tag tag) {
        System.out.print("\nCreating internet gateway");
        List<InternetGateway> internetGateways = client.describeInternetGateways(new DescribeInternetGatewaysRequest().withFilters(CreateVpc.getFilterForTag(tag))).getInternetGateways();
        if (internetGateways.size() == 1)
            return internetGateways.get(0).getInternetGatewayId();

        String internetGatewayId = client.createInternetGateway().getInternetGateway().getInternetGatewayId();

        CreateVpc.tagResources(client, tag, internetGatewayId);

        client.attachInternetGateway(
                new AttachInternetGatewayRequest()
                        .withInternetGatewayId(internetGatewayId)
                        .withVpcId(vpcId));

        return internetGatewayId;
    }

    static String createAndAttachInternetRouteTable(AmazonEC2Client client, String vpcId, String internetGatewayId, String publicSubnetId, Tag... tags) {
        System.out.print("\nCreating route table");
        List<RouteTable> existing = client.describeRouteTables(new DescribeRouteTablesRequest().withFilters(CreateVpc.getFiltersForTags(tags))).getRouteTables();
        if (existing.size() == 1)
            return existing.get(0).getRouteTableId();

        String routeTableId = client.createRouteTable(
                new CreateRouteTableRequest()
                        .withVpcId(vpcId))
                .getRouteTable()
                .getRouteTableId();

        CreateVpc.tagResources(client, routeTableId, tags);

        client.createRoute(
                new CreateRouteRequest()
                        .withRouteTableId(routeTableId)
                        .withGatewayId(internetGatewayId)
                        .withDestinationCidrBlock("0.0.0.0/0"));

        client.associateRouteTable(new AssociateRouteTableRequest().withRouteTableId(routeTableId).withSubnetId(publicSubnetId));

        return routeTableId;
    }

    static void updateMainRouteTableForNat(AmazonEC2Client client, String vpcId, String natInstanceId) {
        System.out.print("\nAdding NAT to route table");
        RouteTable mainRouteTable = client.describeRouteTables(
                new DescribeRouteTablesRequest()
                        .withFilters(
                                new Filter("vpc-id").withValues(vpcId),
                                new Filter("association.main").withValues("true"))).getRouteTables().get(0);
        Optional<Route> natRoute = mainRouteTable.getRoutes().stream()
                .filter(route -> natInstanceId.equals(route.getInstanceId()))
                .findFirst();

        if (natRoute.isPresent())
            return;

        String status = null;
        long startTime = System.currentTimeMillis();
        while (!"running".equals(status) && (System.currentTimeMillis() - startTime < 60000)) {
            List<InstanceStatus> instanceStatuses = client.describeInstanceStatus(
                    new DescribeInstanceStatusRequest()
                            .withInstanceIds(natInstanceId))
                    .getInstanceStatuses();

            if (instanceStatuses.size() > 0) {
                status = instanceStatuses.get(0)
                        .getInstanceState()
                        .getName();
            }
            pause(1000);
        }

        client.createRoute(
                new CreateRouteRequest()
                        .withRouteTableId(mainRouteTable.getRouteTableId())
                        .withDestinationCidrBlock("0.0.0.0/0")
                        .withInstanceId(natInstanceId));
    }
}
