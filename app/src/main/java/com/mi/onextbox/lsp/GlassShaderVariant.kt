package com.mi.onextbox.lsp

internal class GlassShaderVariant(private val original: String, candidate: String?) {
    @Volatile private var extended = candidate
    @Volatile private var accepted = false

    fun <T : Any> createCached(create: (String) -> T?, rejected: (RuntimeException) -> Unit): T? {
        val source = extended ?: return create(original)
        val result = try {
            create(source)
        } catch (e: RuntimeException) {
            rejected(e)
            null
        }
        if (result != null) {
            accepted = true
            return result
        }
        extended = null
        return create(original)
    }

    // A constructor cannot substitute another instance if its native initialization fails.
    fun constructorSource(validate: (String) -> Unit, rejected: (RuntimeException) -> Unit): String {
        if (accepted) return extended ?: original
        synchronized(this) {
            val source = extended ?: return original
            if (!accepted) {
                try {
                    validate(source)
                    accepted = true
                } catch (e: RuntimeException) {
                    extended = null
                    rejected(e)
                    return original
                }
            }
            return source
        }
    }
}
