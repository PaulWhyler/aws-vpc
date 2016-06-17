package org.pmwhyle.eg.vpc;

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

/**
 */
class NewCreateKeyPairs {

    static String createAndWriteKeyPair(AmazonEC2Client client, String id) {
        System.out.print("\nCreating key pair");
        File privateKeyFile = getPrivateKeyFile(id);
        List<KeyPairInfo> keyPairs = client.describeKeyPairs(
                new DescribeKeyPairsRequest()
                        .withFilters(new Filter("key-name").withValues(id)))
                .getKeyPairs();

        if (keyPairs.size() == 1)
            return keyPairs.get(0).getKeyName();

        KeyPair keyPair = client.createKeyPair(new CreateKeyPairRequest(id)).getKeyPair();
        try (FileWriter writer = new FileWriter(privateKeyFile)) {
            writer.write(keyPair.getKeyMaterial());
        } catch (IOException e) {
            client.deleteKeyPair(new DeleteKeyPairRequest(id));
            throw new UnableToWritePrivateKeyException(privateKeyFile.getAbsolutePath(), e);
        }

        try {
            Files.setPosixFilePermissions(Paths.get(privateKeyFile.getAbsolutePath()), PosixFilePermissions.fromString("rw-------"));
        } catch (IOException e) {
            privateKeyFile.setExecutable(false, false);
            privateKeyFile.setReadable(true, true);
            privateKeyFile.setExecutable(false, false);
        }
        return keyPair.getKeyName();
    }

    static File getPrivateKeyFile(String id) {
        return new File(System.getProperty("user.home"), ".ssh/" + id + "_id_rsa");
    }

    private static class UnableToWritePrivateKeyException extends RuntimeException {
        UnableToWritePrivateKeyException(String absolutePath, IOException e) {
            super("Couldn't write Private Key to file " + absolutePath, e);
        }
    }
}
