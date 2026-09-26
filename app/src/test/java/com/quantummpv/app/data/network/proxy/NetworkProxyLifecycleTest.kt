package com.quantummpv.app.data.network.proxy

import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NetworkProxyLifecycleTest {

    @Test
    fun `test proxy lifecycle stops cleanly`() {
        val proxy = NetworkStreamingProxy.getInstance()
        assertNotNull(proxy)
        NetworkStreamingProxy.stopInstance()
    }
}
