package com.quantummpv.app.ui.player.visualizer

import android.opengl.GLES20
import android.opengl.GLES30
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GlUtilsTest {

    @Before
    fun setUp() {
        mockkStatic(GLES20::class)
        mockkStatic(GLES30::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun checkFramebuffer_complete() {
        every { GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) } returns GLES30.GL_FRAMEBUFFER_COMPLETE
        every { GLES20.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) } returns GLES30.GL_FRAMEBUFFER_COMPLETE
        GlUtils.checkFramebuffer("TestLabel")
    }

    @Test
    fun checkFramebuffer_incomplete() {
        val errorCode = 0x8CD6 // GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT
        every { GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) } returns errorCode
        every { GLES20.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) } returns errorCode

        val exception = assertFailsWith<IllegalStateException> {
            GlUtils.checkFramebuffer("TestLabel")
        }
        assertEquals("TestLabel framebuffer incomplete: 0x8cd6", exception.message)
    }
}
