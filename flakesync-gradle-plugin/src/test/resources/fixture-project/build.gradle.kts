plugins {
    java
    id("edu.utexas.ece.flakesync")
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13.1")
}
