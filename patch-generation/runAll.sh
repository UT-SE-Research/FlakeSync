#bash runAll.sh org.java_websocket.issues.Issue677Test#121 org/java_websocket/WebSocketImpl#513~org/java_websocket/WebSocketImpl#515[100] testIssue
currentDir=$(pwd)
inputProj="$currentDir/projects"
logs="$currentDir/logs"
mkdir $inputProj
mkdir $logs
while read line 
    do
    slug=$(echo $line | cut -d',' -f1)
    sha=$(echo $line | cut -d',' -f2)
    module=$(echo $line | cut -d',' -f3)
    rootProj=$(echo "$slug" | cut -d/ -f 1)
    subProj=$(echo "$slug" | cut -d/ -f 2)

    if [[ ! -d ${inputProj}/${rootProj} ]]; then
        git clone "https://github.com/$slug" $inputProj/$slug
    fi
    
    cd $inputProj/$slug
    git checkout ${sha}
    cd $currentDir
    
    full_test=$(echo $line | cut -d',' -f4)
    critical_point=$(echo $line | cut -d',' -f5) #It can be multiple (multiples are seperated by semicolon) or single one
    single_critic_point=$(echo $critical_point | cut -d';' -f1)
    echo "single_critic_point =$single_critic_point"
    #exit

    barrier_point=$(echo $line | cut -d',' -f6)
    threshold=$(echo $line | cut -d',' -f7)

    class_name=$(echo ${single_critic_point} | cut -d'~' -f2 | cut -d'#' -f1 | cut -d'$' -f1)
    class_name_dotted=${class_name//\//.}
    echo $class_name_dotted
    #exit
    test_name=$(echo $full_test | cut -d'#' -f2)
    echo $class_name_dotted
    javac InjectYieldStatement.java
    # Find barrier class name in dot notation
    barrier_class_name=$(echo ${barrier_point} | cut -d'#' -f1 | sed 's/\//./g')
    # Find critic class name in dot notation
    critic_class_name=$(echo ${single_critic_point} | cut -d'~' -f2 | cut -d'#' -f1 | cut -d'$' -f1 | sed 's/\//./g')
    # Find barrier file path
    barrier_file_path=$(find $inputProj/$slug -type f -name "$(basename ${barrier_class_name//./\/}).java" | head -n 1)
    cp "$barrier_file_path" "$barrier_file_path.orig"
    echo "java InjectYieldStatement ${barrier_point} ${class_name_dotted} ${test_name} $inputProj/$slug/"
    java InjectYieldStatement ${barrier_point} "${class_name_dotted}" "${test_name}" "$inputProj/$slug/"
    #exit
    javac  -cp .:javaparser-core-3.25.4.jar InjectFlagInCriticalPoint.java
    # Find critic file path
    critic_file_path=$(find $inputProj/$slug -type f -name "$(basename ${critic_class_name//./\/}).java" | head -n 1)
    cp "$critic_file_path" "$critic_file_path.orig"
    echo "java  -cp .:javaparser-core-3.25.4.jar InjectFlagInCriticalPoint ${single_critic_point} $inputProj/$slug"
    java -cp .:javaparser-core-3.25.4.jar InjectFlagInCriticalPoint ${single_critic_point} "$inputProj/$slug"
    javac SavePatch.java
    # Generate patch for both files in one call
    echo "java SavePatch $barrier_class_name $barrier_class_name $critic_class_name $critic_class_name $inputProj/$slug"
    java SavePatch "$barrier_class_name" "$barrier_class_name" "$critic_class_name" "$critic_class_name" "$inputProj/$slug"
    #exit
    
    cd "$inputProj/$slug"
    mvn clean install -DskipTests
    timeout 2m mvn test -Dtest=${full_test} >> "$logs/${full_test}_patch.csv"
    #exit
    git stash

    #exit
    #org.java_websocket.issues.Issue677Test#testIssue
done < $1
