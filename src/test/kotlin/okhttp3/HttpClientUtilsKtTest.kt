package okhttp3

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class HttpClientUtilsKtTest {

    @Test
    @Disabled
    fun `Create OkHttpClient with only Http 1_1`() {
        val httpClient = createOpenShiftHttpClient()
        assertThat(httpClient.protocols).all {
            hasSize(1)
            contains(Protocol.HTTP_1_1)
        }
    }
}