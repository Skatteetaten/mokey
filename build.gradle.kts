import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    var kotlin_version: String by extra
    kotlin_version = "1.2.30"

    repositories {
        mavenCentral()
        jcenter()
        mavenLocal()
    }

    dependencies {
        classpath(kotlin("gradle-plugin", kotlin_version))
        classpath(kotlin("noarg", kotlin_version))
        classpath(kotlin("allopen", kotlin_version))
        classpath("org.springframework.boot:spring-boot-gradle-plugin:2.0.0.RELEASE")
        classpath("no.skatteetaten.aurora.gradle.plugins:aurora-gradle-plugin:1.2.1")
    }
}

apply {
    plugin("kotlin")
    plugin("kotlin-spring")
    plugin("org.springframework.boot")
    plugin("io.spring.dependency-management")
    plugin("no.skatteetaten.plugins.aurora")
}

plugins {
    java
}

val kotlin_version: String by extra

group = "no.skatteetaten.aurora"

repositories {
    mavenCentral()
    jcenter()
    mavenLocal()
}

dependencies {
    compile(kotlin("reflect", kotlin_version))
    compile(kotlin("stdlib-jdk8", kotlin_version))
    compile("com.fasterxml.jackson.module:jackson-module-kotlin")
    compile("no.skatteetaten.aurora.springboot:aurora-spring-boot2-starter:1.0.0")
    compile("io.fabric8:openshift-client:3.1.8")
    compile("io.fabric8:kubernetes-model:2.0.4")
    compile("org.springframework.retry:spring-retry")
    compile("org.jetbrains.kotlinx:kotlinx-coroutines-core:0.21.2")
    compile("org.springframework.boot:spring-boot-starter-security")

    testCompile("org.springframework.restdocs:spring-restdocs-mockmvc")
    testCompile("org.springframework.security:spring-security-test")
    testCompile("org.junit.jupiter:junit-jupiter-api:5.1.0")
    testCompile("io.fabric8:openshift-server-mock:3.1.8")
    testCompile("org.springframework.boot:spring-boot-starter-test")
    testCompile("io.mockk:mockk:1.7.10")

    testRuntime("org.junit.jupiter:junit-jupiter-engine:5.1.0")
}


tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

val test by tasks.getting(Test::class) {
    useJUnitPlatform()
}
