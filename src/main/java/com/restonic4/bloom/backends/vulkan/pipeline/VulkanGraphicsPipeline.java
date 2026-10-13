package com.restonic4.bloom.backends.vulkan.pipeline;

import com.restonic4.bloom.backends.vulkan.device.VulkanLogicalDevice;
import com.restonic4.bloom.backends.vulkan.shader.VulkanShaderStage;
import com.restonic4.bloom.core.Disposable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.Objects;

import static org.lwjgl.vulkan.VK10.*;

public final class VulkanGraphicsPipeline implements Disposable {
    private final VkDevice device;
    private final VulkanPipelineLayout layout;
    private final long handle;

    private VulkanGraphicsPipeline(VkDevice device, VulkanPipelineLayout layout, long handle) {
        this.device = device;
        this.layout = layout;
        this.handle = handle;
    }

    public static VulkanGraphicsPipeline create(
            VulkanLogicalDevice logicalDevice,
            VulkanPipelineLayout layout,
            int colorFormat,
            VulkanShaderStage... shaderStages
    ) {
        Objects.requireNonNull(logicalDevice, "logicalDevice");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(shaderStages, "shaderStages");

        if (colorFormat == VK_FORMAT_UNDEFINED) {
            throw new IllegalArgumentException("Color format must be defined");
        }

        validateShaderStages(shaderStages);

        VkDevice device = logicalDevice.getHandle();

        try (MemoryStack stack = MemoryStack.stackPush()) {

            // Shader stages

            VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(shaderStages.length, stack);
            for (int i = 0; i < shaderStages.length; i++) {
                stages.get(i).set(shaderStages[i].createInfo(stack));
            }

            // Vertex input: Hardcoded triangle

            VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                    .sType$Default();

            // Input assembly

            VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                    .primitiveRestartEnable(false);

            // Viewport and scissor

            VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .viewportCount(1)
                    .scissorCount(1);

            // Rasterization

            VkPipelineRasterizationStateCreateInfo rasterization = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .depthClampEnable(false)
                    .rasterizerDiscardEnable(false)
                    .polygonMode(VK_POLYGON_MODE_FILL)
                    .cullMode(VK_CULL_MODE_NONE)
                    .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
                    .depthBiasEnable(false)
                    .lineWidth(1.0f);

            // Multisampling

            VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT)
                    .sampleShadingEnable(false);

            // Color blending: write RGBA, no blending

            VkPipelineColorBlendAttachmentState.Buffer colorAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack);

            colorAttachment.get(0)
                    .blendEnable(false)
                    .colorWriteMask(
                        VK_COLOR_COMPONENT_R_BIT
                            | VK_COLOR_COMPONENT_G_BIT
                            | VK_COLOR_COMPONENT_B_BIT
                            | VK_COLOR_COMPONENT_A_BIT
                    );

            VkPipelineColorBlendStateCreateInfo colorBlending = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .logicOpEnable(false)
                    .logicOp(VK_LOGIC_OP_COPY)
                    .attachmentCount(1)
                    .pAttachments(colorAttachment);

            // Dynamic state

            VkPipelineDynamicStateCreateInfo dynamicState = VkPipelineDynamicStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR));

            // Dynamic rendering

            VkPipelineRenderingCreateInfo renderingInfo = VkPipelineRenderingCreateInfo.calloc(stack)
                    .sType$Default()
                    .colorAttachmentCount(1)
                    .pColorAttachmentFormats(stack.ints(colorFormat))
                    .depthAttachmentFormat(VK_FORMAT_UNDEFINED)
                    .stencilAttachmentFormat(VK_FORMAT_UNDEFINED);

            // Graphics pipeline

            VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack);

            pipelineInfo.get(0)
                    .sType$Default()
                    .pNext(renderingInfo.address())
                    .stageCount(shaderStages.length)
                    .pStages(stages)
                    .pVertexInputState(vertexInput)
                    .pInputAssemblyState(inputAssembly)
                    .pViewportState(viewportState)
                    .pRasterizationState(rasterization)
                    .pMultisampleState(multisampling)
                    .pColorBlendState(colorBlending)
                    .pDynamicState(dynamicState)
                    .layout(layout.getHandle())
                    .renderPass(VK_NULL_HANDLE)
                    .subpass(0)
                    .basePipelineHandle(VK_NULL_HANDLE)
                    .basePipelineIndex(-1);

            LongBuffer pPipeline = stack.mallocLong(1);

            int result = vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, pipelineInfo, null, pPipeline);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("Failed to create graphics pipeline: " + result);
            }

            return new VulkanGraphicsPipeline(device, layout, pPipeline.get(0));
        }
    }

    private static void validateShaderStages(VulkanShaderStage[] stages) {
        if (stages.length == 0) throw new IllegalArgumentException("At least vertex and fragment shaders are required");

        boolean vertex = false;
        boolean fragment = false;
        boolean geometry = false;

        for (VulkanShaderStage shaderStage : stages) {
            Objects.requireNonNull(shaderStage, "shaderStage");

            int stage = shaderStage.stage();

            if (stage == VK_SHADER_STAGE_VERTEX_BIT) {
                if (vertex) throw new IllegalArgumentException("Duplicate vertex shader stage");
                vertex = true;
            } else if (stage == VK_SHADER_STAGE_FRAGMENT_BIT) {
                if (fragment) throw new IllegalArgumentException("Duplicate fragment shader stage");
                fragment = true;
            } else if (stage == VK_SHADER_STAGE_GEOMETRY_BIT) {
                if (geometry) throw new IllegalArgumentException("Duplicate geometry shader stage");
                geometry = true;
            } else {
                throw new IllegalArgumentException("Unsupported graphics shader stage: " + stage + ". Supported: vertex, geometry, fragment.");
            }
        }

        if (!vertex || !fragment) {
            throw new IllegalArgumentException("A graphics pipeline requires vertex and fragment stages, we are missing one or both.");
        }
    }

    public long getHandle() { return handle; }

    @Override
    public void dispose() {
        vkDestroyPipeline(device, handle, null);
    }
}