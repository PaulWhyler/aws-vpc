package org.pmwhyle.eg.vpc;

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;

import java.util.List;

/**
 */
class NewCreateVpc {

    static String createVpc(AmazonEC2Client client, Tag id) {
        System.out.print("\nCreating VPC with CIDR 10.2.20/26");
        String cidrBlock = "10.2.2.0/26";

        List<Vpc> existing = getExistingVpc(client, id);
        if (existing.size() == 1 && existing.get(0).getCidrBlock().equals(cidrBlock))
            return existing.get(0).getVpcId();

        if (existing.size() != 0)
            throw new ConflictingVpcTagIdExpection(existing, id, cidrBlock);

        CreateVpcResult result = client.createVpc(new CreateVpcRequest(cidrBlock));
        String vpcId = result.getVpc().getVpcId();
        CreateVpc.tagResources(client, id, vpcId);

        return vpcId;
    }

    private static List<Vpc> getExistingVpc(AmazonEC2Client client, Tag vpcTag) {
        return client.describeVpcs(
                new DescribeVpcsRequest()
                        .withFilters(
                                CreateVpc.getFilterForTag(vpcTag)
                        )).getVpcs();
    }

    private static class ConflictingVpcTagIdExpection extends RuntimeException {
        ConflictingVpcTagIdExpection(List existing, Tag tag, String cidrBlock) {
            super("\nEither there are multiple pre-existing VPCs with the requested tag, " +
                    tag + ",\n or there is a single VPC with that tag, but " +
                    "which has a different CIDR block to " + cidrBlock + ".\n" +
                    existing);
        }
    }
}
