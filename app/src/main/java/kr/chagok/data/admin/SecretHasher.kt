package kr.chagok.data.admin

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PIN·복구키를 원문 대신 솔트를 붙인 해시(PBKDF2)로만 저장한다.
 * 4자리 PIN 은 해시만으로는 다 대입해 보면 풀린다. 막는 것은 틀린 횟수 제한([AdminRepository])이다.
 */
internal object SecretHasher {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 20_000 // 차 헤드유닛에서 0.1초 안쪽
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    private val random = SecureRandom()

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also(random::nextBytes)

    fun hash(secret: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** 걸리는 시간이 같은 비교 */
    fun matches(secret: String, salt: ByteArray, expected: ByteArray): Boolean =
        MessageDigest.isEqual(hash(secret, salt), expected)

    /** 숫자만으로 된 복구키. 보안 난수로 만든다 */
    fun newDigits(length: Int): String = buildString(length) { repeat(length) { append(random.nextInt(10)) } }
}
