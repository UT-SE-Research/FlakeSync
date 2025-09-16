# FlakeSync

## Using FlakeSync
* Run the three phases of the Flakesync tool on your desired maven project to identify the sources of flakiness for a given flaky test
* Each phase will produce output files that will be listed under the following directory: `./<repo>/<module>/.flakesync/`

### Setup
* For now, do not use a Java version past Java 11
* Run ```mvn clean install -U in test project```

### Running Delay Injection and Location Minimization 
For running step 1 which creates a list of concurrent methods and the thread count, run:
* Find concurrent methods
  * ```mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:concurrentfind -Dflakesync.testName=org.apache.uniffle.common.rpc.GrpcServerTest#testGrpcExecutorPool -pl common```
    * This generates two files:
      *  ResultMethods.txt: List of concurrent methods
      * NOTE: For now, you must copy ResultMethods.txt into ResultMethods_tmp.txt before running flakedelay
      *  ThreadCountsList.txt: # of threads
* Get list of locations
  * ```mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:delayeverywhere -Dflakesync.testName=org.apache.uniffle.common.rpc.GrpcServerTest#testGrpcExecutorPool -pl common```
    * This generates one file:
      * Locations.txt: List of locations that delays can be injected for the test to fail
For running step 2 which minimizes the list of locations, run:
* Run delta-debugging to minimize list of locations
  * This will cut down the list of locations by getting the minimum subset of locations required for a test failure
  * ```mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:flakedeltadebug -Dflakesync.testName=org.apache.uniffle.common.rpc.GrpcServerTest#testGrpcExecutorPool -pl common```
### Critical Point Search
* Run critical point and root method search
  *  This will generate a list of root methods
  * ```mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:critsearch -Dflakesync.testName=org.apache.uniffle.common.rpc.GrpcServerTest#testGrpcExecutorPool -pl common```
### Barrier Point Search
* Run barrier point search 
  * This will generate the list of barrier points, linking them to the critical points found earlier
  * ```mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:barrierpointsearch -Dflakesync.testName=org.apache.uniffle.common.rpc.GrpcServerTest#testGrpcExecutorPool -pl common```
## Contributing to FlakeSync 
...

