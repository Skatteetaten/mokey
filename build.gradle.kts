buildscript {
    dependencies {
        // must specify this in gradle.properties since the same version must be here and in aurora plugin
        val springCloudContractVersion: String = project.property("aurora.springCloudContractVersion") as String
        classpath("org.springframework.cloud:spring-cloud-contract-gradle-plugin:$springCloudContractVersion")
    }
}

plugins {
    jacoco
    id("org.jetbrains.kotlin.jvm") version "1.3.30"
    id("org.jetbrains.kotlin.plugin.spring") version "1.3.30"
    id("org.jlleitschuh.gradle.ktlint") version "7.3.0"
    id("org.sonarqube") version "2.7.1"

    id("org.springframework.boot") version "2.1.4.RELEASE"
    id("com.gorylenko.gradle-git-properties") version "2.0.0"
    id("com.github.ben-manes.versions") version "0.21.0"
    id("se.patrikerdes.use-latest-versions") version "0.2.9"

    id("no.skatteetaten.gradle.aurora") version "2.2.1"
}

val kotlinVersion = "1.3.30"
val junit5Version = "5.4.1"

apply(plugin = "spring-cloud-contract")

extra["spring-hateoas.version"] = "0.24.0.RELEASE"

dependencies {
    implementation("io.fabric8:openshift-client:4.2.1")
    testImplementation("io.fabric8:openshift-server-mock:4.2.1")
    testImplementation("com.fkorotkov:kubernetes-dsl:2.0.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:3.14.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:1.2.0")
    implementation("org.springframework.boot:spring-boot-starter-hateoas")
    implementation("org.springframework.retry:spring-retry")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    testImplementation("io.mockk:mockk:1.9.3")
    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.13")
    testImplementation("com.nhaarman:mockito-kotlin:1.6.0")
    testImplementation("no.skatteetaten.aurora:mockmvc-extensions-kotlin:0.6.4")
    testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:$junit5Version")
}

tasks {
    "test"(Test::class) {
        useJUnitPlatform()
    }

    val codeCoverageReport by creating(JacocoReport::class) {
        executionData(fileTree(project.rootDir.absolutePath).include("**/build/jacoco/*.exec"))

        subprojects.onEach {
            sourceSets(it.sourceSets["main"])
        }

        reports {
            sourceDirectories.setFrom(files(sourceSets["main"].allSource.srcDirs))
            classDirectories.setFrom(files(sourceSets["main"].output))
            xml.isEnabled = true
            xml.destination = File("$buildDir/reports/jacoco/report.xml")
            html.isEnabled = false
            csv.isEnabled = false
        }

        dependsOn("test")
    }
}