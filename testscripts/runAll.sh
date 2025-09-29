currentDir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

results="results"

if [ ! -d "$currentDir/$results" ]
then
    mkdir "$currentDir/$results"
fi

echo "slug,sha,test_name,time_to_get_conc_meth,time_to_delay_inject,time_to_do_delta,time_to_crit_search,time_to_do_barrier_search,time_to_do patch" > "$currentDir/$results/Time_Result.csv"
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

     ### Concurrent Method Find
    start_time=$(date +%s.%N)
    mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:concurrentfind -Dflakesync.testName=${test_name} -pl $module
    end_time=$(date +%s.%N)
    duration_ns_conc_meth=$(echo "scale=2; ${end_time} - ${start_time}" | bc)

    ### Delay All Locations
    start_time=$(date +%s.%N)
    mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:delaylocs -Dflakesync.testName=${test_name} -pl $module
    end_time=$(date +%s.%N)
    duration_ns_delay_inject=$(echo "scale=2; ${end_time} - ${start_time}" | bc)

    ### Delta Debugger
    start_time=$(date +%s.%N)
    mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:deltadebug -Dflakesync.testName=${test_name} -pl $module
    end_time=$(date +%s.%N)
    duration_ns_dd=$(echo "scale=2; ${end_time} - ${start_time}" | bc)

    #### Root Method and Critical Point Search
    start_time=$(date +%s.%N)
    mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:critsearch -Dflakesync.testName=${test_name} -pl $module
    end_time=$(date +%s.%N)
    duration_ns_critsearch=$(echo "scale=2; ${end_time} - ${start_time}" | bc)

    ### Barrier Point Search
    start_time=$(date +%s.%N)
    mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:barrierpointsearch -Dflakesync.testName=${test_name} -pl $module
    end_time=$(date +%s.%N)
    duration_ns_barrierpointsearch=$(echo "scale=2; ${end_time} - ${start_time}" | bc)

    ####Patching
    start_time=$(date +%s.%N)
    #mvn edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT:patch -Dflakesync.testName=${test_name} -pl $module
    end_time=$(date +%s.%N)
    duration_ns_patch=$(echo "scale=2; ${end_time} - ${start_time}" | bc)

    end_time=$(date +%s%N) # Capture nanoseconds for end time of plugin

    duration_ns=$(echo "scale=2; ${end_time} - ${start_time}" | bc)


    #echo "End-to-end plugin execution duration: ${duration_ms}ms"
    echo "End-to-end duration:  ${duration_ns}"
    echo "Duration of each mojo:"
    echo "Concurrent method finder: $duration_ns_conc_meth"
    echo "Delay at all locations: $duration_ns_delay_inject"
    echo "Delta Debugger: $duration_ns_dd"
    echo "Critical Point Search: $duration_ns_critsearch"
    echo "Barrier Point Search: $duration_ns_barrierpointsearch"
    echo "Patching: $duration_ns_patch"

    echo "$slug,$sha,$test_name,$duration_ns_conc_meth,$duration_ns_delay_inject,$duration_ns_dd,$duration_ns_critsearch,$duration_ns_barrierpointsearch,$duration_ns_patch" >> "$currentDir/$results/Time_Result.csv"

    cd ../../..

done < $1

