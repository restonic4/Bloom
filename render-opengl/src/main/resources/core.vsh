#version 330 core

layout(location = 0) in vec3 aPosition;
layout(location = 3) in vec2 aUv;
layout(location = 4) in mat4 aModel;

uniform mat4 uProjection;
uniform mat4 uView;

out float vShade;
out vec2 vUv;

void main() {
    gl_Position = uProjection * uView * aModel * vec4(aPosition, 1.0);

    // Test only, same formula as the Vulkan shader: further from the camera is darker
    vShade = max(1.0 - 0.03 * gl_Position.w, 0.1);

    vUv = aUv;
}
