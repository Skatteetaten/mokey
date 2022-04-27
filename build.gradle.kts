plugins {
    id("java")
    id("idea")
    id("no.skatteetaten.gradle.aurora") version "4.4.16"
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:1.6.1")
    implementation("io.projectreactor.addons:reactor-extra:3.4.8")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("no.skatteetaten.aurora.kubernetes:kubernetes-reactor-coroutines-client:1.3.22")
    implementation("no.skatteetaten.aurora.springboot:aurora-spring-security-starter:1.7.0")
    implementation("com.github.ben-manes.caffeine:caffeine:3.0.6")
    implementation("uk.q3c.rest:hal-kotlin:0.5.4.0.db32476")
    implementation("com.jayway.jsonpath:json-path:2.7.0")
    implementation("org.apache.commons:commons-lang3:3.12.0")

    testImplementation("no.skatteetaten.aurora.springboot:webtestclient-extensions-kotlin:1.1.0")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.mockk:mockk:1.12.3")
    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.25")
    testImplementation("no.skatteetaten.aurora:mockwebserver-extensions-kotlin:1.2.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.9.3")
    testImplementation("com.squareup.okhttp3:okhttp:4.9.3")
    testImplementation("com.ninja-squad:springmockk:3.1.1")

    // M1 Mac configuration
    runtimeOnly("io.netty:netty-resolver-dns-native-macos:4.1.75.Final:osx-aarch_64")

    // TODO: These below here are fixed because of IQ violations
    implementation("com.fasterxml.jackson.core:jackson-databind:2.13.2.2")
}

aurora {
    useKotlinDefaults
    useSpringBootDefaults

    useSpringBoot {
        useWebFlux
        useCloudContract
    }
}
