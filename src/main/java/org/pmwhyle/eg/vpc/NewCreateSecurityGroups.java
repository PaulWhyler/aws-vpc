package org.pmwhyle.eg.vpc;

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;
import org.pmwhyle.eg.vpc.CreateVpc.DefectException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 */
class NewCreateSecurityGroups {
    private static final String IP_CHECK_URL = "http://checkip.amazonaws.com/";

    static String getPublicSecurityGroup(AmazonEC2Client client, String vpcId, Tag... tags) {
        System.out.print("\nCreating public security group");
        List<AuthorizeSecurityGroupIngressRequest> ingressRequests = sshIngress(localIp() + "/32");
        ingressRequests.add(new AuthorizeSecurityGroupIngressRequest()
                .withCidrIp("10.2.2.0/26")
                .withFromPort(80)
                .withToPort(80)
                .withIpProtocol("tcp"));
        ingressRequests.add(new AuthorizeSecurityGroupIngressRequest()
                .withCidrIp("10.2.2.0/26")
                .withFromPort(443)
                .withToPort(443)
                .withIpProtocol("tcp"));
        return getSecurityGroup(client, vpcId, "public-access", ingressRequests, tags);
    }

    static String getPrivateSecurityGroup(AmazonEC2Client client, String vpcId, Tag... tags) {
        System.out.print("\nCreating private security group");
        String cidr = client.describeVpcs(
                new DescribeVpcsRequest()
                        .withVpcIds(vpcId))
                .getVpcs()
                .get(0)
                .getCidrBlock();
        return getSecurityGroup(client, vpcId, "private-access", sshIngress(cidr), tags);
    }

    private static List<AuthorizeSecurityGroupIngressRequest> sshIngress(String cidr) {
        List<AuthorizeSecurityGroupIngressRequest> ingress = new ArrayList<>();
        ingress.add(new AuthorizeSecurityGroupIngressRequest()
                .withCidrIp(cidr)
                .withFromPort(22)
                .withToPort(22)
                .withIpProtocol("tcp"));
        return ingress;
    }


    private static String getSecurityGroup(AmazonEC2Client client, String vpcId, String name, List<AuthorizeSecurityGroupIngressRequest> ingressRequests, Tag... tags) {

        List<SecurityGroup> existing = getExistingSecurityGroups(client, vpcId, tags);

        if (existing.size() == 1)
            return existing.get(0).getGroupId();

        String groupId = client.createSecurityGroup(
                new CreateSecurityGroupRequest(
                        name, "For accessing resources from a single public IP Address")
                        .withVpcId(vpcId)).getGroupId();

        ingressRequests.forEach(ingressRequest ->
                client.authorizeSecurityGroupIngress(ingressRequest.withGroupId(groupId)));

        CreateVpc.tagResources(client, groupId, tags);

        return groupId;
    }

    private static List<SecurityGroup> getExistingSecurityGroups(AmazonEC2Client client, String vpcId, Tag... tags) {
        List<Filter> filters = CreateVpc.getFiltersForTags(tags);
        filters.add(new Filter("vpc-id").withValues(vpcId));

        return client.describeSecurityGroups(
                new DescribeSecurityGroupsRequest()
                        .withFilters(filters))
                .getSecurityGroups();
    }

    private static String localIp() {
        String ip;
        URL url;
        try {
            url = new URL(IP_CHECK_URL);
        } catch (MalformedURLException e) {
            throw new DefectException(e);
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()))) {
            ip = br.readLine();
        } catch (IOException e) {
            throw new NoLocalIPAddressFromAWSException("Attempting to retrieve the local IP address from AWS", e);
        }
        return ip;
    }

    private static class NoLocalIPAddressFromAWSException extends RuntimeException {
        NoLocalIPAddressFromAWSException(String s, IOException e) {
            super(s, e);
        }
    }
}
