package okhttp3

import io.fabric8.kubernetes.client.ConfigBuilder
import io.fabric8.kubernetes.client.utils.HttpClientUtils

fun createOpenShiftHttpClient() = OkHttpClient.Builder(
    HttpClientUtils.createHttpClient(
        ConfigBuilder()
            .withConnectionTimeout(3_000)
            .withRequestTimeout(3_000)
            .withHttp2Disable(true)
            .build()
    )
)
    .retryOnConnectionFailure(true)
    .build()
