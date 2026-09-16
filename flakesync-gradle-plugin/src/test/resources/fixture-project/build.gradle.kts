plugins {
    java
    id("edu.utexas.ece.flakesync")
}

// flakesync-core bundles ASM 9.0, which cannot parse class files newer than Java 15 (major
// version 59). Without pinning this, the fixture project compiles to whatever bytecode version
// the JDK running Gradle defaults to, and the agent's bytecode transformation silently no-ops
// (the JVM instrumentation framework swallows a ClassFileTransformer's exception and just uses
// the original, untransformed bytecode) — a failure mode invisible to tests that only assert an
// output file exists rather than that it reflects real instrumentation.
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13.1")
}
