import com.adarshr.gradle.testlogger.theme.ThemeType

repositories {
    mavenLocal()
}

plugins {
    id("org.springframework.cloud.contract")
    id("org.jetbrains.kotlin.jvm") version "1.3.61"
    id("org.jetbrains.kotlin.plugin.spring") version "1.3.61"
    id("org.jlleitschuh.gradle.ktlint") version "9.2.1"
    id("org.sonarqube") version "2.8"
    id("org.asciidoctor.convert") version "2.4.0"
    id("org.springframework.boot") version "2.2.4.RELEASE"
    id("com.gorylenko.gradle-git-properties") version "2.2.1"
    id("com.github.ben-manes.versions") version "0.27.0"
    id("se.patrikerdes.use-latest-versions") version "0.2.13"
    id("com.adarshr.test-logger") version "2.0.0"

    id("no.skatteetaten.gradle.aurora") version "3.4.6"
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:1.3.3")
    implementation("io.projectreactor.addons:reactor-extra:3.3.2.RELEASE")
    implementation("no.skatteetaten.aurora.kubernetes:kubernetes-reactor-coroutines-client:1.3-1")

    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    implementation("net.rakugakibox.spring.boot:logback-access-spring-boot-starter:2.7.1")

    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    implementation("uk.q3c.rest:hal-kotlin:0.5.4.0.db32476")
    implementation("com.jayway.jsonpath:json-path:2.4.0")

    testImplementation("io.mockk:mockk:1.9.3")
    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.21")
    testImplementation("com.nhaarman:mockito-kotlin:1.6.0")
    testImplementation("no.skatteetaten.aurora:mockmvc-extensions-kotlin:1.0.4")
}

testlogger {
    theme = ThemeType.PLAIN
}
