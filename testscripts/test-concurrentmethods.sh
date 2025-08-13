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

    # Clone project into a directory called project
    git clone https://github.com/$slug input/${slug} >> ${OUTFILE}
    cd input/${slug}

    git checkout ${commit} >> ${OUTFILE}

    # Build
    mvn install -pl ${module} -am -DskipTests=true >> ${OUTFILE}

    # Run command
    mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:concurrentfind -Dflakesync.testName=${testname} -pl $module >> ${OUTFILE}

    # Check that the results are consistent
    # Assume expected results are in a known file

    errors=0
    # First check if ResultMethods.txt was even created 
    if [ -f ./${module}/.flakesync/${testname//#/.}-ResultMethods.txt ]; then
	#cat ./${module}/.flakesync/ResultMethods.txt
        :
    else 
        echo "ERROR: Result file not created"
    	((errors++))
    fi
 
    while read line_exp; do
        if grep -q -e "${line_exp//\[/\\\[.*}" ./${module}/.flakesync/${testname//#/.}-ResultMethods.txt; then
            :
        else
            ((errors++))
            echo ${line_exp}
        fi
    done < ../../../expected/concurrentmethods/${slug}/${testname//#/.}-ResultMethods.txt

    if [[ errors -eq 0 ]]; then
       echo "${slug} ${testname} Concurrent Methods: Pass"
    else
       echo "${slug} ${testname} Concurrent Methods: Fail"
       exitcode=1
    fi
    
    cd ${CURRENT_DIR}
done < $1

exit ${exitcode}
