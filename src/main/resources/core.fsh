#version 330 core

in float vShade;

uniform vec4 uColor;

out vec4 FragColor;

void main() {
    FragColor = vec4(uColor.rgb * vShade, uColor.a);
}