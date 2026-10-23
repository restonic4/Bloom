package com.restonic4.bloom.backends.vulkan.rendering;

import com.restonic4.bloom.backends.vulkan.buffer.VulkanBuffer;
import com.restonic4.bloom.backends.vulkan.command.VulkanCommandPool;
import com.restonic4.bloom.backends.vulkan.command.VulkanMeshBuffers;
import com.restonic4.bloom.backends.vulkan.command.VulkanMeshDraw;
import com.restonic4.bloom.backends.vulkan.device.VulkanLogicalDevice;
import com.restonic4.bloom.backends.vulkan.material.VulkanMaterialLibrary;
import com.restonic4.bloom.backends.vulkan.memory.VulkanMemoryUsage;
import com.restonic4.bloom.core.Disposable;
import com.restonic4.bloom.core.math.FrameProfiler;
import com.restonic4.bloom.core.scene.DrawBatch;
import com.restonic4.bloom.core.scene.MeshData;
import com.restonic4.bloom.core.scene.MeshInstance;
import com.restonic4.bloom.core.scene.Scene;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.VK10.*;

// GPU side of a core Scene: mesh buffers (uploaded the first time a mesh is seen), the draw list (rebuilt when the
// scene's version changes) and one instance buffer per frame slot holding every model matrix, batch after batch.
public final class VulkanSceneResources implements Disposable {
    private static final int MATRIX_BYTES = 16 * Float.BYTES;
    private static final int MIN_CAPACITY = 256;

    private final VulkanLogicalDevice logicalDevice;
    private final VulkanCommandPool commandPool;
    private final VulkanMaterialLibrary materials;
    private final FrameProfiler profiler;

    private final Map<MeshData, VulkanMeshBuffers> meshes = new HashMap<>();
    private final VulkanBuffer[] instanceBuffers;

    // CPU copy of the matrices, reused every frame. Not a MemoryStack: that is 64 KB, about 1000 matrices
    private ByteBuffer matrices = memAlloc(MIN_CAPACITY * MATRIX_BYTES);

    private List<VulkanMeshDraw> draws = List.of();
    private long drawsVersion = -1;

    public VulkanSceneResources(VulkanLogicalDevice logicalDevice, VulkanCommandPool commandPool, VulkanMaterialLibrary materials, FrameProfiler profiler, int framesInFlight) {
        this.logicalDevice = logicalDevice;
        this.profiler = profiler;
        this.commandPool = commandPool;
        this.materials = materials;

        instanceBuffers = new VulkanBuffer[framesInFlight];
        for (int i = 0; i < framesInFlight; i++) {
            instanceBuffers[i] = createInstanceBuffer(MIN_CAPACITY);
        }
    }

    /**
     * Brings the frame slot's instance buffer up to date and returns what to draw.
     * Call it after waiting for the slot: that is what makes it safe to overwrite, or replace, the slot's buffer.
     */
    public List<VulkanMeshDraw> prepare(Scene scene, int frameIndex) {
        if (scene.version() != drawsVersion) {
            draws = buildDraws(scene);
            drawsVersion = scene.version();
        }

        int count = scene.instanceCount();
        ensureInstanceCapacity(frameIndex, count);

        if (count > 0) {
            long fillStart = profiler.start();
            fillMatrices(scene);
            profiler.stop("fill", fillStart);

            long writeStart = profiler.start();
            instanceBuffers[frameIndex].write(matrices);
            profiler.stop("write", writeStart);
        }

        return draws;
    }

    public VulkanBuffer getInstanceBuffer(int frameIndex) { return instanceBuffers[frameIndex]; }

    // Each batch uses a slice of the instance buffer, in the same order fillMatrices writes them
    private List<VulkanMeshDraw> buildDraws(Scene scene) {
        List<VulkanMeshDraw> built = new ArrayList<>(scene.drawBatches().size());

        int firstInstance = 0;
        for (DrawBatch batch : scene.drawBatches()) {
            VulkanMeshBuffers buffers = meshes.computeIfAbsent(batch.mesh(), this::uploadMesh);
            int batchSize = batch.instances().size();

            built.add(new VulkanMeshDraw(buffers.vertexBuffer(), buffers.indexBuffer(), buffers.indexCount(), firstInstance, batchSize, materials.setFor(batch.material())));
            firstInstance += batchSize;
        }

        return built;
    }

    // Safe to replace: only frames of this slot use this buffer, and the caller waited for the last of them
    private void ensureInstanceCapacity(int frameIndex, int count) {
        long capacity = instanceBuffers[frameIndex].getSize() / MATRIX_BYTES;
        if (count <= capacity) return;

        instanceBuffers[frameIndex].dispose();
        instanceBuffers[frameIndex] = createInstanceBuffer(Math.max(count, capacity * 2));
    }

    private void fillMatrices(Scene scene) {
        int bytes = scene.instanceCount() * MATRIX_BYTES;
        if (matrices.capacity() < bytes) {
            matrices = memRealloc(matrices, Math.max(bytes, matrices.capacity() * 2));
        }

        matrices.clear();

        int offset = 0;
        for (DrawBatch batch : scene.drawBatches()) {
            for (MeshInstance instance : batch.instances()) {
                instance.getTransform().getModelMatrix().get(offset, matrices);
                offset += MATRIX_BYTES;
            }
        }

        matrices.limit(bytes);
    }

    private VulkanBuffer createInstanceBuffer(long capacity) {
        return VulkanBuffer.create(logicalDevice, capacity * MATRIX_BYTES, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, VulkanMemoryUsage.HOST_WRITABLE);
    }

    // Mesh buffers are device-local, filled through staging
    private VulkanMeshBuffers uploadMesh(MeshData mesh) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            float[] vertices = mesh.interleavedVertices();
            ByteBuffer vertexData = stack.malloc(vertices.length * Float.BYTES);
            vertexData.asFloatBuffer().put(vertices);
            VulkanBuffer vertexBuffer = VulkanBuffer.createDeviceLocal(logicalDevice, commandPool, vertexData, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);

            ByteBuffer indexData = stack.malloc(mesh.indices().length * Integer.BYTES);
            indexData.asIntBuffer().put(mesh.indices());
            VulkanBuffer indexBuffer = VulkanBuffer.createDeviceLocal(logicalDevice, commandPool, indexData, VK_BUFFER_USAGE_INDEX_BUFFER_BIT);

            return new VulkanMeshBuffers(vertexBuffer, indexBuffer, mesh.indexCount());
        }
    }

    @Override
    public void dispose() {
        for (VulkanBuffer buffer : instanceBuffers) buffer.dispose();

        for (VulkanMeshBuffers buffers : meshes.values()) {
            buffers.indexBuffer().dispose();
            buffers.vertexBuffer().dispose();
        }

        memFree(matrices);
    }
}
