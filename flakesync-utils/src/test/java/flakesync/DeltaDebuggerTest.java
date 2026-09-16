package flakesync;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class DeltaDebuggerTest {

    @Test
    public void minimizesToTheSingleRequiredElement() {
        List<String> elements = new ArrayList<>(Arrays.asList("a", "b", "c", "culprit", "d"));

        DeltaDebugger<String> debugger = new DeltaDebugger<String>() {
            @Override
            public boolean checkValid(List<String> chunk) {
                return chunk.contains("culprit");
            }
        };

        List<String> result = debugger.deltaDebug(elements, 2);

        assertEquals(Arrays.asList("culprit"), result);
    }
}
