package flakesync.patching;

import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public class InjectFlagInCriticalPointTest {

    @Test
    public void findsJavaFileByFullyQualifiedClassName() throws IOException {
        TemporaryFolder tmp = new TemporaryFolder();
        tmp.create();
        Path srcDir = tmp.newFolder("src", "main", "java", "pkg").toPath();
        Path javaFile = srcDir.resolve("Sample.java");
        Files.write(javaFile, "package pkg;\nclass Sample {}\n".getBytes());

        Path found = InjectFlagInCriticalPoint.findJavaFilePath(tmp.getRoot().toString(), "pkg.Sample");

        assertEquals(javaFile.toAbsolutePath().normalize(), found.toAbsolutePath().normalize());
        tmp.delete();
    }
}
