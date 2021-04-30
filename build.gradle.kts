import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.30"
    kotlin("plugin.serialization") version "1.4.30"
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
    mavenCentral()
    jcenter()
    google()
}

application {
    mainClass.set("cn.kherrisan.honeydome.broker.MainKt")
}

val vertxVersion = "4.0.3"

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    //自己写的一些 kt 小轮子
    implementation("cn.kherrisan:kommons:1.0.8")

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

    //vertx
    implementation("io.vertx:vertx-lang-kotlin-coroutines:$vertxVersion")
    implementation("io.vertx:vertx-lang-kotlin:$vertxVersion")
    implementation("io.vertx:vertx-web-client:$vertxVersion")
    implementation("io.vertx:vertx-web:$vertxVersion")
    implementation("io.vertx:vertx-web-openapi:$vertxVersion")

    implementation("org.junit.jupiter:junit-jupiter:5.4.2")

    testImplementation ("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation ("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly ("org.junit.jupiter:junit-jupiter-engine:5.6.0")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

tasks.test {
    useJUnitPlatform()
}
