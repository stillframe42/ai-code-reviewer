package stillframe42.aicodereviewer.common

import org.springframework.stereotype.Component
import java.io.File
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

// RSA Private Key 로더 — PEM 파일 경로를 받아 PrivateKey를 반환하는 공통 유틸
// GitHub App 외에도 RSA 키가 필요한 모든 곳에서 재사용 가능하다
@Component
class RsaKeyLoader {

    // 주어진 경로의 PEM 파일을 읽어 RSA PrivateKey를 반환한다
    fun load(path: String): PrivateKey {
        val pemContent = File(path).readText()
        val pkcs1Bytes = decodePemBody(pemContent)
        val pkcs8Bytes = wrapPkcs1InPkcs8(pkcs1Bytes)
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(pkcs8Bytes))
    }

    // PEM 헤더/푸터 및 개행 제거 후 Base64 디코딩
    private fun decodePemBody(pem: String): ByteArray {
        val body = pem
            .lines()
            .filter { !it.startsWith("-----") }
            .joinToString("")
        return Base64.getDecoder().decode(body)
    }

    // PKCS#1 DER bytes를 PKCS#8 PrivateKeyInfo 구조로 래핑 (BouncyCastle 없이)
    // PKCS#8 구조: SEQUENCE { version(0), algorithmIdentifier(RSA OID), OCTET STRING { pkcs1Bytes } }
    internal fun wrapPkcs1InPkcs8(pkcs1Bytes: ByteArray): ByteArray {
        // RSA AlgorithmIdentifier: SEQUENCE { OID 1.2.840.113549.1.1.1, NULL }
        val algorithmId = byteArrayOf(
            0x30, 0x0D,
            0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(),
            0x0D, 0x01, 0x01, 0x01,
            0x05, 0x00,
        )
        val version = byteArrayOf(0x02, 0x01, 0x00)
        val octetString = derTlv(0x04, pkcs1Bytes)
        val inner = version + algorithmId + octetString
        return derTlv(0x30, inner)
    }

    // DER TLV 인코딩: tag + length + value
    private fun derTlv(tag: Int, value: ByteArray): ByteArray {
        val length = value.size
        val lengthBytes = when {
            length < 0x80 -> byteArrayOf(length.toByte())
            length < 0x100 -> byteArrayOf(0x81.toByte(), length.toByte())
            else -> byteArrayOf(
                0x82.toByte(),
                (length shr 8).toByte(),
                (length and 0xFF).toByte(),
            )
        }
        return byteArrayOf(tag.toByte()) + lengthBytes + value
    }
}
