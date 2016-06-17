package org.pmwhyle.eg.vpc;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;

import java.util.*;

/**
 */
class NewCreateSubnet {

    static Map<String, String> getSubnetId(AmazonEC2Client client, String vpcId, Tag tag) {
        System.out.print("\nCreating subnets");
        Map<String, String> subnetsByZone = new HashMap<>();
        List<String> cidrBlocks = Arrays.asList("10.2.2.0/28", "10.2.2.16/28", "10.2.2.32/28", "10.2.2.48/28");
        List<AvailabilityZone> availabilityZones = client.describeAvailabilityZones().getAvailabilityZones();
        for (int i = 0; i < availabilityZones.size(); i++) {
            String zoneName = availabilityZones.get(i).getZoneName();
            subnetsByZone.put(zoneName, createSubnetFor(client, vpcId, zoneName, cidrBlocks.get(i), tag));
        }
        return subnetsByZone;
    }

    private static String createSubnetFor(AmazonEC2Client client, String vpcId, String zoneName, String cidrBlock, Tag tag) {

        List<Subnet> existing = getExistingSubnet(client, zoneName, cidrBlock, vpcId, tag);

        if (!(existing.isEmpty()))
            return existing.get(0).getSubnetId();

        CreateSubnetResult result;
        try {
            result = client.createSubnet(
                    new CreateSubnetRequest()
                            .withVpcId(vpcId)
                            .withAvailabilityZone(zoneName)
                            .withCidrBlock(cidrBlock));
        } catch (AmazonServiceException ase) {
            throw new ConflictingSubnetTagIdExpection(cidrBlock, vpcId, ase);
        }
        String subnetId = result.getSubnet().getSubnetId();
        CreateVpc.tagResources(client, subnetId, tag);

        return subnetId;
    }

    private static List<Subnet> getExistingSubnet(AmazonEC2Client client, String zoneName, String cidrBlock, String vpcId, Tag tag) {
        return client.describeSubnets(
                new DescribeSubnetsRequest()
                        .withFilters(
                                CreateVpc.getFilterForTag(tag),
                                new Filter("vpc-id").withValues(vpcId),
                                new Filter("availabilityZone").withValues(zoneName),
                                new Filter("cidrBlock").withValues(cidrBlock)
                        )).getSubnets();
    }


    private static class ConflictingSubnetTagIdExpection extends RuntimeException {
        ConflictingSubnetTagIdExpection(String cidrBlock, String vpcId, AmazonServiceException ase) {
            super("\nIn VPC " + vpcId + ", there is already a subnet that conflicts with CIDR " + cidrBlock, ase);
        }
    }
}
