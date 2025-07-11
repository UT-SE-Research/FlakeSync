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
    git clone https://github.com/$slug  input/${slug} > out.txt 
    cd input/${slug}

    git checkout ${commit} > out.txt

    # Build
    mvn install -pl ${module} -am -DskipTests=true > out.txt

    # Run command
    mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:flakefind -Dflakesync.testName=${testname} -pl $module > out.txt    

    # Check that the results are consistent (TBD)
    # Assume expected results are in a known file
    
    errors=0
    while read line_exp; do
        if grep -q ${line_exp} ./${module}/.flakesync/ResultMethods.txt; then 
	   echo ""
	else
	   ((errors++))
        fi
    done < ../../../expected/${slug}/ResultMethods.txt

    if [[ errors -eq 0 ]]; then		  
       echo "${slug} ${test_name} Concurrent Methods: Pass"
    else
       echo "${slug} ${test_name} Concurrent Methods: Fail"
    fi
done < $1
