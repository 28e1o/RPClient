package me.kafuuneko.rpclient.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TokenPresetTest {
    @Test
    fun entries_defineSharedDisplayNamesAndBinaryValues() {
        assertEquals(
            listOf("8K", "16K", "32K", "64K", "128K", "256K", "512K", "1M"),
            TokenPreset.entries.map { it.displayName }
        )
        assertEquals(
            listOf(8192, 16384, 32768, 65536, 131072, 262144, 524288, 1048576),
            TokenPreset.entries.map { it.value }
        )
    }
}
