package com.mi.onextbox.lsp

/** Changes the native material's RGB at the final sample, preserving its capture, coverage and mask. */
internal object SoftLightGlassShader {
    const val INPUT = "uniBDFBitmap"
    const val UNIFORM = "onbSoftGlass"
    const val STYLE_UNIFORM = "onbLiquidStyle"
    const val STYLE_DECLARATION = "uniform float2 $STYLE_UNIFORM;"
    private const val DECLARATION = "uniform float3 $UNIFORM;"
    private const val MAIN = "half4 main(float2 position)"
    private const val SAMPLE = "half4 outputCol = half4(uniBDFBitmap.eval(mapCoords).rgb, 1.0);"
    private const val MASKED = "return outputCol*shape;"
    private const val UNMASKED = "return outputCol;"

    enum class PanelProgram(val style: Int, val lowPower: Boolean) {
        SOFT(0, false), SOFT_LOW(0, true), REFRACTION(1, false), REFRACTION_LOW(1, true),
        LIQUID(2, false), LIQUID_LOW(2, true);

        val marker = "// onbPanelGlass=$name\n"
        companion object {
            fun forStyle(style: Int, lowPower: Boolean) = entries.first { it.style == style && it.lowPower == lowPower }
            fun inSource(source: String) = entries.firstOrNull { source.startsWith(it.marker, DECLARATION.length + 1) }
        }
    }

    fun isMaterial(source: CharSequence) = source.startsWith(DECLARATION)

