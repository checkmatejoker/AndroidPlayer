#extension GL_OES_EGL_image_external : require
precision mediump float;
varying vec2 v_texPosition;
uniform samplerExternalOES sTexture;

uniform int sampler_split_screen;

void main() {
    gl_FragColor=texture2D(sTexture, v_texPosition);

    vec2 uv = v_texPosition.xy;
    float px = uv.x,py = uv.y;
    if(sampler_split_screen == 1){
        px = uv.x;
        py = uv.y;
    } else if(sampler_split_screen == 2){//2分屏
        py = uv.y;
        if (uv.x >= 0.0 && uv.x <= 0.5) { // 0.0～0.5 范围内显示0～1范围的像素
            px = uv.x * 2.0;
        } else {// 0.5～1 范围内显示0～1范围的像素
            px = (uv.x - 0.5) * 2.0;
        }
    }
    gl_FragColor = texture2D(sTexture, vec2(px, py));
}

