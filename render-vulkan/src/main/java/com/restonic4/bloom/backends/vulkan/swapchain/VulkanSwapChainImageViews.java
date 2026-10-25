package com.restonic4.bloom.backends.vulkan.swapchain;

import com.restonic4.bloom.backends.vulkan.device.VulkanLogicalDevice;
import com.restonic4.bloom.core.Disposable;

import java.util.ArrayList;
import java.util.List;

public final class VulkanSwapChainImageViews implements Disposable {
    private final List<VulkanImageView> views;

    private VulkanSwapChainImageViews(List<VulkanImageView> views) {
        this.views = List.copyOf(views);
    }

    public static VulkanSwapChainImageViews create(VulkanLogicalDevice logicalDevice, VulkanSwapChain swapChain) {
        long[] images = swapChain.getImageHandles();
        int format = swapChain.getImageFormat();

        List<VulkanImageView> views = new ArrayList<>(images.length);

        try {
            for (long image : images) {
                views.add(VulkanImageView.create(logicalDevice, image, format));
            }

            return new VulkanSwapChainImageViews(views);
        } catch (RuntimeException e) {
            for (int i = views.size() - 1; i >= 0; i--) {
                views.get(i).dispose();
            }

            throw e;
        }
    }

    public int size() { return views.size(); }
    public VulkanImageView get(int index) { return views.get(index); }
    public List<VulkanImageView> getViews() { return views; }

    @Override
    public void dispose() {
        for (int i = views.size() - 1; i >= 0; i--) {
            views.get(i).dispose();
        }
    }
}