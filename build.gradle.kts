import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.32"
    kotlin("plugin.serialization") version "1.4.32"
    application
}

group = "cn.kherrisan.honeydome"
version = "1.0.0-SNAPSHOT"

repositories {
    maven(url = "https://maven.pkg.github.com/kherrisan/kommons") {
        credentials {
            username = "kherrisan"
            password = "6a5cad077e72fd4ef2a774e24ae3d28dbdea1b7f"
        }
    }
    maven {
        url = uri("https://plugins.gradle.org/m2/")
    }
    mavenCentral()
    jcenter()
    google()
}

application {
    mainClass.set("cn.kherrisan.honeydome.broker.MainKt")
}

val vertxVersion = "4.0.3"
val protocVersion = "3.15.8"
val grpcVersion = "1.37.0"
val grpcKotlinVersion = "1.0.0"

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.4.20")

    //自己写的一些 kt 小轮子
    implementation("cn.kherrisan:kommons:1.0.8")

    //grpc
    implementation("io.grpc:grpc-protobuf:${grpcVersion}")
    implementation("io.grpc:grpc-kotlin-stub:${grpcKotlinVersion}")
    implementation("io.grpc:grpc-netty:${grpcVersion}")

    // logger
    implementation("org.apache.logging.log4j:log4j-api:2.14.1")
    implementation("org.apache.logging.log4j:log4j-core:2.14.1")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.14.1")
    implementation("org.slf4j:slf4j-api:1.7.25")

    // json(gson)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.1.0")
    implementation("com.google.code.gson:gson:2.8.6")

    // kmongo
    implementation("org.litote.kmongo:kmongo-serialization:4.2.5")
    implementation("org.litote.kmongo:kmongo-coroutine-serialization:4.2.5")
    implementation("org.litote.kmongo:kmongo:4.2.5")

    // vertx
    implementation("io.vertx:vertx-lang-kotlin-coroutines:$vertxVersion")
    implementation("io.vertx:vertx-lang-kotlin:$vertxVersion")
    implementation("io.vertx:vertx-web-client:$vertxVersion")
    implementation("io.vertx:vertx-web:$vertxVersion")
    implementation("io.vertx:vertx-web-openapi:$vertxVersion")
    implementation("io.vertx:vertx-grpc:$vertxVersion")

    implementation("org.junit.jupiter:junit-jupiter:5.4.2")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.6.0")
}

tasks.withType<Wrapper> {
    gradleVersion = "6.7.1"
    distributionType = Wrapper.DistributionType.BIN
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

tasks.test {
    useJUnitPlatform()
}
