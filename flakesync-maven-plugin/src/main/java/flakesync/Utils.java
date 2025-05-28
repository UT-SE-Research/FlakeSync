package flakesync;

import flakesync.common.ConfigurationDefaults;
import flakesync.common.Level;
import flakesync.common.Logger;
import jakarta.xml.bind.DatatypeConverter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;


public class Utils {

    public static Throwable linkException(Throwable nestedThrowable, Throwable pastSupressedException) {
        if (pastSupressedException == null) {
            return nestedThrowable;
        }
        if (nestedThrowable == null) {
            return pastSupressedException;
        }
        pastSupressedException.addSuppressed(nestedThrowable);
        return pastSupressedException;
    }

    public static String getFreshExecutionId() {
        try {
            // TODO(gyori): Fix to check that the id was not used before in the
            // .nondex (?)
            String id = DatatypeConverter.printBase64Binary(
                    MessageDigest.getInstance("SHA-256").digest(Long.toString(System.currentTimeMillis()).getBytes()));
            id = id.replace("/", "");
            id = id.replace("\\", "");
            return id;
        } catch (NoSuchAlgorithmException nsae) {
            return "No_ID";
        }
    }

    public static boolean checkJDKBefore8() {
        return System.getProperty("java.version").startsWith("1.");
    }

    public static boolean checkJDK8() {
        return System.getProperty("java.version").startsWith("1.8");
    }

    public static Path getRtJarLocation() {
        String javaHome = System.getProperty("java.home");
        if (javaHome == null) {
            Logger.getGlobal().log(Level.SEVERE, "JAVA_HOME is not set!");
            throw new IllegalStateException("JAVA_HOME is not set!");
        }


        Path pathToRt = Paths.get(javaHome, "jre", "lib", "rt.jar");
        Logger.getGlobal().log(Level.FINE, pathToRt.toString());
        if (Files.exists(pathToRt)) {
            return pathToRt;
        }

        pathToRt = Paths.get(javaHome, "lib", "rt.jar");
        Logger.getGlobal().log(Level.FINE, pathToRt.toString());
        if (Files.exists(pathToRt)) {
            return pathToRt;
        }

        return null;
    }
}
