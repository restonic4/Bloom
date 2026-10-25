package com.restonic4.bloom.core.graphics;

import com.restonic4.bloom.core.Resource;

public record ShaderSource(String path, ShaderStage stage) {
    public String loadSource() { return Resource.loadString(path); }
    public static ShaderSource from(String path, ShaderStage stage) { return new ShaderSource(path, stage); }
}