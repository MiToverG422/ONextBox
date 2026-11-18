package com.mi.onextbox.lsp

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DexDescriptorStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private val identity = DexCacheIdentity("apk-fingerprint", 42, "ColorOs17", 3, "feature-v2")
    class Fixture {
        @JvmField var state = 1
        fun example(number: Int, words: Array<String>): String = "$number:${words.size}"
    }
    interface CovariantParent { fun value(): Any }
    class CovariantChild : CovariantParent { override fun value(): String = "value" }
    private fun descriptor() = DexMemberDescriptor.of(Fixture::class.java.getDeclaredMethod("example", Int::class.javaPrimitiveType, Array<String>::class.java))

    @Test fun roundTripsSignaturesAndEmptySchemaGroups() {
        val file = File(folder.root, "targets.bin")
        val targets = mapOf("gate" to listOf(descriptor()), "schema:v2" to emptyList())
        DexDescriptorStore.write(file, identity, targets)
        assertEquals(targets, DexDescriptorStore.read(file, identity))
        val resolved = DexDescriptorStore.read(file, identity)!!.getValue("gate").single().resolve(javaClass.classLoader!!)
        assertEquals(Fixture::class.java, resolved.declaringClass)
        assertEquals("example", resolved.name)
    }

    @Test fun everyIdentityComponentInvalidatesTheCache() {
        val file = File(folder.root, "targets.bin")
        DexDescriptorStore.write(file, identity, mapOf("gate" to listOf(descriptor())))
        listOf(identity.copy(apk = "new"), identity.copy(moduleBuild = 43), identity.copy(adaptation = "ColorOs16"),
            identity.copy(generation = 4), identity.copy(schema = "v3")).forEach { assertNull(DexDescriptorStore.read(file, it)) }
    }

    @Test fun corruptAndTruncatedFilesAreCacheMisses() {
        val file = File(folder.root, "targets.bin")
        DexDescriptorStore.write(file, identity, mapOf("gate" to listOf(descriptor())))
        val bytes = file.readBytes()
        file.writeBytes(bytes.copyOf(bytes.size / 2))
        assertNull(DexDescriptorStore.read(file, identity))
        file.writeText("not a target cache")
        assertNull(DexDescriptorStore.read(file, identity))
    }

    @Test fun changedReturnTypeOrModifiersAreRejectedBeforeInstallation() {
        val descriptor = descriptor()
        assertThrows(IllegalStateException::class.java) { descriptor.copy(type = "boolean").resolve(javaClass.classLoader!!) }
        assertThrows(IllegalStateException::class.java) { descriptor.copy(modifiers = 0).resolve(javaClass.classLoader!!) }
    }

    @Test fun membersResolveInTheSuppliedLoader() {
        val name = Fixture::class.java.name
        val bytes = Fixture::class.java.getResourceAsStream("/${name.replace('.', '/')}.class")!!.use { it.readBytes() }
        val child = object : ClassLoader(javaClass.classLoader) {
            override fun loadClass(className: String, resolve: Boolean): Class<*> =
                if (className == name) findLoadedClass(className) ?: defineClass(className, bytes, 0, bytes.size)
                else super.loadClass(className, resolve)
        }
        val member = descriptor().resolve(child)
        assertSame(child, member.declaringClass.classLoader)
        assertNotSame(Fixture::class.java, member.declaringClass)
        val field = DexMemberDescriptor.of(Fixture::class.java.getDeclaredField("state")).resolve(child)
        assertSame(child, field.declaringClass.classLoader)
    }

    @Test fun changingApkContentsInvalidatesEvenAtTheSamePath() {
        val apk = File(folder.root, "host.apk")
        fun write(text: String) = ZipOutputStream(apk.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("classes.dex")); zip.write(text.toByteArray()); zip.closeEntry()
        }
        write("first dex")
        val first = DexDescriptorStore.fingerprint(apk)
        assertEquals(first, DexDescriptorStore.fingerprint(apk))
        write("other dex")
        assertNotEquals(first, DexDescriptorStore.fingerprint(apk))
    }

    @Test fun bridgeMethodsAreResolvedByReturnTypeAndModifiers() {
        val methods = CovariantChild::class.java.declaredMethods.filter { it.name == "value" }
        assertEquals(2, methods.size)
        assertTrue(methods.any { it.isBridge })
        methods.forEach { method ->
            assertEquals(method, DexMemberDescriptor.of(method).resolve(javaClass.classLoader!!))
        }
    }

    @Test fun obfuscatedFieldsWithTheSameNameAreResolvedByType() {
        val bytes = java.io.ByteArrayOutputStream().also { buffer ->
            java.io.DataOutputStream(buffer).use { out ->
                out.writeInt(0xCAFEBABE.toInt()); out.writeShort(0); out.writeShort(52); out.writeShort(8)
                fun utf(value: String) { out.writeByte(1); out.writeUTF(value) }
                fun type(index: Int) { out.writeByte(7); out.writeShort(index) }
                utf("fixture/OverloadedFields"); type(1); utf("java/lang/Object"); type(3)
                utf("value"); utf("I"); utf("Ljava/lang/String;")
                out.writeShort(0x21); out.writeShort(2); out.writeShort(4); out.writeShort(0); out.writeShort(2)
                listOf(6, 7).forEach { descriptor ->
                    out.writeShort(1); out.writeShort(5); out.writeShort(descriptor); out.writeShort(0)
                }
                out.writeShort(0); out.writeShort(0)
            }
        }.toByteArray()
        val loader = object : ClassLoader(javaClass.classLoader) {
            fun define(): Class<*> = defineClass("fixture.OverloadedFields", bytes, 0, bytes.size)
        }
        val owner = loader.define()
        assertEquals(2, owner.declaredFields.size)
        owner.declaredFields.forEach { field -> assertEquals(field, DexMemberDescriptor.of(field).resolve(loader)) }
    }
}
