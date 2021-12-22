plugins {
    id("java")
    id("idea")
    id("no.skatteetaten.gradle.aurora") version "4.4.2"
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:1.5.2")
    implementation("io.projectreactor.addons:reactor-extra:3.4.5")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("no.skatteetaten.aurora.kubernetes:kubernetes-reactor-coroutines-client:1.3.20")
    implementation("no.skatteetaten.aurora.springboot:aurora-spring-security-starter:1.6.3")
    implementation("com.github.ben-manes.caffeine:caffeine:3.0.4")
    implementation("uk.q3c.rest:hal-kotlin:0.5.4.0.db32476")
    implementation("com.jayway.jsonpath:json-path:2.6.0")
    implementation("org.apache.commons:commons-lang3:3.12.0")

    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.mockk:mockk:1.12.0")
    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.25")
    testImplementation("no.skatteetaten.aurora:mockwebserver-extensions-kotlin:1.2.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.9.2")
    testImplementation("com.squareup.okhttp3:okhttp:4.9.2")
    testImplementation("com.ninja-squad:springmockk:3.0.1")

    testImplementation("no.skatteetaten.aurora.springboot:webtestclient-extensions-kotlin:local-snapshot")
}

aurora {
    useKotlinDefaults
    useSpringBootDefaults

    useSpringBoot {
        useWebFlux
        useCloudContract
    }
}
