git clone https://github.com/apache/incubator-uniffle input/incubator-uniffle
cd input/incubator-uniffle 
git checkout a2b9c17b
mvn clean install -U -DskipTests=true
#find . -name "*.class" | grep -v Tests | sed 's;.*target/classes/;;'| sed 's;/;.;g' | sed 's;.class$;;' > common/.flakesync/whitelist.txt
mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:flakefind -Dflakesync.testName=org.apache.uniffle.common.rpc.GrpcServerTest#testGrpcExecutorPool -pl common
mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:flakedelay -Dflakesync.testName=org.apache.uniffle.common.rpc.GrpcServerTest#testGrpcExecutorPool -pl common
mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:flakedeltadebug -Dflakesync.testName=org.apache.uniffle.common.rpc.GrpcServerTest#testGrpcExecutorPool -pl common
mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:critsearch -Dflakesync.testName=org.apache.uniffle.common.rpc.GrpcServerTest#testGrpcExecutorPool -pl common
mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:barrierpointsearch -Dflakesync.testName=org.apache.uniffle.common.rpc.GrpcServerTest#testGrpcExecutorPool -pl common
