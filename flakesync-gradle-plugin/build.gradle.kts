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
}

tasks.test {
    useJUnitPlatform()
}

gradlePlugin {
    plugins {
        create("flakesync") {
            id = "edu.utexas.ece.flakesync"
            implementationClass = "edu.utexas.ece.flakesync.gradle.FlakeSyncPlugin"
        }
    }
}
