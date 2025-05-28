/*
The MIT License (MIT)
Copyright (c) 2015 Alex Gyori
Copyright (c) 2022 Kaiyao Ke
Copyright (c) 2015 Owolabi Legunsen
Copyright (c) 2015 Darko Marinov
Copyright (c) 2015 August Shi


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

public class ConfigurationDefaults {

    public static final String PROPERTY_EXECUTION_ID = "flakesyncExecid";
    public static final String NO_EXECUTION_ID = "NoId";
    public static final String PROPERTY_DEFAULT_EXECUTION_ID = ConfigurationDefaults.NO_EXECUTION_ID;

    public static final String PROPERTY_RUN_ID = "flakesyncRunId";
    public static final String LATEST_RUN_ID = "LATEST";
    public static final String PROPERTY_DEFAULT_RUN_ID = ConfigurationDefaults.LATEST_RUN_ID;

    public static final String PROPERTY_FLAKESYNC_DIR = "flakesyncDir";
    public static final String DEFAULT_FLAKESYNC_DIR = ".flakesync";

    //Surefire Arguments
    public static final String CONCURRENT_METHODS_FILE = "ResultMethods.txt";
    public static final String LOCATIONS_FILE = "Locations.txt";
    public static final String WHITELIST_FILE = "whitelist.txt";

    public static final String PROPERTY_FLAKESYNC_JAR_DIR = "flakesyncJarDir";
    public static final String DEFAULT_FLAKESYNC_JAR_DIR = ".flakesync";


    public static final int SEED_FACTOR = 0xA1e4;

    public static final String PROPERTY_LOGGING_LEVEL = "flakesyncLogging";
    public static final String DEFAULT_LOGGING_LEVEL = "CONFIG";

    public static final String CONCURRENT_METHODS_JAR =
        "/edu/utexas/ece/flakesync-core/1.0-SNAPSHOT/flakesync-core-1.0-SNAPSHOT.jar";
    public static final String BOUNDARY_SEARCH_JAR =
        "/edu/utexas/ece/localization-core/0.1-SNAPSHOT/localization-core-0.1-SNAPSHOT.jar";
    public static final String BARRIER_SEARCH_JAR =
        "/edu/utexas/ece/barrierSearch-core/1.0-SNAPSHOT/barrierSearch-core-1.0-SNAPSHOT.jar";
}
