attribute vec4 av_Position;//顶点位置
attribute vec2 af_Position;//纹理位置
varying vec2 ft_Position;//纹理位置  与fragment_shader交互
void main() {
    ft_Position = af_Position;
    gl_Position = av_Position;
}
