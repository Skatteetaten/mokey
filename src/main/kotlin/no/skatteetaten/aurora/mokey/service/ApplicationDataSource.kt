package no.skatteetaten.aurora.mokey.service

import org.springframework.beans.factory.annotation.Qualifier

enum class DataSources {
    CACHE, CLUSTER
}

@Target(AnnotationTarget.TYPE, AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class ApplicationDataSource(val value: DataSources)
