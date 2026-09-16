package fixture;

import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FlakySampleTest {

    @Test
    public void alwaysPasses() {
        assertTrue(true);
    }

    @Test
    public void alwaysFails() {
        fail("intentionally failing fixture, used to drive FlakeSync task integration tests");
    }
}
