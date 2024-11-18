#!/bin/bash

REPOS=$(cut -d, -f1 inputs.csv)
delimiter=" "
declare -a SHAS=($(cut -d, -f2 inputs.csv | tr "$delimiter" "\n"))
declare -a MODULES=($(cut -d, -f3 inputs.csv | tr "$delimiter" "\n"))
declare -a TESTS=($(cut -d, -f4 inputs.csv | tr "$delimiter" "\n"))

i=0
for repo in $REPOS
do
    ###Clone the repository and move to repository directory
    REPO_DIR=$(echo $repo | cut -d'/' -f2)
    git clone https://github.com/$repo
    #echo $REPO_DIR
    cd $REPO_DIR

    ###Checkout required SHA for testing repo
    #echo ${SHAS[$i]}
    git checkout ${SHAS[$i]}

    ###Run the test with flakesync plugin
    #echo ${TESTS[$i]}
    #echo ${MODULES[$i]}
    mvn clean install -DskipTests -pl ${MODULES[$i]} -am -Dmaven.javadoc.skip=true -Dcheckstyle.skip
    mvn sample.plugin:flake-sync-plugin:1.0-SNAPSHOT:flakesync -Dflakesync.testName=${TESTS[$i]} -pl ${MODULES[$i]} -am

    cd ..
    i=$((i+1))
done