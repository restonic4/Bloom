package com.restonic4.bloom.backends.vulkan.image;

import com.restonic4.bloom.backends.vulkan.device.VulkanLogicalDevice;
import com.restonic4.bloom.core.Disposable;

import static org.lwjgl.vulkan.VK10.*;

public final class VulkanDepthImage implements Disposable {
    // The spec requires every device to support this format as a depth attachment
    public static final int FORMAT = VK_FORMAT_D32_SFLOAT;

    private final VulkanImage image;

    private VulkanDepthImage(VulkanImage image) {
        this.image = image;
    }

    public static VulkanDepthImage create(VulkanLogicalDevice logicalDevice, int width, int height) {
        return new VulkanDepthImage(VulkanImage.create(
                logicalDevice, width, height, FORMAT,
                VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT, VK_IMAGE_ASPECT_DEPTH_BIT
        ));
    }

    public long getImageHandle() { return image.getImageHandle(); }
    public long getViewHandle() { return image.getViewHandle(); }

    @Override
    public void dispose() {
        image.dispose();
    }
}
