package com.mi.onextbox.lsp

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipFile

internal data class DexCacheIdentity(
    val apk: String, val moduleBuild: Long, val adaptation: String, val generation: Int, val schema: String,
)

/** Persist names and signatures only. Reflection objects always belong to the current loader. */
internal data class DexMemberDescriptor(
    val kind: String, val owner: String, val name: String, val type: String,
    val parameters: List<String>, val modifiers: Int,
) {
    fun resolve(loader: ClassLoader): Member {
        val ownerClass = HostClassLookup.load(owner, false, loader)
        return when (kind) {
            "method" -> {
                val parameterTypes = parameters.map { resolveType(it, loader) }.toTypedArray()
                // Bridge methods and obfuscated members can share a name and parameter list.
                (ownerClass.declaredMethods.singleOrNull {
                    it.name == name && it.returnType.name == type && it.modifiers == modifiers &&
                        it.parameterTypes.contentEquals(parameterTypes)
                } ?: error("Cached method signature changed")).also { it.isAccessible = true }
            }
            "field" -> {
                check(parameters.isEmpty())
                (ownerClass.declaredFields.singleOrNull {
                    it.name == name && it.type.name == type && it.modifiers == modifiers
                } ?: error("Cached field signature changed")).also { it.isAccessible = true }
            }
            else -> error("Unknown cached member kind")
        }
    }

    companion object {
        fun of(member: Member): DexMemberDescriptor = when (member) {
            is Method -> DexMemberDescriptor("method", member.declaringClass.name, member.name,
                member.returnType.name, member.parameterTypes.map { it.name }, member.modifiers)
            is Field -> DexMemberDescriptor("field", member.declaringClass.name, member.name,
                member.type.name, emptyList(), member.modifiers)
            else -> error("Unsupported cached member")
        }

        private fun resolveType(name: String, loader: ClassLoader): Class<*> = when (name) {
            "boolean" -> Boolean::class.javaPrimitiveType!!
            "byte" -> Byte::class.javaPrimitiveType!!
            "char" -> Char::class.javaPrimitiveType!!
            "short" -> Short::class.javaPrimitiveType!!
            "int" -> Int::class.javaPrimitiveType!!
            "long" -> Long::class.javaPrimitiveType!!
            "float" -> Float::class.javaPrimitiveType!!
            "double" -> Double::class.javaPrimitiveType!!
            else -> HostClassLookup.load(name, false, loader)
        }
    }
}

internal object DexDescriptorStore {
    private const val MAGIC = 0x4F4E4458
    private const val FORMAT = 1
    private const val MAX_BYTES = 2 * 1024 * 1024L

    fun fingerprint(apk: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        ZipFile(apk).use { zip ->
            zip.entries().asSequence().filter { !it.isDirectory }.sortedBy { it.name }.forEach {
                digest.update("${it.name}:${it.size}:${it.crc}\n".toByteArray(Charsets.UTF_8))
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun read(file: File, identity: DexCacheIdentity): Map<String, List<DexMemberDescriptor>>? = runCatching {
        if (!file.isFile || file.length() > MAX_BYTES) return null
        DataInputStream(file.inputStream().buffered()).use { input ->
            check(input.readInt() == MAGIC && input.readInt() == FORMAT)
            val saved = DexCacheIdentity(input.readUTF(), input.readLong(), input.readUTF(), input.readInt(), input.readUTF())
            if (saved != identity) return null
            val result = linkedMapOf<String, List<DexMemberDescriptor>>()
            repeat(input.readInt().also { require(it in 0..512) }) {
                val key = input.readUTF()
                check(key !in result)
                result[key] = List(input.readInt().also { require(it in 0..4096) }) {
                    DexMemberDescriptor(input.readUTF(), input.readUTF(), input.readUTF(), input.readUTF(),
                        List(input.readInt().also { require(it in 0..255) }) { input.readUTF() }, input.readInt())
                }
            }
            check(input.read() == -1)
            result
        }
    }.getOrNull()

    fun write(file: File, identity: DexCacheIdentity, targets: Map<String, List<DexMemberDescriptor>>) {
        check(file.parentFile?.isDirectory == true || file.parentFile?.mkdirs() == true)
        val pending = File.createTempFile(file.name, ".tmp", file.parentFile)
        try {
            DataOutputStream(pending.outputStream().buffered()).use { output ->
                output.writeInt(MAGIC); output.writeInt(FORMAT)
                output.writeUTF(identity.apk); output.writeLong(identity.moduleBuild)
                output.writeUTF(identity.adaptation); output.writeInt(identity.generation); output.writeUTF(identity.schema)
                output.writeInt(targets.size)
                targets.forEach { (key, descriptors) ->
                    output.writeUTF(key); output.writeInt(descriptors.size)
                    descriptors.forEach { item ->
                        output.writeUTF(item.kind); output.writeUTF(item.owner); output.writeUTF(item.name)
                        output.writeUTF(item.type); output.writeInt(item.parameters.size)
                        item.parameters.forEach(output::writeUTF); output.writeInt(item.modifiers)
                    }
                }
            }
            check(pending.length() <= MAX_BYTES)
            Files.move(pending.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { pending.delete() }
    }
}
