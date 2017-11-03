package no.skatteetaten.aurora.mokey

import io.micrometer.spring.autoconfigure.export.StringToDurationConverter
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@Configuration
@EnableScheduling
@Import(StringToDurationConverter::class)
class Main

fun main(args: Array<String>) {
    SpringApplication.run(Main::class.java, *args)
}
