# FlakeSync

## Using FlakeSync

### Setup
* Run ```mvn clean install -U in test project``` 

### Running Delay Injection and Location Minimization
For running step 1 which creates a list of concurrent methods and the thread count, run:
* Find concurrent methods
  * ```mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:flakefind -Dflakesync.testName=org.apache.uniffle.common.rpc.GrpcServerTest#testGrpcExecutorPool -pl common```
    * This generates two files:
      *  ResultMethods.txt: List of concurrent methods
      *  ThreadCountsList.txt: # of threads
* Get list of locations
  * ```mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:flakedelay -Dflakesync.testName=org.apache.uniffle.common.rpc.GrpcServerTest#testGrpcExecutorPool -pl common```
    * This generates one file:
      * Locations.txt: List of locations that delays can be injected for the test to fail
* Run delta-debugging to minimize list of locations
  * ```mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:flakedeltadebug -Dflakesync.testName=org.apache.uniffle.common.rpc.GrpcServerTest#testGrpcExecutorPool -pl common```


## Contributing to FlakeSync 
...

