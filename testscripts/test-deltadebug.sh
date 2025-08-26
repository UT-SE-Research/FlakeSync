#!/bin/bash

if [[ $1 == "" ]]; then
    echo "arg1 - File with list of projects on which to run FlakeSync"
    exit 1
fi


# Overall exit code for the entire test script
exitcode=0
CURRENT_DIR=$(pwd)
while read line; do
    if [[ ${line} =~ ^\# ]]; then
       #echo "line starts with Hash"
       continue
    fi

    # Parse out parts of the line
    slug=$(echo ${line} | cut -d',' -f1)
    commit=$(echo ${line} | cut -d',' -f2)
    module=$(echo ${line} | cut -d',' -f3)
    testname=$(echo ${line} | cut -d',' -f4)

    
    OUTFILE=$(pwd)/out_${testname}.txt
    rm -f ${OUTFILE}
    touch ${OUTFILE}

    EXPECTED_DIR=${CURRENT_DIR}/expected/deltadebug/

    # Clone project into a directory called project
    git clone https://github.com/$slug input/${slug} >> ${OUTFILE}
    cd input/${slug}

    git checkout ${commit} >> ${OUTFILE}

    # Build
    mvn install -pl ${module} -am -DskipTests=true >> ${OUTFILE}

   # Setup the test set of locations needed
   mkdir -p ${module}/.flakesync/
   cp ${EXPECTED_DIR}/${slug}/${testname//#/.}-Locations_all.txt ${module}/.flakesync/${testname//#/.}-Locations.txt
   
   
    if [ -f ./${module}/.flakesync/${testname//#/.}-Locations.txt ]; then
	:
    else 
        echo "ERROR: Missing input file(s)"
    fi


    # Run command
    mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:flakedeltadebug -Dflakesync.testName=${testname} -pl $module >> ${OUTFILE}

    # Check that the results are consistent
    # Assume expected results are in a known file
    errors=0
    
    # First check if Locations.txt was even created 
    if [ -f ./${module}/.flakesync/${testname//#/.}-Locations_minimized.txt ]; then
	:
    else 
        echo "ERROR: Result file not created"
    	((errors++))
    fi
    
    while read line_exp; do
        if grep -q ${line_exp} ./${module}/.flakesync/${testname//#/.}-Locations_minimized.txt; then
           :
        else 
           ((errors++))
        fi
    done < ${EXPECTED_DIR}/${slug}/${testname//#/.}-Locations_minimized.txt


    if [[ errors -eq 0 ]]; then 
       echo "${slug} ${testname} Delta Debug: Pass"
    else 
       echo "${slug} ${testname} Delta Debug: Fail"
       exitcode=1
    fi
done < $1

exit ${exitcode}
