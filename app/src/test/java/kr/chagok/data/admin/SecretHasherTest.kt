package kr.chagok.data.admin

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** PIN·복구키 해시 (PBKDF2, 솔트). 원문 대신 이 해시만 저장한다 */
class SecretHasherTest {
    private val salt = ByteArray(16) { it.toByte() }
    private val otherSalt = ByteArray(16) { (it + 1).toByte() }

    @Test
    fun `같은 PIN 과 같은 솔트는 늘 같은 32바이트 해시가 된다`() {
        val first = SecretHasher.hash("4826", salt)

        assertEquals(32, first.size)
        assertArrayEquals(first, SecretHasher.hash("4826", salt))
    }

    @Test
    fun `솔트가 다르면 같은 PIN 이라도 해시가 다르다`() {
        assertFalse(SecretHasher.hash("4826", salt).contentEquals(SecretHasher.hash("4826", otherSalt)))
    }

    @Test
    fun `PIN 이 한 자리만 달라도 해시가 다르다`() {
        assertFalse(SecretHasher.hash("4826", salt).contentEquals(SecretHasher.hash("4827", salt)))
    }

    @Test
    fun `맞는 PIN 과 솔트면 일치하고, PIN 이나 솔트가 다르면 일치하지 않는다`() {
        val saved = SecretHasher.hash("4826", salt)

        assertTrue(SecretHasher.matches("4826", salt, saved))
        assertFalse(SecretHasher.matches("4827", salt, saved))
        assertFalse(SecretHasher.matches("4826", otherSalt, saved))
        assertFalse(SecretHasher.matches("", salt, saved))
    }

    @Test
    fun `새 솔트는 16바이트이고 만들 때마다 다르다`() {
        val first = SecretHasher.newSalt()

        assertEquals(16, first.size)
        assertFalse(first.contentEquals(SecretHasher.newSalt()))
    }

    @Test
    fun `복구키는 요청한 길이만큼 숫자로만 만든다`() {
        repeat(20) {
            val key = SecretHasher.newDigits(16)
            assertEquals(16, key.length)
            assertTrue(key, key.all { it in '0'..'9' })
        }
        assertEquals("", SecretHasher.newDigits(0))
    }
}
