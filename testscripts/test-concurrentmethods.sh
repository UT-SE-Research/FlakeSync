#!/bin/bash

if [[ $1 == "" ]]; then
    echo "arg1 - File with list of projects on which to run FlakeSync"
    exit 1
fi

OUTFILE=$(pwd)/out.txt

rm -f ${OUTFILE}
touch ${OUTFILE}

# Overall exit code for the entire test script
exitcode=0

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

    # Clone project into a directory called project
    git clone https://github.com/$slug input/${slug} >> ${OUTFILE}
    cd input/${slug}

    git checkout ${commit} >> ${OUTFILE}

    # Build
    mvn install -pl ${module} -am -DskipTests=true >> ${OUTFILE}

    # Run command
    mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:concurrentfind -Dflakesync.testName=${testname} -pl $module >> ${OUTFILE}

    # Check that the results are consistent (TBD)
    # Assume expected results are in a known file

    errors=0
    while read line_exp; do
        if grep -q ${line_exp} ./${module}/.flakesync/ResultMethods.txt; then
            echo ""
        else
            ((errors++))
        fi
    done < ../../../expected/concurrentmethods/${slug}/ResultMethods.txt

    if [[ errors -eq 0 ]]; then
       echo "${slug} ${test_name} Concurrent Methods: Pass"
    else
       echo "${slug} ${test_name} Concurrent Methods: Fail"
       exitcode=1
    fi
done < $1

exit ${exitcode}
