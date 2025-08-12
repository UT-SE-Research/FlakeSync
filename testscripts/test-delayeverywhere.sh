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

   # Setup the smaller set of concurrent methods needed
   mkdir -p ${module}/.flakesync/
   cp ../../../expected/concurrentmethods/${slug}/ResultMethods.txt ${module}/.flakesync/ResultMethods.txt

    # Run command
    mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:flakedelay -Dflakesync.testName=${testname} -pl $module >> ${OUTFILE}

    # Check that the results are consistent
    # Assume expected results are in a known file

    errors=0
    
    if [ -f ./${module}/.flakesync/ResultMethods.txt ]; then
	:
    else 
        echo "ERROR: Missing input file(s)"
    	((errors++))
    fi
    # First check if Locations.txt was even created 
    if [ -f ./${module}/.flakesync/Locations.txt ]; then
	:
    else 
        echo "ERROR: Result file not created"
    	((errors++))
    fi
    
    while read line_exp; do
        if grep -q ${line_exp} ./${module}/.flakesync/Locations.txt; then
           :
        else 
           ((errors++))
        fi
    done < ../../../expected/delaylocs/${slug}/Locations.txt


    if [[ errors -eq 0 ]]; then 
       echo "${slug} ${test_name} Delay Locations: Pass"
    else 
       echo "${slug} ${test_name} Delay Locations: Fail"
       exitcode=1
    fi
done < $1

exit ${exitcode}
