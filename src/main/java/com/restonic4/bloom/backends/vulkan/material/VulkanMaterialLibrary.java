package com.restonic4.bloom.backends.vulkan.material;

import com.restonic4.bloom.backends.vulkan.command.VulkanCommandPool;
import com.restonic4.bloom.backends.vulkan.descriptor.VulkanDescriptorBinding;
import com.restonic4.bloom.backends.vulkan.descriptor.VulkanDescriptorPool;
import com.restonic4.bloom.backends.vulkan.descriptor.VulkanDescriptorSetLayout;
import com.restonic4.bloom.backends.vulkan.descriptor.VulkanDescriptorWriter;
import com.restonic4.bloom.backends.vulkan.device.VulkanLogicalDevice;
import com.restonic4.bloom.backends.vulkan.image.VulkanImage;
import com.restonic4.bloom.backends.vulkan.image.VulkanSampler;
import com.restonic4.bloom.core.Disposable;
import com.restonic4.bloom.core.scene.ImageData;
import com.restonic4.bloom.core.scene.Material;
import com.restonic4.bloom.core.scene.Texture;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.vulkan.VK10.*;

// GPU side of core materials: one descriptor set (set 1 of the pipeline layout) per material, holding its texture.
// GPU images and sets are created the first time a material is asked for and live until dispose().
public final class VulkanMaterialLibrary implements Disposable {
    public static final int SET_INDEX = 1;

    private static final int MAX_MATERIALS = 64;

    private final VulkanLogicalDevice logicalDevice;
    private final VulkanCommandPool commandPool;
    private final VulkanDescriptorSetLayout setLayout;
    private final VulkanDescriptorPool pool;
    private final VulkanSampler sampler;

    private final Map<Texture, VulkanImage> images = new HashMap<>();
    private final Map<Material, Long> sets = new HashMap<>();

    public VulkanMaterialLibrary(VulkanLogicalDevice logicalDevice, VulkanCommandPool commandPool) {
        this.logicalDevice = logicalDevice;
        this.commandPool = commandPool;

        setLayout = VulkanDescriptorSetLayout.create(
                logicalDevice,
                new VulkanDescriptorBinding(0, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, VK_SHADER_STAGE_FRAGMENT_BIT)
        );
        pool = VulkanDescriptorPool.create(logicalDevice, MAX_MATERIALS, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
        sampler = VulkanSampler.create(logicalDevice);
    }

    public long setFor(Material material) {
        Long existing = sets.get(material);
        if (existing != null) return existing;

        VulkanImage image = images.computeIfAbsent(material.albedo(), this::upload);

        long set = pool.allocateSets(setLayout, 1)[0];
        VulkanDescriptorWriter.writeCombinedImageSampler(logicalDevice, set, 0, image.getViewHandle(), sampler.getHandle());

        sets.put(material, set);
        return set;
    }

    private VulkanImage upload(Texture texture) {
        ImageData data = texture.image();

        VulkanImage image = VulkanImage.create(
                logicalDevice,
                data.width(),
                data.height(),
                VulkanImage.mipLevelsFor(data.width(), data.height()),
                VK_FORMAT_R8G8B8A8_SRGB,
                VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT,
                VK_IMAGE_ASPECT_COLOR_BIT
        );

        try {
            image.upload(logicalDevice, commandPool, data.pixels());
        } catch (RuntimeException e) {
            image.dispose();
            throw e;
        }

        return image;
    }

    public VulkanDescriptorSetLayout getSetLayout() { return setLayout; }

    @Override
    public void dispose() {
        images.values().forEach(VulkanImage::dispose);
        sampler.dispose();
        pool.dispose();
        setLayout.dispose();
    }
}
