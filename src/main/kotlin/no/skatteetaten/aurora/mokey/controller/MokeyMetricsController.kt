package no.skatteetaten.aurora.mokey.controller

import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

@Component
@RestControllerEndpoint(id = "mokey-metrics")
class MokeyMetricsController {

    private val webClient = WebClient.create("http://localhost:8081")

    @GetMapping(value = ["/application_info"], produces = [MediaType.TEXT_PLAIN_VALUE])
    suspend fun getApplicationInfo() = webClient
        .get()
        .uri("/actuator/prometheus?includedNames=application_info")
        .retrieve()
        .awaitBody<String>()

    @GetMapping(value = ["/application_status"], produces = [MediaType.TEXT_PLAIN_VALUE])
    suspend fun getApplicationStatus() = webClient
        .get()
        .uri("/actuator/prometheus?includedNames=application_status")
        .retrieve()
        .awaitBody<String>()

    @GetMapping(produces = [MediaType.TEXT_PLAIN_VALUE])
    suspend fun getMokeyMetrics() = webClient
        .get()
        .uri(
            "/actuator/prometheus?includedNames=" +
                "application_ready_time," +
                "application_started_time," +
                "disk_free,disk_total," +
                "executor_active," +
                "executor_completed," +
                "executor_pool_core," +
                "executor_pool_max," +
                "executor_pool_size," +
                "executor_queue_remaining," +
                "executor_queued," +
                "jvm_buffer_count," +
                "jvm_buffer_memory_used," +
                "jvm_buffer_total_capacity," +
                "jvm_classes_loaded," +
                "jvm_classes_unloaded," +
                "jvm_gc_live_data_size," +
                "jvm_gc_max_data_size," +
                "jvm_gc_memory_allocated," +
                "jvm_gc_memory_promoted," +
                "jvm_gc_overhead," +
                "jvm_gc_pause," +
                "jvm_memory_committed," +
                "jvm_memory_max," +
                "jvm_memory_usage_after_gc," +
                "jvm_memory_used," +
                "jvm_threads_daemon," +
                "jvm_threads_live," +
                "jvm_threads_peak," +
                "jvm_threads_states," +
                "logback_events," +
                "process_cpu_usage," +
                "process_files_max," +
                "process_files_open," +
                "process_start_time," +
                "process_uptime," +
                "reactor_netty_connection_provider_active_connections," +
                "reactor_netty_connection_provider_idle_connections," +
                "reactor_netty_connection_provider_max_connections," +
                "reactor_netty_connection_provider_max_pending_connections," +
                "reactor_netty_connection_provider_pending_connections," +
                "reactor_netty_connection_provider_total_connections," +
                "system_cpu_count," +
                "system_cpu_usage," +
                "system_load_average_1m," +
                "zipkin_reporter_messages," +
                "zipkin_reporter_messages_total," +
                "zipkin_reporter_queue_bytes," +
                "zipkin_reporter_queue_spans," +
                "zipkin_reporter_spans," +
                "zipkin_reporter_spans_dropped," +
                "zipkin_reporter_spans_total"
        )
        .retrieve()
        .awaitBody<String>()
}
