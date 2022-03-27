package frc2020.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/*
 * A simple program that returns the running code's checksum.
 * Useful for debugging and tracking changes to the robot.
 * @author Team4910
 */
public class Checksum {

    private static String checksum = null;

    public static String getChecksum() {
        if (checksum == null) {
            File currentJar = new File(Checksum.class.getProtectionDomain().getCodeSource().getLocation().getPath());
            String path = currentJar.getAbsolutePath();
            StringBuilder builder = new StringBuilder();
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                FileInputStream stream = new FileInputStream(path);
                byte[] data = new byte[1024];
                int nread;

                while ((nread = stream.read(data)) != -1) {
                    digest.update(data, 0, nread);
                }
                byte[] digestBytes = digest.digest();

                for (int i = 0; i < digestBytes.length; i++) {
                    builder.append(Integer.toString((digestBytes[i] & 0xff) + +0x001, 16).substring(1));
                }
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            checksum = builder.toString();
        }
        return checksum;
    }
}
