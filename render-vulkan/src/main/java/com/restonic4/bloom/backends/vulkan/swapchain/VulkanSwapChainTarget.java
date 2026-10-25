package com.restonic4.bloom.backends.vulkan.swapchain;

import com.restonic4.bloom.backends.vulkan.device.VulkanLogicalDevice;
import com.restonic4.bloom.backends.vulkan.device.VulkanPhysicalDevice;
import com.restonic4.bloom.core.Disposable;

import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkDeviceWaitIdle;

/**
 * <p>Owns everything that depends on the window's size: the swapchain and its image views.</p>
 * <p>Nothing outside this class should keep its own copy of the image handles, because {@link #recreate} replaces them.</p>
 */
public final class VulkanSwapChainTarget implements Disposable {
    private final VulkanPhysicalDevice physicalDevice;
    private final VulkanLogicalDevice logicalDevice;
    private final long surface;

    private VulkanSwapChain swapChain;
    private VulkanSwapChainImageViews imageViews;

    private VulkanSwapChainTarget(
            VulkanPhysicalDevice physicalDevice, VulkanLogicalDevice logicalDevice,long surface,
            VulkanSwapChain swapChain, VulkanSwapChainImageViews imageViews
    ) {
        this.physicalDevice = physicalDevice;
        this.logicalDevice = logicalDevice;
        this.surface = surface;
        this.swapChain = swapChain;
        this.imageViews = imageViews;
    }

    public static VulkanSwapChainTarget create(VulkanPhysicalDevice physicalDevice, VulkanLogicalDevice logicalDevice, long surface, long window) {
        VulkanSwapChain swapChain = VulkanSwapChain.create(physicalDevice, logicalDevice, surface, window);

        try {
            VulkanSwapChainImageViews imageViews = VulkanSwapChainImageViews.create(logicalDevice, swapChain);
            return new VulkanSwapChainTarget(physicalDevice, logicalDevice, surface, swapChain, imageViews);
        } catch (RuntimeException e) {
            swapChain.dispose();
            throw e;
        }
    }

    /** @return whether the image format changed, which invalidates pipelines built for the old one */
    public boolean recreate(long window) {
        int idleResult = vkDeviceWaitIdle(logicalDevice.getHandle());
        if (idleResult != VK_SUCCESS) {
            throw new IllegalStateException("Failed to wait for device idle: " + idleResult);
        }

        VulkanSwapChain newSwapChain = VulkanSwapChain.create(physicalDevice, logicalDevice, surface, window, swapChain.getHandle());

        VulkanSwapChainImageViews newImageViews;
        try {
            newImageViews = VulkanSwapChainImageViews.create(logicalDevice, newSwapChain);
        } catch (RuntimeException e) {
            newSwapChain.dispose();
            throw e;
        }

        boolean formatChanged = newSwapChain.getImageFormat() != swapChain.getImageFormat();

        imageViews.dispose();
        swapChain.dispose();

        swapChain = newSwapChain;
        imageViews = newImageViews;

        return formatChanged;
    }

    public VulkanSwapChain getSwapChain() { return swapChain; }
    public VulkanSwapChainImageViews getImageViews() { return imageViews; }

    @Override
    public void dispose() {
        imageViews.dispose();
        swapChain.dispose();
    }
}