    fun extend(source: String, launcher: Boolean, systemUi: Boolean, lockscreen: Boolean = false, panel: PanelProgram? = null): String? {
        if (isMaterial(source)) return source
        val main = source.indexOf(MAIN)
        if (main < 0 || !source.contains("uniform shader $INPUT;") || !source.contains("uniform float u_commonArray[8];")) return null
        val sample = source.indexOf(SAMPLE, main)
        if (sample < 0 || source.indexOf("ShapeResult shapeResult =", sample) < 0) return null
        val masked = source.lastIndexOf(MASKED)
        val finish = if (masked > sample) masked else source.lastIndexOf(UNMASKED)
        if (finish <= sample) return null
        val plugin = panel == null || panel.style == SoftLightGlassSettings.LIQUID
        val classic = panel == null || panel.style != SoftLightGlassSettings.LIQUID
        val style = panel?.let { "${it.style}.0" } ?: "onbLiquidStyle.x"
        fun optics(body: String) = if (panel == null) body else body.replace("onbLiquidStyle.x", style)
            .replace("onbLowPower", if (panel.lowPower) "1.0" else "0.0")
        return buildString {
            appendLine(DECLARATION)
            panel?.let { append(it.marker) }
            appendLine(STYLE_DECLARATION)
            appendLine(LiquidGlassShader.DECLARATION)
            appendLine("uniform float onbLgUnified;")
            appendLine("uniform float4 onbLgOptics[7]; uniform float4 onbLgFinish[7]; uniform float4 onbLgTint[7]; uniform float onbLgSampling[7];")
            appendLine("const bool onbLockscreenPlugin = $lockscreen;")
            appendLine("uniform float4 onbScenes;")
            appendLine("uniform float onbAodScene;")
            appendLine("uniform float4 onbOptics;") // Strength, blur spread, rim width, rim light.
            appendLine("uniform float3 onbToneControls;") // Refraction, saturation, exposure.
            appendLine("uniform float onbKeepWhite;")
            appendLine("uniform float onbLowPower;")
            appendLine("uniform float2 onbTint;")
            appendLine("uniform float onbDimming;")
            appendLine("uniform float2 onbLampOffset;")
            appendLine("uniform float onbSelectionState;")
            appendLine("uniform float onbPreviewFolder;")
            appendLine(if (systemUi) "uniform float2 onbPanelSurface;" else "const float2 onbPanelSurface = float2(0.0);")
            append(source, 0, main)
            appendLine("const bool onbLauncher = ${if (launcher) "true" else "false"};")
            append(COMMON_OPTICS)
            if (plugin) append(optics(LiquidGlassShader.SOURCE))
            if (classic) appendLine(optics(CLASSIC_OPTICS))
            append(source, main, sample)
            appendLine("half4 onbCapture = uniBDFBitmap.eval(mapCoords);")
            appendLine("half4 outputCol = half4(onbCapture.rgb, 1.0);")
            append(source, sample + SAMPLE.length, finish)
            val rounded = masked > sample
            val outline = if (systemUi && !launcher) {
                val unmaskedPanel = "((onbPanelSurface.x>=3.0 && onbPanelSurface.x<=5.0) || onbPanelSurface.x>=7.0)"
                if (rounded) "(u_commonArray[6]>.5 || $unmaskedPanel)" else unmaskedPanel
            } else "true"
            val scene = when {
                launcher -> "onbPreviewFolder < .5 ? onbScenes.x : (onbPreviewFolder < 1.5 ? onbScenes.y : (onbPreviewFolder < 3.5 ? onbScenes.z : onbScenes.w))"
                systemUi -> "(onbPanelSurface.x == 8.0 || onbPanelSurface.x == 11.0) ? onbAodScene : (onbPanelSurface.x == 1.0 ? onbScenes.y : ((onbPanelSurface.x == 2.0 || onbPanelSurface.x == 6.0) ? onbScenes.z : ((onbPanelSurface.x == 3.0 || onbPanelSurface.x == 10.0) ? onbScenes.w : onbScenes.x)))"
                else -> "onbScenes.x"
            }
            appendLine("if (onbSoftGlass.x > 0.0 && onbOptics.x > 0.0 && ($scene) > .5 && onbPanelSurface.x >= 0.0 && ($outline) && (outputCol.a > .001 || $style > 1.5) && min(u_size.x,u_size.y)>3.0 && (u_size.x<.94*onbSoftGlass.y || u_size.y<.82*onbSoftGlass.z || onbPanelSurface.x==4.0 || onbPanelSurface.x==5.0 || onbPanelSurface.x==7.0)) {")
            if (plugin) {
                appendLine("    if ($style > 1.5 && onbCapture.a > .001) {")
                appendLine("        float3 nativeColor = outputCol.rgb/max(.001,outputCol.a);")
                appendLine("        float low = min(min(nativeColor.r,nativeColor.g),nativeColor.b);")
                appendLine("        bool keep = onbKeepWhite > .5 && (onbSelectionState > 1.5 || (onbSelectionState > .5 && low > .70 && max(max(nativeColor.r,nativeColor.g),nativeColor.b)-low < .17));")
                if (systemUi && !lockscreen) {
                    appendLine("        keep = keep || onbSelectionState > 2.5 || onbSelectionState < -.5 || (onbPanelSurface.x == 1.0 && onbSelectionState > .5);")
                }
                val coverage = if (rounded) "outputCol*shape" else "outputCol"
                appendLine("        if (!keep) {")
                appendLine("            half4 nativeLayer = $coverage;")
                appendLine("            if (nativeLayer.a <= 0.0) return nativeLayer;")
                val hasRect = "CommonDataRect rectData = getCommonDataRect(coords);" in source
                val rect = if (hasRect) "rectData.pos*float2(1,-1),rectData.halfSize,rectData.coner" else
                    "position-float2(u_commonArray[2],u_size.y-u_commonArray[3]),float2(u_commonArray[4],u_commonArray[5]),u_commonArray[6]"
                appendLine("            half3 glass = onbPluginMaterial(onbCapture.rgb/onbCapture.a,position,mapCoords,matrixScale,$rect,shapeResult.sdf);")
                appendLine("            return half4(mix(nativeLayer.rgb,glass*nativeLayer.a,smoothstep(.05,.95,onbCapture.a)),nativeLayer.a);")
                appendLine("        }")
                appendLine("    }")
            }
            if (classic) appendLine("    if ($style <= 1.5) outputCol.rgb = outputCol.a * onbMaterial(outputCol.rgb/outputCol.a, onbCapture, position, mapCoords, matrixScale, shapeResult.sdf, $rounded);")
            appendLine("}")
            append(source, finish, source.length)
        }
    }

