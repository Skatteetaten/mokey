package no.skatteetaten.aurora.mokey.service

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class DataParserTest {

    @Test
    fun `it should parse a given time into an instant`() {
        val instant = DateParser.parseString("03.04.2019 @ 16:17:35 CEST")
        assertThat(instant?.toString()).isEqualTo("2019-04-03T14:17:35Z")

        val instant1 = DateParser.parseString("2019-04-03 16:17:35 +0200")
        assertThat(instant1?.toString()).isEqualTo("2019-04-03T14:17:35Z")

        val instant2 = DateParser.parseString("2019-04-03T14:17:35Z")
        assertThat(instant2?.toString()).isEqualTo("2019-04-03T14:17:35Z")
    }
}