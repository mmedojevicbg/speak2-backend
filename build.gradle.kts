plugins {
    kotlin("jvm") version "2.1.21"
    application
}

group = "com.mmedojevic"
version = "1.0-SNAPSHOT"

val akkaVersion = "2.8.8"

repositories {
    mavenCentral()
    maven {
        url = uri("https://repo.akka.io/maven")
    }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("com.typesafe.akka:akka-actor-testkit-typed_2.13:$akkaVersion")
    testImplementation("junit:junit:4.13.2")
    implementation("com.typesafe.akka:akka-actor-typed_2.13:$akkaVersion")
    implementation("com.typesafe.akka:akka-stream-typed_2.13:$akkaVersion")
    implementation("com.typesafe.akka:akka-http_2.13:10.5.3")
    implementation("com.typesafe.akka:akka-cluster-typed_2.13:$akkaVersion")
    implementation("com.typesafe.akka:akka-cluster-sharding-typed_2.13:$akkaVersion")
    implementation("com.typesafe.akka:akka-serialization-jackson_2.13:$akkaVersion")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
    implementation("com.datastax.oss:java-driver-core:4.17.0")
    implementation("com.datastax.oss:java-driver-query-builder:4.17.0")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

application {
    applicationDefaultJvmArgs = listOf(
        "--add-opens=java.base/java.nio=ALL-UNNAMED",
        "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED"
    )
}