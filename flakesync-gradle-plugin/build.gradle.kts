plugins {
    java
    `java-gradle-plugin`
}

group = "edu.utexas.ece"
version = "1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("edu.utexas.ece:flakesync-core:1.0-SNAPSHOT")
    implementation("edu.utexas.ece:flakesync-utils:1.0-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(gradleTestKit())
    testImplementation("junit:junit:4.13.1")
}

tasks.test {
    useJUnitPlatform()
    // These classes exist only to be forked and run by SingleTestExecutor (see
    // SingleTestExecutorTest) — the JUnit4 ones are already invisible to this task since there's
    // no vintage engine on this module's own test classpath, but the JUnit5 ones would otherwise
    // be discovered and run directly by this very task, including the one that deliberately fails.
    exclude("**/fixtures/**")
}

gradlePlugin {
    plugins {
        create("flakesync") {
            id = "edu.utexas.ece.flakesync"
            implementationClass = "edu.utexas.ece.flakesync.gradle.FlakeSyncPlugin"
        }
    }
}
