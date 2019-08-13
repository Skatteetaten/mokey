package okhttp3

import io.fabric8.kubernetes.client.ConfigBuilder
import io.fabric8.kubernetes.client.utils.HttpClientUtils

fun createOpenShiftHttpClient() = OkHttpClient.Builder(
    HttpClientUtils.createHttpClient(
        ConfigBuilder().withConnectionTimeout(60_000).withRequestTimeout(60_000).build()
    )
).protocols(listOf(Protocol.HTTP_1_1)).build()
