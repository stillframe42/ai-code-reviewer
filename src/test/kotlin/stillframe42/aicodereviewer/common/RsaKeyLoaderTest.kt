package stillframe42.aicodereviewer.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest

// RsaKeyLoader 단위 테스트 — PEM 파일 없이 내부 변환 로직을 검증한다
class RsaKeyLoaderTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var rsaKeyLoader: RsaKeyLoader

    @Test
    fun `wrapPkcs1InPkcs8 결과는 DER SEQUENCE 태그(0x30)로 시작한다`() {
        val dummyPkcs1 = ByteArray(10) { it.toByte() }
        val pkcs8 = rsaKeyLoader.wrapPkcs1InPkcs8(dummyPkcs1)

        assertThat(pkcs8[0]).isEqualTo(0x30.toByte())
    }

    @Test
    fun `wrapPkcs1InPkcs8 결과에 RSA OID 바이트 시퀀스가 포함된다`() {
        // RSA OID: 1.2.840.113549.1.1.1 → 2A 86 48 86 F7 0D 01 01 01
        val rsaOid = byteArrayOf(
            0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(),
            0x0D, 0x01, 0x01, 0x01,
        )
        val dummyPkcs1 = ByteArray(10) { it.toByte() }
        val pkcs8 = rsaKeyLoader.wrapPkcs1InPkcs8(dummyPkcs1)

        // PKCS#8 바이트 배열 안에 RSA OID가 포함되어야 한다
        val pkcs8Hex = pkcs8.joinToString("") { "%02X".format(it) }
        val oidHex = rsaOid.joinToString("") { "%02X".format(it) }
        assertThat(pkcs8Hex).contains(oidHex)
    }
}
