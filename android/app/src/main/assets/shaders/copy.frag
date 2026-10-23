#version 300 es
precision highp float;
in vec2 texCoord; uniform sampler2D image; out vec4 outputColor;
void main(){ outputColor=texture(image,texCoord); }
