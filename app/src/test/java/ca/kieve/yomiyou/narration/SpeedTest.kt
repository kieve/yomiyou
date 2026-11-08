package ca.kieve.yomiyou.narration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SpeedTest {
    @Test
    fun `speed steps by a tenth within its bounds`() {
        assertEquals(1.1f, Speed.step(1f, 1))
        assertEquals(0.9f, Speed.step(1f, -1))
        assertEquals(1.5f, Speed.step(1.4f, 1))
        assertEquals(2f, Speed.step(2f, 1))
        assertEquals(0.5f, Speed.step(0.5f, -1))
        assertEquals(1f, Speed.of(null))
        assertEquals(2f, Speed.of(3f))
    }

    @Test
    fun `speed reads as a multiple`() {
        assertEquals("1.0×", Speed.label(1f))
        assertEquals("1.3×", Speed.label(1.3f))
    }
}
