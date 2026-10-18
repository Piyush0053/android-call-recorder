package com.callrecorder.app

import com.callrecorder.app.core.audio.CircularByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CircularByteBufferTest {

    @Test
    fun testEmptyBuffer() {
        val buffer = CircularByteBuffer(10)
        assertEquals(0, buffer.size)
        assertArrayEquals(ByteArray(0), buffer.toByteArray())
    }

    @Test
    fun testPartialWrite() {
        val buffer = CircularByteBuffer(10)
        val data = byteArrayOf(1, 2, 3)
        buffer.write(data, 0, 3)

        assertEquals(3, buffer.size)
        assertArrayEquals(byteArrayOf(1, 2, 3), buffer.toByteArray())
    }

    @Test
    fun testExactCapacityWrite() {
        val buffer = CircularByteBuffer(5)
        val data = byteArrayOf(10, 20, 30, 40, 50)
        buffer.write(data, 0, 5)

        assertEquals(5, buffer.size)
        assertArrayEquals(byteArrayOf(10, 20, 30, 40, 50), buffer.toByteArray())
    }

    @Test
    fun testOverflowWrapAround() {
        val buffer = CircularByteBuffer(4)
        // Write 3 bytes
        buffer.write(byteArrayOf(1, 2, 3), 0, 3)
        assertEquals(3, buffer.size)

        // Write 3 more bytes (overflow by 2 bytes)
        buffer.write(byteArrayOf(4, 5, 6), 0, 3)
        assertEquals(4, buffer.size)

        // Oldest bytes (1, 2) overwritten, remaining: 3, 4, 5, 6 in order
        assertArrayEquals(byteArrayOf(3, 4, 5, 6), buffer.toByteArray())
    }

    @Test
    fun testClear() {
        val buffer = CircularByteBuffer(5)
        buffer.write(byteArrayOf(1, 2, 3, 4, 5), 0, 5)
        assertEquals(5, buffer.size)

        buffer.clear()
        assertEquals(0, buffer.size)
        assertArrayEquals(ByteArray(0), buffer.toByteArray())
    }

    @Test
    fun testInvalidInputsIgnored() {
        val buffer = CircularByteBuffer(5)
        buffer.write(byteArrayOf(1, 2), 0, 0) // zero length
        assertEquals(0, buffer.size)

        buffer.write(byteArrayOf(1, 2), -1, 1) // negative offset
        assertEquals(0, buffer.size)

        buffer.write(byteArrayOf(1, 2), 0, -1) // negative length
        assertEquals(0, buffer.size)

        buffer.write(byteArrayOf(1, 2), 1, 5) // offset + length > data.size
        assertEquals(0, buffer.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testZeroCapacityThrows() {
        CircularByteBuffer(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testNegativeCapacityThrows() {
        CircularByteBuffer(-5)
    }
}
