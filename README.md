# FlakeSync

## Using FlakeSync
* Run the four phases(6 goals total) of the Flakesync tool on your desired maven project to identify the sources of flakiness for a given flaky test
* Each phase will produce output files that will be listed under the following directory: `./<repo>/<module>/.flakesync/`

### Setup
* Can be built on Java 8+
* Run ```mvn clean install -U in test project```

### Running Delay Injection and Location Minimization 
To run step 1 which creates a list of concurrent methods, run:
* Find concurrent methods
  * ex. ```mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:concurrentfind -Dflakesync.testName=org.apache.uniffle.common.rpc.GrpcServerTest#testGrpcExecutorPool -pl common```
    * This generates two files:
      *  ResultMethods.txt: List of concurrent methods
* Get list of locations
  * ex. ```mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:delaylocs -Dflakesync.testName=org.apache.uniffle.common.rpc.GrpcServerTest#testGrpcExecutorPool -pl common```
    * This generates one file:
      * Locations.txt: List of locations where delays can be injected for the test to fail
For running step 2 which minimizes the list of locations, run:
* Delta-debugging to minimize list of locations
  * This will cut down the list of locations by getting the minimum subset of locations required for a test failure
  * ex. ```mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:deltadebug -Dflakesync.testName=org.apache.uniffle.common.rpc.GrpcServerTest#testGrpcExecutorPool -pl common```
### Critical Point Search
* Run critical point and root method search
  * ex. ```mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:critsearch -Dflakesync.testName=org.apache.uniffle.common.rpc.GrpcServerTest#testGrpcExecutorPool -pl common```
  * This will generate a list of root methods and a list of critical points
    * RootMethods.csv
    * CriticalPoints.csv
### Barrier Point Search
* Run barrier point search
  * ex. ```mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:barrierpointsearch -Dflakesync.testName=org.apache.uniffle.common.rpc.GrpcServerTest#testGrpcExecutorPool -pl common```
  * This will generate the list of barrier points, linking them to the critical points found earlier
    * BarrierPoints.csv
### Patcher
* Run patcher
  * This will generate the patch files for files containing the critical points, barrier points, and test methods. These patch files can be applied to their counterparts to repair the source of flakiness.
    * These patch files will be named: \<original class name>.patch, and they will be created at `./<repo>/.flakesync/patch/`
  * ex. ```mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:patch -Dflakesync.testName=org.apache.uniffle.common.rpc.GrpcServerTest#testGrpcExecutorPool -pl common```

...

