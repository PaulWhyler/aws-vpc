package org.pmwhyle.eg.vpc;

import java.util.List;

/**
 */
public class VpcInfo {

    private final String id;
    private final String publicIp;
    private final List<String> privateIps;

    public VpcInfo(String id, String publicIp, List<String> privateIps) {
        this.id = id;
        this.publicIp = publicIp;
        this.privateIps = privateIps;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Created a VPC with the following details.");
        sb.append("\n\n\t* Identifier tag, with tag key 'eg-id', is '").append(id).append("'.");
        sb.append("\n\t* Public IP address of bastian instance, through which all ");
        sb.append("SSH access is made, is ").append("\n\t\t").append(publicIp).append(".");
        sb.append("\n\t* ").append(privateIps.size()).append(" ECS instances have been created, added to ");
        sb.append("  the cluster named '").append(id).append("'. These instances, accessible through the bastion, have the IP addresses");
        privateIps.stream().forEach(p -> sb.append("\n\t\t").append(p));
        sb.append("\n\nTo access the bastion (assuming you're using a Linux desktop), execute the following:");
        sb.append("\n\n\t\tssh-add ~/.ssh/").append(id).append("_id_rsa");
        sb.append("\n\n\tto add the private key generated here to the ssh-agent. You can then access the bastion with");
        sb.append("\n\n\t\tssh -A ec2-user@").append(publicIp);
        sb.append("\n\n\tand from there you can access the private ECS instances.");
        return sb.toString();
    }
}
