package org.mmclark.koreloader.infrastructure.hash

import kotlin.test.Test
import kotlin.test.assertEquals

/** Expected values from Python's hashlib.blake2b (BLAKE2b-512, same as b2sum). */
class Blake2bTest {

    private fun data(n: Int) = ByteArray(n) { (it % 251).toByte() }

    private fun hash(bytes: ByteArray, chunk: Int = bytes.size.coerceAtLeast(1)): String {
        val b = Blake2b()
        var pos = 0
        while (pos < bytes.size) {
            val n = minOf(chunk, bytes.size - pos)
            b.update(bytes, pos, n)
            pos += n
        }
        return b.digest().toHex()
    }

    private val vectors = mapOf(
        0 to "786a02f742015903c6c6fd852552d272912f4740e15847618a86e217f71f5419d25e1031afee585313896444934eb04b903a685b1448b755d56f701afe9be2ce",
        127 to "b6292669ccd38d5f01caae96ba272c76a879a45743afa0725d83b9ebb26665b731f1848c52f11972b6644f554c064fa90780dbbbf3a89d4fc31f67df3e5857ef",
        128 to "2319e3789c47e2daa5fe807f61bec2a1a6537fa03f19ff32e87eecbfd64b7e0e8ccff439ac333b040f19b0c4ddd11a61e24ac1fe0f10a039806c5dcc0da3d115",
        129 to "f59711d44a031d5f97a9413c065d1e614c417ede998590325f49bad2fd444d3e4418be19aec4e11449ac1a57207898bc57d76a1bcf3566292c20c683a5c4648f",
        255 to "fe2c02da499516b0e9fb2dd70c49eb3629039f632e20a880946fb7bc97a7ab09deb7d48774d7f0648141c9d9ede19ae6e0dbf07863a128cf4b00195f0f179f74",
        256 to "93463ac058b6163eb43be3f5bb32b28541498f4e3366f1effe253ad44e1e076e41c3616046027c82a7124f8f4746668ad10b12e8e25a95ac8f3151df01cd5a93",
        1000 to "c11e1c0340bd7e5a1b275f1230c962fad215ecb1391486e74e31b960a2f2996381a5fad092da06841d5f26e38f6ecfeaf441acbcd1c2de61aef121e7927175f5",
        100000 to "a0b4e1c912a57364ed019d57296b9f14fd4ac7a36dc5b324706ba2b1497cf7b58b813ef6d86efab3a9461768ea9a552ab97554f8c4b010ed26d04ae6158bcedf",
    )

    @Test
    fun rfc7693Abc() {
        assertEquals(
            "ba80a53f981c4d0d6a2797b69f12f6e94c212f14685ac4b74b12bb6fdbffa2d17d87c5392aab792dc252d5de4533cc9518d38aa8dbf1925ab92386edd4009923",
            hash("abc".encodeToByteArray()),
        )
    }

    @Test
    fun blockBoundaries() {
        for ((n, expected) in vectors) assertEquals(expected, hash(data(n)), "length $n")
    }

    @Test
    fun chunkingDoesNotMatter() {
        for (chunk in listOf(1, 7, 128, 129, 4096)) {
            assertEquals(vectors.getValue(1000), hash(data(1000), chunk), "chunk $chunk")
        }
    }
}
