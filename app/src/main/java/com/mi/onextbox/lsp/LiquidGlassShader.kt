package com.mi.onextbox.lsp

internal object LiquidGlassShader {
    const val DECLARATION = "// onbLiquidGlassRevision=12"
    const val SOURCE = """
float lgSurfaceConvexSquircle(float x) { return pow(1.0 - pow(1.0 - x, 4.0), 0.25); }
float2 lgRefractRay(float2 normal, float eta) {
    float cosI = -normal.y;
    float k    = 1.0 - eta * eta * (1.0 - cosI * cosI);
    if (k < 0.0) return float2(0.0);
    float kSqrt = sqrt(k);
    return float2(
        -(eta * cosI + kSqrt) * normal.x,
         eta - (eta * cosI + kSqrt) * normal.y
    );
}

float lgRawRefraction(float bezelRatio, float glassThickness, float bezelWidth, float eta) {
    float x     = clamp(bezelRatio, 0.05, 0.95);
    float y     = lgSurfaceConvexSquircle(x);
    float y2    = lgSurfaceConvexSquircle(x + 0.001);
    float deriv = (y2 - y) / 0.001;
    float mag   = sqrt(deriv * deriv + 1.0);
    float2 n    = float2(-deriv / mag, -1.0 / mag);
    float2 r    = lgRefractRay(n, eta);
    if (length(r) < 0.0001 || abs(r.y) < 0.0001) return 0.0;
    float remaining = y * bezelWidth + glassThickness;
    return r.x * (remaining / r.y);
}

float lgDisplacementAtRatio(float bezelRatio, float glassThickness, float bezelWidth, float eta) {
    float peak = lgRawRefraction(0.05, glassThickness, bezelWidth, eta);
    if (abs(peak) < 0.0001) return 0.0;
    float raw     = lgRawRefraction(bezelRatio, glassThickness, bezelWidth, eta);
    float norm    = raw / peak;
    float falloff = 1.0 - smoothstep(0.0, 1.0, bezelRatio);
    return norm * falloff;
}

float lgLinearizeSRGB(float c) {
    return c > 0.04045 ? pow((c + 0.055) / 1.055, 2.4) : c / 12.92;
}

float lgGammaCorrectSRGB(float c) {
    return c <= 0.0031308 ? 12.92 * c : 1.055 * pow(c, 1.0 / 2.4) - 0.055;
}

float3 lgSrgbToXyz(float3 rgb) {
    float3 lin = float3(lgLinearizeSRGB(rgb.r), lgLinearizeSRGB(rgb.g), lgLinearizeSRGB(rgb.b));
    return float3(
        dot(lin, float3(0.4124, 0.3576, 0.1805)),
        dot(lin, float3(0.2126, 0.7152, 0.0722)),
        dot(lin, float3(0.0193, 0.1192, 0.9505))
    );
}

float3 lgXyzToSrgb(float3 xyz) {
    float3 lin = float3(
        dot(xyz, float3( 3.2406, -1.5372, -0.4986)),
        dot(xyz, float3(-0.9689,  1.8758,  0.0415)),
        dot(xyz, float3( 0.0557, -0.2040,  1.0570))
    );
    return clamp(float3(lgGammaCorrectSRGB(lin.r), lgGammaCorrectSRGB(lin.g), lgGammaCorrectSRGB(lin.b)), 0.0, 1.0);
}

float lgLabF(float t) {
    return t > 0.00885645167 ? pow(t, 1.0 / 3.0) : (7.787037 * t + 16.0 / 116.0);
}

float lgLabInvF(float t) {
    float t3 = t * t * t;
    return t3 > 0.00885645167 ? t3 : (t - 16.0 / 116.0) / 7.787037;
}

float3 lgXyzToLab(float3 xyz) {
    float3 n = xyz / float3(0.95047, 1.0, 1.08883);
    float fx = lgLabF(n.x), fy = lgLabF(n.y), fz = lgLabF(n.z);
    return float3(116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz));
}

float3 lgLabToXyz(float3 lab) {
    float fy = (lab.x + 16.0) / 116.0;
    float fx = fy + lab.y / 500.0;
    float fz = fy - lab.z / 200.0;
    return float3(0.95047 * lgLabInvF(fx), lgLabInvF(fy), 1.08883 * lgLabInvF(fz));
}

// Keep backdrop samples in the OEM texture's coordinate space, including when
// the launcher maps a small, translated RenderNode onto the screen capture.
// Each half-cell integrates the two neighboring samples of the reduced backdrop.
float2 lgTentTap(float part, float fraction, float pitch) {
    float k = part*.5-1.0;
    float l = (1.0-fraction)*max(0.0,1.0-abs(k)) + fraction*max(0.0,1.0-abs(k-1.0));
    float r = (1.0-fraction)*max(0.0,1.0-abs(k+.5)) + fraction*max(0.0,1.0-abs(k-.5));
    float mass = l+r;
    float correction = max(0.0,1.0-4.0/(pitch*pitch))/6.0;
    return float2((k+.25+.5*correction*(r-l)/max(mass,.0001))*pitch,.25*mass);
}

float2 lgGaussianTap(int index, bool dock) {
    float direction = index < 3 ? -1.0 : 1.0;
    // Weighted centroids of the inner, middle and outer bands of the Dock wallpaper filter.
    if (dock) {
        if (index==0 || index==5) return float2(1.938677*direction,.0668072);
        if (index==1 || index==4) return float2(1.073831*direction,.1598202);
        return float2(.357771*direction,.2733726);
    }
    if (index==0 || index==5) return float2(2.5067*direction,.01578);
    if (index==1 || index==4) return float2(1.4696*direction,.12541);
    return float2(.488*direction,.35881);
}

float3 lgBackdrop(float2 p, float2 uv, float2 scale, float2 displacement, float3 capture, float blur, float quality) {
    float pitch = 1.0/max(.1,quality);
    if (blur <= .01 && pitch <= 1.001) return onbSample(uv,scale,displacement,capture);
    if (onbLowPower > .5) {
        float spread = min(4.0*onbSoftGlass.x,sqrt(blur*blur+(pitch*pitch-1.0)/12.0));
        float2 u = float2(.92388,.38268)*spread;
        float2 v = float2(-u.y,u.x);
        return .25*(onbSample(uv,scale,displacement+u,capture)+onbSample(uv,scale,displacement-u,capture)
            + onbSample(uv,scale,displacement+v,capture)+onbSample(uv,scale,displacement-v,capture));
    }
    float2 pixel = p+displacement;
    float2 grid = pixel/pitch-.5;
    float2 phase = fract(grid);
    float2 origin = (floor(grid)+.5)*pitch-p;
    bool dock = onbLauncher && onbPreviewFolder > 4.5;
    float2 axis = dock ? float2(.9238795,.3826834) : float2(.8660254,.5);
    float2 sigma = sqrt(blur*blur+(pitch*pitch-1.0)/(dock ? 12.0 : 6.0) + pitch*pitch*phase*(1.0-phase));
    float3 sum = float3(0.0);
    for (int row=0; row<6; ++row) {
        float2 y = blur <= .01 ? lgTentTap(float(row),phase.y,pitch) : lgGaussianTap(row,dock);
        for (int col=0; col<6; ++col) {
            float2 x = blur <= .01 ? lgTentTap(float(col),phase.x,pitch) : lgGaussianTap(col,dock);
            // Rotate the Gaussian grid to avoid aliasing horizontal and vertical detail.
            float2 offset = blur <= .01 ? origin+float2(x.x,y.x) :
                displacement+sigma*float2(axis.x*x.x-axis.y*y.x,axis.y*x.x+axis.x*y.x);
            sum += (x.y*y.y)*onbSample(uv,scale,offset,capture);
        }
    }
    return sum;
}

float3 lgHighlight(float3 base, float3 lab, float sdf, float bezel, float opacity, float lobe) {
    float inset = max(0.0,-sdf);
    float edge = clamp(1.0-max(0.0,sdf),0.0,1.0);
    float stroke = clamp(1.0-inset/2.0,0.0,1.0);
    float fresnel = pow(clamp(1.0-inset/bezel,0.0,1.0),2.2);
    float shine = (lobe*stroke*opacity*2.15 + fresnel*.34)*edge;
    lab.x = clamp(lab.x+shine*36.0,0.0,100.0);
    lab.yz *= max(0.0,1.0-shine*9.0/max(length(lab.yz),.0001));
    return mix(base,lgXyzToSrgb(lgLabToXyz(lab)),clamp(shine*.70,0.0,1.0));
}

float lgLobe(float2 q, float2 normal) {
    float incidence = abs(dot(normal,float2(-.64278761,-.76604444)));
    return min(q.x,q.y)>0.0 ? smoothstep(.46,.90,incidence) : smoothstep(.66,.86,incidence);
}

half3 onbPluginMaterial(float3 capture, float2 p, float2 uv, float2 scale, float2 pos, float2 halfSize, float nativeRadius, float sdf) {
    float4 optics, finish, tint;
    float sampling;
    if (onbLauncher && onbPreviewFolder > 4.5) {
            optics = onbLgOptics[4]; finish = onbLgFinish[4]; tint = onbLgTint[4]; sampling = onbLgSampling[4];
    } else if (onbLauncher && onbPreviewFolder > 3.5) {
            optics = onbLgOptics[3]; finish = onbLgFinish[3]; tint = onbLgTint[3]; sampling = onbLgSampling[3];
    } else if (onbLauncher && onbPreviewFolder > 1.5) {
            optics = onbLgOptics[2]; finish = onbLgFinish[2]; tint = onbLgTint[2]; sampling = onbLgSampling[2];
    } else if (onbLauncher && onbPreviewFolder > .5) {
            optics = onbLgOptics[1]; finish = onbLgFinish[1]; tint = onbLgTint[1]; sampling = onbLgSampling[1];
    } else if (onbPanelSurface.x == 1.0) {
            optics = onbLgOptics[5]; finish = onbLgFinish[5]; tint = onbLgTint[5]; sampling = onbLgSampling[5];
    } else if (onbPanelSurface.x == 3.0 || onbPanelSurface.x == 8.0 || onbPanelSurface.x >= 10.0) {
            optics = onbLgOptics[6]; finish = onbLgFinish[6]; tint = onbLgTint[6]; sampling = onbLgSampling[6];
    } else {
            optics = onbLgOptics[0]; finish = onbLgFinish[0]; tint = onbLgTint[0]; sampling = onbLgSampling[0];
    }

    float density = onbSoftGlass.x;
    float diameter = 2.0*min(halfSize.x,halfSize.y);
    if (onbLgUnified < .5 && onbPanelSurface.x == 1.0 && max(halfSize.x,halfSize.y) <= 1.18*min(halfSize.x,halfSize.y)) {
        if (2.0*max(halfSize.x,halfSize.y) <= 110.0*density) {
            // Small controls use the passcode lens shape with the control center's own tint.
            optics *= float4(30.0/18.0,80.0/120.0,1.5/1.2,1.0/1.35);
            finish.xy *= float2(3.0/10.0,.60/.55);
            sampling *= 2.0;
        } else if (diameter >= 120.0*density) {
            optics *= onbLgOptics[2]/float4(18,120,1.2,1.35);
            finish.xy *= onbLgFinish[2].xy/float2(10,.55);
            sampling *= onbLgSampling[2]/.25;
        }
        optics.z = max(1.0,optics.z);
        sampling = clamp(sampling,.1,1.0);
        finish.y = clamp(finish.y,0.0,1.0);
    }
    float2 q = abs(pos)-halfSize+clamp(nativeRadius,0.0,diameter*.5);
    float2 corner = max(q,0.0);
    float radial = length(corner);
    float2 normal = sign(pos)*(radial > .0001 ? corner/radial :
        (q.x > q.y ? float2(1,0) : float2(0,1)));
    float bezel = max(.001,optics.x*density);
    // A material without its own contour can be clipped later by its host.
    float inset = sdf <= 0.0 ? -sdf : 1e6;
    float ratio = clamp(inset/bezel,0.0,1.0);
    float2 displacement = float2(0);
    if (inset < bezel && optics.z > 1.0) {
        displacement = -normal*lgDisplacementAtRatio(ratio,optics.y,bezel,1.0/optics.z)*bezel*optics.w;
    }
    float sigma = finish.x*density*.5;
    float quality = min(sampling,clamp(2.4*max(halfSize.x,halfSize.y)*2.0/max(onbSoftGlass.y,onbSoftGlass.z),.16,1.0));
    if (onbLauncher) quality=min(quality,onbPreviewFolder<1.5 && onbPreviewFolder>.5 ? .28 :
        (onbPreviewFolder>1.5 && onbPreviewFolder<3.5 ? .34 : .42));
    else if (onbPanelSurface.x==3.0 || onbPanelSurface.x==8.0) quality=min(quality,.65);
    if (onbLauncher && onbPreviewFolder > 4.5 && 2.0*sigma*quality < .5) sigma = 0.0;
    float3 color = lgBackdrop(p,uv,scale,displacement,capture,sigma,quality);
    float dispersion = min(length(displacement/max(onbSoftGlass.yz,float2(1)))*24.0,.012);
    if (onbLowPower < .5 && dispersion > .00001) {
        float2 prism = displacement*.55*dispersion*80.0;
        color.rb = mix(color.rb,float2(lgBackdrop(p,uv,scale,displacement-prism,capture,sigma,quality).r,
            lgBackdrop(p,uv,scale,displacement+prism,capture,sigma,quality).b), dispersion*.65);
    }
    if (onbLowPower > .5 && onbOptics.w > 0.0) {
        float stroke = (1.0-smoothstep(0.0,2.0,inset))*finish.y*lgLobe(q,normal);
        float fresnel = (1.0-ratio)*(1.0-ratio);
        color += (1.0-color)*(.24*stroke+.08*fresnel);
    } else if (onbOptics.w > 0.0 && (inset < 3.0 || ratio < 1.0)) {
        float3 lab = lgXyzToLab(lgSrgbToXyz(clamp(color,0.0,1.0)));
        if (inset < 3.0) {
            // Integrate the lit color, including its nonlinear tone curve, over the
            // pixel. Reuse its backdrop and leave final coverage to the host.
            float3 sum = float3(0);
            float mass = 0.0;
            for (int i=0;i<4;++i) {
                float2 tap = i==0 ? float2(-.375,-.125) : i==1 ? float2(.125,-.375) :
                    i==2 ? float2(.375,.125) : float2(-.125,.375);
                float d = sdf+dot(tap,normal);
                float weight = clamp(1.0-max(0.0,d),0.0,1.0);
                float2 tapQ = q+abs(pos+tap)-abs(pos);
                float2 arc = max(tapQ,0.0);
                float2 n = dot(arc,arc)>.000001 ? sign(pos+tap)*normalize(arc) : normal;
                sum += weight*lgHighlight(color,lab,d,bezel,finish.y,lgLobe(tapQ,n));
                mass += weight;
            }
            color = sum/max(mass,.0001);
        } else {
            color = lgHighlight(color,lab,sdf,bezel,finish.y,0.0);
        }
    }
    if (tint.a < 0.0) {
        bool dark = tint.a == -3.0 || (tint.a == -1.0 &&
            (onbPanelSurface.y > .0 ? onbPanelSurface.y < .5 : onbLiquidStyle.y < .5));
        tint = dark ? float4(0,0,0,finish.w) : float4(1,1,1,finish.z);
    }
    return half3(mix(color,tint.rgb,tint.a));
}
    """
}
