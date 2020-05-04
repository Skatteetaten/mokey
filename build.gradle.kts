import com.adarshr.gradle.testlogger.theme.ThemeType

repositories {
    mavenLocal()
}

plugins {
    id("org.springframework.cloud.contract")
    id("org.jetbrains.kotlin.jvm") version "1.3.72"
    id("org.jetbrains.kotlin.plugin.spring") version "1.3.72"
    id("org.springframework.boot") version "2.2.6.RELEASE"

    id("org.jlleitschuh.gradle.ktlint") version "9.2.1"
    id("org.sonarqube") version "2.8"
    id("org.asciidoctor.convert") version "2.4.0"
    id("com.gorylenko.gradle-git-properties") version "2.2.2"
    id("com.github.ben-manes.versions") version "0.28.0"
    id("se.patrikerdes.use-latest-versions") version "0.2.13"
    id("com.adarshr.test-logger") version "2.0.0"

    id("no.skatteetaten.gradle.aurora") version "3.5.2"
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:3.14.4")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:1.3.4")
    implementation("io.projectreactor.addons:reactor-extra:3.3.3.RELEASE")
    implementation("no.skatteetaten.aurora.kubernetes:kubernetes-reactor-coroutines-client:1.2.11")
    implementation("com.github.ben-manes.caffeine:caffeine:2.8.1")

    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    implementation("net.rakugakibox.spring.boot:logback-access-spring-boot-starter:2.7.1")

    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    implementation("uk.q3c.rest:hal-kotlin:0.5.4.0.db32476")
    implementation("com.jayway.jsonpath:json-path:2.4.0")

    testImplementation("io.mockk:mockk:1.10.0")
    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.21")
    testImplementation("com.nhaarman:mockito-kotlin:1.6.0")
    testImplementation("no.skatteetaten.aurora:mockmvc-extensions-kotlin:1.0.13")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.5.0")
    testImplementation("com.squareup.okhttp3:okhttp:4.5.0")
    testImplementation("com.ninja-squad:springmockk:2.0.1")
}

testlogger {
    theme = ThemeType.PLAIN
}
