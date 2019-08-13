package okhttp3

import io.fabric8.kubernetes.client.ConfigBuilder
import io.fabric8.kubernetes.client.utils.HttpClientUtils
import java.util.concurrent.TimeUnit

fun createOpenShiftHttpClient() = OkHttpClient.Builder(
    HttpClientUtils.createHttpClient(
        ConfigBuilder().withConnectionTimeout(3_000).withRequestTimeout(3_000).build()
    )
)
    .protocols(listOf(Protocol.HTTP_1_1))
    .connectionPool(ConnectionPool(1, 30, TimeUnit.SECONDS))
    .retryOnConnectionFailure(true)
    .build()
