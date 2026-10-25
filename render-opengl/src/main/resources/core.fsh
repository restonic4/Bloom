#version 330 core

in float vShade;
in vec2 vUv;

uniform vec4 uColor;
uniform sampler2D uTexture;

out vec4 FragColor;

void main() {
    FragColor = texture(uTexture, vUv);
}
