#extension GL_OES_EGL_image_external : require
precision mediump float;

varying vec2 vTexCoord;
uniform samplerExternalOES uTexture;

// 0 = none, 1 = classic B&W, 2 = cyberpunk, 3 = vintage 90s, 4 = cinematic
uniform int uFilterType;
uniform float uTime;        // drives film-grain animation for vintage look
uniform bool uLetterbox;    // cinematic-only optional letterbox bars

float luma(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

float grain(vec2 uv, float t) {
    return fract(sin(dot(uv * t, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec4 color = texture2D(uTexture, vTexCoord);
    vec3 rgb = color.rgb;

    if (uFilterType == 1) {
        // Classic B&W — high contrast, gritty
        float y = luma(rgb);
        y = clamp((y - 0.5) * 1.35 + 0.5 - 0.02, 0.0, 1.0);
        rgb = vec3(y);
    } else if (uFilterType == 2) {
        // Cyberpunk — neon pink/blue/purple grade
        rgb.r = clamp(rgb.r * 1.15, 0.0, 1.0);
        rgb.g = clamp(rgb.g * 0.85, 0.0, 1.0);
        rgb.b = clamp(rgb.b * 1.30, 0.0, 1.0);
        float y = luma(rgb);
        rgb = mix(vec3(y), rgb, 1.6); // vibrance boost
    } else if (uFilterType == 3) {
        // Vintage 90s — warm sepia + animated VHS grain
        vec3 sepia = vec3(
            dot(rgb, vec3(0.393, 0.769, 0.189)),
            dot(rgb, vec3(0.349, 0.686, 0.168)),
            dot(rgb, vec3(0.272, 0.534, 0.131))
        );
        rgb = mix(rgb, sepia, 0.35);
        float n = grain(vTexCoord, uTime) - 0.5;
        rgb += n * 0.06;
    } else if (uFilterType == 4) {
        // Cinematic — desaturated, slightly contrasty, optional letterbox
        float y = luma(rgb);
        rgb = mix(vec3(y), rgb, 0.6);
        rgb = clamp((rgb - 0.5) * 1.1 + 0.5, 0.0, 1.0);
        if (uLetterbox) {
            float barHeight = 0.10;
            if (vTexCoord.y < barHeight || vTexCoord.y > 1.0 - barHeight) {
                rgb = vec3(0.0);
            }
        }
    }
    // uFilterType == 0 → passthrough, no change

    gl_FragColor = vec4(rgb, color.a);
}
