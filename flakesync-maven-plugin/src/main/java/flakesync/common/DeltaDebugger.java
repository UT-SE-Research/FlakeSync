/*
The MIT License (MIT)
Copyright (c) 2025 August Shi
Copyright (c) 2025 Shanto Rahman
Copyright (c) 2025 Nandita Jayanthi


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

package flakesync.common;

import java.util.ArrayList;
import java.util.List;

// Utility class for handling general delta debugging
public abstract class DeltaDebugger<T> {

    protected int iterations;   // Keep track of number of iterations the delta debugging went through

    // Core logic for delta debugging, generalized to elements
    public List<T> deltaDebug(final List<T> elements, int granularity) {
        this.iterations++;

        // If n granularity is greater than number of tests, then finished, simply return passed in tests
        if (elements.size() < granularity) {
            return elements;
        }

        // Cut the elements into n equal chunks and try each chunk
        int chunkSize = (int)Math.round((double)(elements.size()) / granularity);
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < elements.size(); i += chunkSize) {
            List<T> chunk = new ArrayList<>();
            List<T> otherChunk = new ArrayList<>();
            // Create chunk starting at this iteration
            int endpoint = Math.min(elements.size(), i + chunkSize);
            chunk.addAll(elements.subList(i, endpoint));

            // Complement chunk are elements before and after this current chunk
            otherChunk.addAll(elements.subList(0, i));
            otherChunk.addAll(elements.subList(endpoint, elements.size()));

            // Try to other, complement chunk first, with theory that valid elements are closer to end
            if (checkValid(otherChunk)) {
                return deltaDebug(otherChunk, 2);   // If works, then delta debug some more the complement chunk
            }
            // Check if running this chunk works
            if (checkValid(chunk)) {
                return deltaDebug(chunk, 2);        // If works, then delta debug some more this chunk
            }
        }
        // If size is equal to number of chunks, we are finished, cannot go down more
        if (elements.size() == granularity) {
            return elements;
        }
        // If not chunk/complement work, increase granularity and try again
        if (elements.size() < granularity * 2) {
            return deltaDebug(elements, elements.size());
        } else {
            return deltaDebug(elements, granularity * 2);
        }
    }

    // Getter method for number of iterations
    public int getIterations() {
        return this.iterations;
    }

    // Method to check if chunks during delta debugging is valid,
    // to be overwritten by subclasses for specific delta debugging tasks
    public abstract boolean checkValid(List<T> elements);
}
