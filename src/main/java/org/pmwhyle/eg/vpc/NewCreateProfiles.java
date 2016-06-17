package org.pmwhyle.eg.vpc;

import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.identitymanagement.model.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;

/**
 */
class NewCreateProfiles {

    private static final String ECS_ROLE_ARN =
            "arn:aws:iam::aws:policy/service-role/AmazonEC2ContainerServiceforEC2Role";

    private static final String ECS_S3_FULL_ACCESS =
            "arn:aws:iam::aws:policy/AmazonS3FullAccess";

    static String getEcsRole(AmazonIdentityManagementClient iamClient, String id) {
        System.out.print("\nCreating ECS Role");
        Optional<Role> existingRoles = getExistingRoles(iamClient, id);
        if (existingRoles.isPresent())
            return existingRoles.get().getRoleId();

        ObjectMapper mapper = new ObjectMapper();
        Role role;
        try {
            role = iamClient.createRole(
                    new CreateRoleRequest()
                            .withPath(pathFor(id))
                            .withRoleName(roleNameFor(id))
                            .withAssumeRolePolicyDocument(
                                    mapper.writeValueAsString(new RolePolicy())))
                    .getRole();
        } catch (JsonProcessingException e) {
            throw new CreateVpc.DefectException("Couldn't create a string from a hardwired class", e);
        }

        iamClient.attachRolePolicy(
                new AttachRolePolicyRequest()
                        .withPolicyArn(ECS_ROLE_ARN)
                        .withRoleName(role.getRoleName()));

        iamClient.attachRolePolicy(
                new AttachRolePolicyRequest()
                        .withPolicyArn(ECS_S3_FULL_ACCESS)
                        .withRoleName(role.getRoleName()));

        return role.getRoleName();
    }

    private static String roleNameFor(String id) {
        return id + "-ecs";
    }

    static String pathFor(String id) {
        return "/" + id + "/";
    }

    static String getInstanceProfile(AmazonIdentityManagementClient iamClient, String roleName, String id) {
        System.out.print("\nCreating instance profile");
        Optional<InstanceProfile> existing = getExistingInstanceProfile(iamClient, id);

        if (existing.isPresent())
            return existing.get().getArn();

        InstanceProfile instanceProfile = iamClient.createInstanceProfile(
                new CreateInstanceProfileRequest()
                        .withInstanceProfileName(roleNameFor(id))
                        .withPath(pathFor(id))).getInstanceProfile();

        iamClient.addRoleToInstanceProfile(
                new AddRoleToInstanceProfileRequest()
                        .withInstanceProfileName(instanceProfile.getInstanceProfileName())
                        .withRoleName(roleName));

        return instanceProfile.getArn();
    }

    private static Optional<InstanceProfile> getExistingInstanceProfile(AmazonIdentityManagementClient iamClient, String id) {
        return iamClient.listInstanceProfiles(
                new ListInstanceProfilesRequest()
                        .withPathPrefix(pathFor(id)))
                .getInstanceProfiles()
                .stream()
                .filter(instanceProfile -> instanceProfile.getInstanceProfileName().equals(roleNameFor(id)))
                .findFirst();
    }

    private static Optional<Role> getExistingRoles(AmazonIdentityManagementClient iamClient, String id) {
        return iamClient.listRoles(
                new ListRolesRequest().withPathPrefix(pathFor(id)))
                .getRoles()
                .stream()
                .filter(role -> role.getRoleName().equals(roleNameFor(id)))
                .findFirst();
    }
}