    private const val COMMON_OPTICS = """
        float onbHeight(float x, float power) {
            float t = .5+.5*sqrt(clamp(x,0.0,1.0));
            float smoothHeight = 2.0*t*t*t*(t*(t*6.0-15.0)+10.0)-1.0;
            return 1.0-pow(clamp(1.0-smoothHeight,0.0,1.0),power);
        }

        float3 onbTone(float3 color, float4 curve, float amount, float saturation, float brightness) {
            float3 rgb = toLinearSrgb(clamp(color,0.0,1.0));
            float3 cone = pow(max(float3(dot(rgb,float3(.412221,.536333,.051446)),
                dot(rgb,float3(.211903,.680700,.107397)),dot(rgb,float3(.088302,.281719,.629979))),0.0),
                float3(1.0/3.0));
            float l = dot(cone,float3(.210454,.793618,-.004072));
            float t = clamp(l,0.0,1.0);
            float3 m = mix(curve.xyz,curve.yzw,t);
            float2 n = mix(m.xy,m.yz,t);
            cone += amount*(mix(n.x,n.y,t)-l);
            cone = cone*cone*cone;
            rgb = fromLinearSrgb(max(float3(dot(cone,float3(4.076742,-3.307712,.230970)),
                dot(cone,float3(-1.268438,2.609757,-.341319)),dot(cone,float3(-.004196,-.703419,1.707615))),0.0));
            return mix(float3(dot(rgb,float3(.2126,.7152,.0722))),rgb,saturation)+brightness;
        }

        float3 onbSample(float2 uv, float2 scale, float2 offset, float3 center) {
            float2 sampleAt = uv+(uMatrix*float3(offset/scale/scale,0.0)).xy;
            half4 sampleColor = uniBDFBitmap.eval(sampleAt);
            return mix(center, sampleColor.rgb/max(.001,sampleColor.a),smoothstep(.0,.8,sampleColor.a));
        }

        """
    private const val CLASSIC_OPTICS = """

        half3 onbFinish(float3 stock, float3 body, float y, float showMaterial) {
            float3 weights = float3(.2126,.7152,.0722);
            body = mix(float3(dot(body,weights)),body,onbToneControls.y)+onbToneControls.z;
            body += (body-.5)*onbTint.y*.6;
            body += float3(.10,.015,-.10)*onbTint.x*(1.0-abs(2.0*y-1.0));
            body = mix(body, float3(onbDimming < 0.0 ? 1.0 : 0.0),
                abs(onbDimming) * (onbDimming < 0.0 ? .24 : .50));
            return half3(mix(stock,clamp(body,0.0,1.0),showMaterial*onbOptics.x));
        }

        half3 onbLiquidMaterial(float3 stock, float3 capture, float2 p, float2 uv, float2 scale,
                float sdf, bool rounded, float showMaterial) {
            float density = onbSoftGlass.x;
            float shortSide = min(u_size.x,u_size.y);
            float2 displacement = float2(0.0);
            float stroke = 0.0;
            if (rounded && uEnableMetaBall == 0 && u_commonArray[6] > 0.0) {
                float2 halfSize = max(float2(u_commonArray[4],u_commonArray[5]),float2(1.0));
                float inset = max(-sdf,0.0);
                float height = min(24.0*density*onbOptics.z,min(halfSize.x,halfSize.y));
                if (inset < height) {
                    float radius = min(u_commonArray[6]*1.5,min(halfSize.x,halfSize.y));
                    float2 pos = p-float2(u_commonArray[2],u_size.y-u_commonArray[3]);
                    float2 q = abs(pos)-halfSize+radius;
                    float2 corner = max(q,0.0);
                    float2 normal = sign(pos)*(length(corner) > .001 ? normalize(corner) :
                        (q.x >= q.y ? float2(1,0) : float2(0,1)));
                    float x = 1.0-inset/height;
                    displacement = -normal*(1.0-sqrt(max(0.0,1.0-x*x)))*min(24.0*density,.5*shortSide)*onbToneControls.x;
                    float2 lamp = float2(-.4,-.6)+onbLampOffset;
                    lamp = float2(.98058*lamp.x+.19612*lamp.y,-.19612*lamp.x+.98058*lamp.y);
                    float rim = 1.0-smoothstep(.25*density,.75*density+.5,inset);
                    stroke = .5*abs(dot(normal,lamp/max(length(lamp),.001)))*rim*onbOptics.w;
                }
            }
            float3 color = capture;
            if (dot(displacement,displacement) > .0001) color = onbSample(uv,scale,displacement,capture);
            float blur = min(8.0*density*onbOptics.y,.30*shortSide);
            if (onbLowPower < .5 && blur > .01) {
                float2 u = float2(.7071,.7071)*blur;
                float2 v = float2(-u.y,u.x);
                color = .2*(color + onbSample(uv,scale,displacement+u,capture) + onbSample(uv,scale,displacement-u,capture)
                    + onbSample(uv,scale,displacement+v,capture) + onbSample(uv,scale,displacement-v,capture));
            }
            float3 weights = float3(.213,.715,.072);
            color = clamp(mix(float3(dot(color,weights)),color,1.5),0.0,1.0);
            float3 body = mix(color,float3(onbLiquidStyle.y),.4);
            float stockChroma = max(max(stock.r,stock.g),stock.b)-min(min(stock.r,stock.g),stock.b);
            float captureChroma = max(max(capture.r,capture.g),capture.b)-min(min(capture.r,capture.g),capture.b);
            body = mix(body,stock,.72*smoothstep(.18,.5,stockChroma-captureChroma));
            body += stroke;
            return onbFinish(stock,body,clamp(dot(body,weights),0.0,1.0),showMaterial);
        }

        half3 onbMaterial(half3 stock, half4 capture, float2 p, float2 uv, float2 scale, float sdf, bool rounded) {
            float density = onbSoftGlass.x;
            float shortSide = min(u_size.x,u_size.y);
            bool workspace = onbLauncher && onbPreviewFolder > .5;
            bool widget = workspace && onbPreviewFolder > 1.5;
            bool folder = workspace && !widget;
            bool slider = !onbLauncher && u_size.y > u_size.x*1.65 && u_size.x < 110.0*density;
            bool volumeTrack = onbPanelSurface.x == 2.0 && slider;
            bool volumeMore = onbPanelSurface.x == 6.0;
            bool sideVolumeGlass = volumeTrack || volumeMore;
            bool externalPanel = onbPanelSurface.x == 4.0 || onbPanelSurface.x == 5.0 || onbPanelSurface.x == 7.0;
            float fill = min(min(stock.r,stock.g),stock.b);
            if (onbKeepWhite > .5 && (onbSelectionState > 1.5 ||
                (onbSelectionState > .5 && fill > .70 && max(max(stock.r,stock.g),stock.b)-fill < .17))) return stock;
            // The dock samples wallpaper directly. A bright wallpaper is still a valid glass backdrop.
            bool dock = onbLauncher && onbPreviewFolder > 4.5;
            // A selected control has one material weight across its native fill and rim.
            bool whiteControl = onbSelectionState > 2.5;
            float showMaterial = (whiteControl ? .22 : (dock ? 1.0 : 1.0-smoothstep(.82,.97,fill)))*smoothstep(.0,.85,capture.a);
            if (showMaterial < .001) return stock;
            float3 color = capture.rgb/max(.001,capture.a);
            if (onbLiquidStyle.x > .5) return onbLiquidMaterial(stock,color,p,uv,scale,sdf,rounded,showMaterial);
            bool diffused = onbPanelSurface.x > .5 || widget;
            if (onbLowPower < .5 && (diffused || onbOptics.y > 1.0)) {
                float radius = widget ? 12.0 : (onbPanelSurface.x == 2.0 || volumeMore ? 23.0 : (onbPanelSurface.x == 4.0 ? 26.0 : 17.0));
                float spread = min(.28*shortSide,radius*density)*(diffused ? onbOptics.y : onbOptics.y-1.0);
                float2 axis = float2(.895,.447)*spread;
                float2 across = float2(-axis.y,axis.x);
                color = .20*(color + onbSample(uv,scale,axis,color) + onbSample(uv,scale,-axis,color)
                    + onbSample(uv,scale,across,color) + onbSample(uv,scale,-across,color));
            }
            float3 original = color;
            float edge = min((workspace?16.7:(slider?20.0:24.0))*density,.45*shortSide);
            if (sideVolumeGlass) edge = min(9.0*density,.18*shortSide);
            edge = min(edge*onbOptics.z,.49*shortSide);
            float thickness = min((workspace?33.3:26.7)*density,.5*shortSide);
            float inset = -sdf;
            float shine = 0.0;
            float stockShine = 0.0;
            bool inRim = rounded && uEnableMetaBall == 0 && inset >= 0.0 && inset < edge && u_commonArray[6] > 0.0;
            if (inRim && onbLowPower > .5) {
                shine = .55*(1.0-smoothstep(0.0,min(edge,1.5*density),inset));
                stockShine = shine;
            } else if (inRim) {
                float2 halfSize = max(float2(u_commonArray[4],u_commonArray[5]),float2(1.0));
                float radius = min(u_commonArray[6],min(halfSize.x,halfSize.y));
                float2 pos = p-float2(u_commonArray[2],u_size.y-u_commonArray[3]);
                float2 q = abs(pos)-halfSize+radius;
                float2 corner = max(q,0.0);
                float2 direction = length(corner) > .001 ? normalize(corner) :
                    (q.x>q.y ? float2(1,0) : float2(0,1));
                float depth = onbHeight(inset/edge,3.8);
                float slope = .5*edge*(onbHeight((inset+1.0)/edge,3.8)-onbHeight((inset-1.0)/edge,3.8));
                float3 normal = normalize(float3(direction*sign(pos)*slope,1.0));
                float2 ray = refract(float3(0,0,-1),normal,slider||workspace ? .3333:.25).xy;
                float2 transmit = ray*2.0*mix(max(thickness-edge,0.0),thickness,depth);
                float2 reflectAt = normal.xy*(2.0*normal.z) * min((workspace?175.0:266.7)*density,2.25*shortSide)*(1.0-depth);
                float3 reflection = onbSample(uv,scale,reflectAt,original);
                float reflectionWeight = pow(max(1.0-depth,0.0),1.0/3.8);
                // Keep the same diffused base on both sides of the rim. At zero displacement
                // the optical correction must vanish, even on a high-contrast backdrop.
                float3 centerSample = capture.rgb/max(.001,capture.a);
                float3 refracted = onbSample(uv,scale,transmit,centerSample);
                color += onbToneControls.x*(mix(refracted,reflection,(workspace?.38:.68)*reflectionWeight) - centerSample);
                color = clamp(color,0.0,1.0);
                color += onbOptics.w * reflection * ((workspace?.13:.21)*(1.0-depth)) *
                    (.3+.7*clamp(1.9-2.15*dot(color,float3(.2126,.7152,.0722)),0.0,1.0));
                float3 lamp = normalize(float3(float2(-.4,-.6) + onbLampOffset,-.8));
                float2 light = max(float2(dot(normal,lamp),dot(normal,float3(-lamp.xy,lamp.z))),0.0);
                float2 arc = max(1.0-.602*sqrt(max(1.0-light,0.0)),0.0);
                shine = dot(light*arc,workspace?float2(1.8,1.0):float2(1.4,.7));
                if (onbLauncher && !workspace) {
                    float3 baseLamp = normalize(float3(-.4,-.6,-.8));
                    float2 baseLight = max(float2(dot(normal,baseLamp),dot(normal,float3(-baseLamp.xy,baseLamp.z))),0.0);
                    stockShine = dot(baseLight*(1.0-.602*sqrt(max(1.0-baseLight,0.0))),float2(1.4,.7));
                }
            }
            float luminance = dot(color,float3(.2126,.7152,.0722));
            if (workspace) {
                // Wallpaper-backed cards and folder previews share a neutral transmission profile.
                float3 transmitted = mix(float3(luminance),color,.90);
                float neutral = mix(.82,.32,smoothstep(.18,.80,luminance));
                color = mix(transmitted,float3(neutral),.13);
            } else if (onbPanelSurface.x == 3.0 || onbPanelSurface.x == 8.0) {
                float3 glass = onbTone(color,float4(0.0,2.0,.3,.4),.15,1.4,.08);
                color = mix(glass,float3(stock),.32);
            } else if (sideVolumeGlass || externalPanel) {
                // The side-key track already has a dark platform tint. Light the diffused
                // backdrop directly so the center keeps the wallpaper's hue.
                color += .10*(1.0-luminance)*(1.0-luminance);
            } else {
                // Control-center sliders share the panel's exposure, including their unfilled track.
                color = onbTone(color,float4(.67,.16,.09,0.0),.24,1.4,-.02);
                float peak = max(max(color.r,color.g),color.b);
                color *= 1.0-.12*smoothstep(.34,.82,peak);
            }
            // Keep state tints, including filled sliders, while replacing the neutral vendor blend.
            float stockChroma = max(max(stock.r,stock.g),stock.b)-min(min(stock.r,stock.g),stock.b);
            float captureChroma = max(max(original.r,original.g),original.b)-min(min(original.r,original.g),original.b);
            float stateTint = smoothstep(.18,.50,stockChroma-captureChroma);
            if (widget) {
                float3 weights = float3(.2126,.7152,.0722);
                color += .70*(dot(stock,weights)-dot(color,weights));
            }
            float3 body = externalPanel ? color : mix(color,float3(stock),(folder ? 0.0 : (slider || volumeMore ? .18 : .10))+.52*stateTint);
            if (onbPanelSurface.x > .5) {
                float localLuma = dot(body,float3(.2126,.7152,.0722));
                float diffuse = mix(onbPanelSurface.y,localLuma,.52);
                float coverage = onbPanelSurface.x == 2.0 || volumeMore ? .48 : (onbPanelSurface.x == 3.0 || onbPanelSurface.x == 8.0 ? .22 :
                    (externalPanel ? .20 : .34));
                float3 diffuseColor = float3(diffuse);
                // Keep the exposure of the frosted surface while transmitting its blurred backdrop hue.
                if (sideVolumeGlass || externalPanel) diffuseColor += .84*(body-localLuma);
                body = mix(body,diffuseColor,coverage*(1.0-.65*stateTint));
            }
            float y = clamp(dot(body,float3(.2126,.7152,.0722)),0.0,1.0);
            float ambient = smoothstep(0.0,.5,y);
            float weight = smoothstep(0.0,1.0,shine)*mix(.75,.4,ambient);
            float3 highlight = min((body+mix(1.0-y,y,ambient))*weight, float3(workspace?.115:.16));
            float3 baseHighlight = min((body+mix(1.0-y,y,ambient))*(smoothstep(0.0,1.0,stockShine)*mix(.75,.4,ambient)),float3(.16));
            body += onbOptics.w*highlight;
            float bottom = smoothstep(0.0,1.0,p.y/max(1.0,u_size.y));
            body += (workspace?.009:.018)*bottom*bottom;
            float3 weights = float3(.2126,.7152,.0722);
            if (onbLauncher && !workspace) {
                // Preserve the adaptive tint on search/menu surfaces at the default settings.
                body = mix(float3(stock),dot(stock,weights)+original-dot(original,weights),.08);
                body += (onbOptics.w-1.0)*highlight + (onbToneControls.x-1.0)*(color-original)*.12;
                body += highlight-baseHighlight;
            }
            return onbFinish(stock,body,y,showMaterial);
        }
    """
}
