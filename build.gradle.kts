import com.adarshr.gradle.testlogger.theme.ThemeType

plugins {
    id("org.springframework.cloud.contract")
    id("org.jetbrains.kotlin.jvm") version "1.3.50"
    id("org.jetbrains.kotlin.plugin.spring") version "1.3.50"
    id("org.jlleitschuh.gradle.ktlint") version "8.2.0"
    id("org.sonarqube") version "2.7.1"

    id("org.springframework.boot") version "2.1.8.RELEASE"
    id("com.gorylenko.gradle-git-properties") version "2.1.0"
    id("com.github.ben-manes.versions") version "0.25.0"
    id("se.patrikerdes.use-latest-versions") version "0.2.12"
    id("com.adarshr.test-logger") version "1.7.0"

    id("no.skatteetaten.gradle.aurora") version "2.4.2"
}

extra["spring-hateoas.version"] = "0.24.0.RELEASE"

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.2.0")
    implementation("com.fkorotkov:kubernetes-dsl:2.2")
    testImplementation("io.fabric8:openshift-server-mock:4.5.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:1.3.1")
    implementation("org.springframework.boot:spring-boot-starter-hateoas")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("io.projectreactor.addons:reactor-extra:3.2.3.RELEASE")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    testImplementation("io.mockk:mockk:1.9.3")
    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.19")
    testImplementation("com.nhaarman:mockito-kotlin:1.6.0")
    testImplementation("no.skatteetaten.aurora:mockmvc-extensions-kotlin:1.0.0")
}

testlogger {
    theme = ThemeType.PLAIN
}