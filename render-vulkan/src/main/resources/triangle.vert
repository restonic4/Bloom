#version 450

layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec2 inUv;
layout(location = 2) in mat4 inModel;

layout(location = 0) out float outShade;
layout(location = 1) out vec2 outUv;

// Descriptor set 0, slot 0: the current frame slot's camera buffer
layout(set = 0, binding = 0) uniform Camera {
    mat4 viewProj;
} camera;

void main() {
    gl_Position = camera.viewProj * inModel * vec4(inPosition, 1.0);

    // Test only, same formula as the OpenGL shader: further from the camera is darker
    outShade = max(1.0 - 0.03 * gl_Position.w, 0.1);

    outUv = inUv;
}