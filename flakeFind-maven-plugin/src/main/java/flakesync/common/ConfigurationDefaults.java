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

    public static final String PROPERTY_FLAKESYNC_JAR_DIR = "flakesyncJarDir";
    public static final String DEFAULT_FLAKESYNC_JAR_DIR = ".flakesync";

    public static final String FAILURES_FILE = "failures";
    public static final String INVOCATIONS_FILE = "invocations";
    public static final String DEBUG_FILE = "debug";
    public static final String CONFIGURATION_FILE = "config";

    public static final int SEED_FACTOR = 0xA1e4;

    public static final String PROPERTY_LOGGING_LEVEL = "flakesyncLogging";
    public static final String DEFAULT_LOGGING_LEVEL = "CONFIG";
}