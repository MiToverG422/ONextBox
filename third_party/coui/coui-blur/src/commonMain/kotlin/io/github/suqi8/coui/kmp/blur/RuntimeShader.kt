// Copyright 2026, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
@file:Suppress("FunctionName")

package io.github.suqi8.coui.kmp.blur

import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import io.github.suqi8.coui.kmp.shader.asBrush as coreAsBrush
import io.github.suqi8.coui.kmp.shader.asComposeShader as coreAsComposeShader
import io.github.suqi8.coui.kmp.shader.isRuntimeShaderSupported as coreIsRuntimeShaderSupported

/** Back-compat re-export. New code should use `io.github.suqi8.coui.kmp.shader.RuntimeShader`. */
typealias RuntimeShader = io.github.suqi8.coui.kmp.shader.RuntimeShader

/**
 * Back-compat re-export.
 *
 * @param shaderString The AGSL/SkSL shader source code to compile into the [RuntimeShader].
 */
fun RuntimeShader(shaderString: String): RuntimeShader = io.github.suqi8.coui.kmp.shader.RuntimeShader(shaderString)

/** Back-compat re-export. */
fun RuntimeShader.asComposeShader(): Shader = coreAsComposeShader()

/** Back-compat re-export. */
fun RuntimeShader.asBrush(): ShaderBrush = coreAsBrush()

/** Back-compat re-export. */
fun isRuntimeShaderSupported(): Boolean = coreIsRuntimeShaderSupported()
