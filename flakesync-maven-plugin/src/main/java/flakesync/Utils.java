/*
The MIT License (MIT)
Copyright (c) 2025 August Shi
Copyright (c) 2025 Nandita Jayanthi
Copyright (c) 2025 Shanto Rahman



Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

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
            String id = DatatypeConverter.printBase64Binary(
                    MessageDigest.getInstance("SHA-256").digest(Long.toString(System.currentTimeMillis()).getBytes()));
            id = id.replace("/", "");
            id = id.replace("\\", "");
            return id;
        } catch (NoSuchAlgorithmException nsae) {
            return "No_ID";
        }
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
