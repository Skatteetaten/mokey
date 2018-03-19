import org.jetbrains.kotlin.gradle.dsl.Coroutines
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    val kotlinVersion = "1.2.30"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.spring") version kotlinVersion
    id("org.springframework.boot") version "2.0.0.RELEASE"
    id("io.spring.dependency-management") version "1.0.4.RELEASE"
}


buildscript {

    repositories {
        mavenCentral()
        jcenter()
        mavenLocal()
    }

    dependencies {
        classpath("no.skatteetaten.aurora.gradle.plugins:aurora-gradle-plugin:1.2.1")
    }

}

extra["aurora"]= mapOf(
            "requireStaging" to false,
            "applySpockSupport" to false
)

apply {
    plugin("no.skatteetaten.plugins.aurora")
}

group = "no.skatteetaten.aurora"

repositories {
    mavenCentral()
    jcenter()
    mavenLocal()
}

val jar: Jar by tasks
jar.enabled = true

val bootJar: BootJar by tasks
bootJar.enabled = false

springBoot {
    buildInfo()
}

dependencies {
    compile(kotlin("reflect"))
    compile(kotlin("stdlib-jdk8"))
    compile("com.fasterxml.jackson.module:jackson-module-kotlin")
    compile("no.skatteetaten.aurora.springboot:aurora-spring-boot2-starter:1.1.0")
    compile("io.fabric8:openshift-client:3.1.8")
    compile("io.fabric8:kubernetes-model:2.0.4")
    compile("org.springframework.retry:spring-retry")
    compile("org.jetbrains.kotlinx:kotlinx-coroutines-core:0.21.2")
    compile("org.springframework.boot:spring-boot-starter-security")

    testCompile("org.springframework.restdocs:spring-restdocs-mockmvc")
    testCompile("org.springframework.security:spring-security-test")
    testCompile("io.fabric8:openshift-server-mock:3.1.8")
    testCompile("org.springframework.boot:spring-boot-starter-test") {
        exclude(module = "junit")
    }
    testCompile("io.mockk:mockk:1.7.10")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.1.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.1.0")
    // testRuntime("org.junit.jupiter:junit-jupiter-engine:5.1.0")
}

kotlin {
    experimental.coroutines = Coroutines.ENABLE
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget="1.8"
        //freeCompilerArgs = listOf("-Xjsr305=strict")
    }
}

val test by tasks.getting(Test::class) {
    useJUnitPlatform()
}
