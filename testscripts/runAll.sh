while read line; do
    if [[ ${line} =~ ^\# ]]; then
       #echo "line starts with Hash"
       continue
    fi
    slug=$(echo $line | cut -d',' -f1 )
    sha=$(echo $line | cut -d',' -f2)
    module=$(echo $line | cut -d',' -f3)
    test_name=$(echo $line | cut -d',' -f4)
    
    git clone https://github.com/$slug input/$slug
    cd input/$slug
    git checkout $sha
    mvn clean install -pl $module -am -U -DskipTests
     
    echo "mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:flakefind -Dflakesync.testName=${test_name} -pl $module"
    #find . -name "*.class" | grep -v Tests | sed 's;.*target/classes/;;'| sed 's;/;.;g' | sed 's;.class$;;' > $module/.flakesync/whitelist.txt
    mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:flakefind -Dflakesync.testName=${test_name} -pl $module
    exit
    mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:flakedelay -Dflakesync.testName=${test_name} -pl $module
    #exit
    mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:flakedeltadebug -Dflakesync.testName=${test_name} -pl $module
    #exit
    mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:critsearch -Dflakesync.testName=${test_name} -pl $module
    #exit
    mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:barrierpointsearch -Dflakesync.testName=${test_name} -pl $module
done < $1

#git clone https://github.com/apache/incubator-uniffle input/incubator-uniffle
#cd input/incubator-uniffle 
#git checkout a2b9c17b
#mvn clean install -U -DskipTests=true
##find . -name "*.class" | grep -v Tests | sed 's;.*target/classes/;;'| sed 's;/;.;g' | sed 's;.class$;;' > common/.flakesync/whitelist.txt
#mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:flakefind -Dflakesync.testName=org.apache.uniffle.common.rpc.GrpcServerTest#testGrpcExecutorPool -pl common
#mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:flakedelay -Dflakesync.testName=org.apache.uniffle.common.rpc.GrpcServerTest#testGrpcExecutorPool -pl common
#mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:flakedeltadebug -Dflakesync.testName=org.apache.uniffle.common.rpc.GrpcServerTest#testGrpcExecutorPool -pl common
#mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:critsearch -Dflakesync.testName=org.apache.uniffle.common.rpc.GrpcServerTest#testGrpcExecutorPool -pl common
#mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:barrierpointsearch -Dflakesync.testName=org.apache.uniffle.common.rpc.GrpcServerTest#testGrpcExecutorPool -pl common


