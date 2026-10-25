package com.restonic4.bloom.backends.vulkan.rendering;

import com.restonic4.bloom.backends.vulkan.buffer.VulkanBuffer;
import com.restonic4.bloom.backends.vulkan.command.VulkanCommandPool;
import com.restonic4.bloom.backends.vulkan.command.VulkanInstanceGroup;
import com.restonic4.bloom.backends.vulkan.command.VulkanMeshBuffers;
import com.restonic4.bloom.backends.vulkan.command.VulkanMeshDraw;
import com.restonic4.bloom.backends.vulkan.device.VulkanLogicalDevice;
import com.restonic4.bloom.backends.vulkan.material.VulkanMaterialLibrary;
import com.restonic4.bloom.backends.vulkan.memory.VulkanDeferredDisposer;
import com.restonic4.bloom.backends.vulkan.memory.VulkanMemoryUsage;
import com.restonic4.bloom.backends.vulkan.sync.VulkanFrameSync;
import com.restonic4.bloom.core.Disposable;
import com.restonic4.bloom.core.math.FrameProfiler;
import com.restonic4.bloom.core.scene.DrawBatch;
import com.restonic4.bloom.core.scene.MeshData;
import com.restonic4.bloom.core.scene.MeshInstance;
import com.restonic4.bloom.core.scene.Scene;
import com.restonic4.bloom.core.scene.StaticChunk;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.VK10.*;

// GPU side of a core Scene: mesh buffers (uploaded the first time a mesh is seen), the dynamic instances' matrices
// (one host-writable buffer per frame slot, rewritten every frame) and each static chunk's matrices (one device-local
// buffer per chunk, uploaded once and again only when that chunk changes).
public final class VulkanSceneResources implements Disposable {
    private static final int MATRIX_BYTES = 16 * Float.BYTES;
    private static final int MIN_CAPACITY = 256;

    private record GpuChunk(long version, VulkanBuffer buffer, VulkanInstanceGroup group) {}

    private final VulkanLogicalDevice logicalDevice;
    private final VulkanCommandPool commandPool;
    private final VulkanMaterialLibrary materials;
    private final FrameProfiler profiler;

    private final Map<MeshData, VulkanMeshBuffers> meshes = new HashMap<>();
    private final VulkanBuffer[] instanceBuffers;
    private final VulkanDeferredDisposer retired = new VulkanDeferredDisposer();

    // CPU copy of the matrices being uploaded, reused. Not a MemoryStack: that is 64 KB, about 1000 matrices
    private ByteBuffer matrices = memAlloc(MIN_CAPACITY * MATRIX_BYTES);

    private List<VulkanMeshDraw> dynamicDraws = List.of();
    private long dynamicDrawsVersion = -1;

    private final Map<StaticChunk, GpuChunk> staticChunks = new HashMap<>();
    private final List<VulkanInstanceGroup> staticGroups = new ArrayList<>();
    private long syncedStaticVersion = -1;

    private final List<VulkanInstanceGroup> groups = new ArrayList<>();

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
     * Brings the GPU data up to date and returns what to draw: the dynamic group, then one group per static chunk.
     * Call it after waiting for the frame slot: that is what makes it safe to overwrite, or replace, the slot's buffer.
     * The returned list is reused by the next call.
     */
    public List<VulkanInstanceGroup> prepare(Scene scene, int frameIndex, VulkanFrameSync frameSync) {
        retired.collect(frameSync.getCompletedValue());

        syncStatic(scene, frameSync.getLastSubmittedValue());
        prepareDynamic(scene, frameIndex);

        groups.clear();
        if (!dynamicDraws.isEmpty()) groups.add(new VulkanInstanceGroup(instanceBuffers[frameIndex], dynamicDraws));
        groups.addAll(staticGroups);

        return groups;
    }

    private void prepareDynamic(Scene scene, int frameIndex) {
        if (scene.version() != dynamicDrawsVersion) {
            dynamicDraws = buildDraws(scene.dynamicBatches());
            dynamicDrawsVersion = scene.version();
        }

        int count = scene.dynamicInstanceCount();
        ensureInstanceCapacity(frameIndex, count);

        if (count > 0) {
            long fillStart = profiler.start();
            fillMatrices(scene.dynamicBatches(), count);
            profiler.stop("fill", fillStart);

            long writeStart = profiler.start();
            instanceBuffers[frameIndex].write(matrices);
            profiler.stop("write", writeStart);
        }
    }

    // Chunks that changed or are new get a fresh buffer; the old one may still be read by frames in flight, so it is retired, not freed
    private void syncStatic(Scene scene, long lastSubmittedValue) {
        if (scene.staticVersion() == syncedStaticVersion) return;

        long start = profiler.start();

        Set<StaticChunk> inScene = new HashSet<>(scene.staticChunks());
        staticChunks.entrySet().removeIf(entry -> {
            if (inScene.contains(entry.getKey())) return false;

            retired.retire(entry.getValue().buffer(), lastSubmittedValue);
            return true;
        });

        staticGroups.clear();
        for (StaticChunk chunk : scene.staticChunks()) {
            GpuChunk gpu = staticChunks.get(chunk);

            if (gpu == null || gpu.version() != chunk.version()) {
                if (gpu != null) retired.retire(gpu.buffer(), lastSubmittedValue);

                gpu = uploadChunk(chunk);
                staticChunks.put(chunk, gpu);
            }

            staticGroups.add(gpu.group());
        }

        syncedStaticVersion = scene.staticVersion();
        profiler.stop("static", start);
    }

    private GpuChunk uploadChunk(StaticChunk chunk) {
        fillMatrices(chunk.batches(), chunk.instanceCount());

        VulkanBuffer buffer = VulkanBuffer.createDeviceLocal(logicalDevice, commandPool, matrices, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
        return new GpuChunk(chunk.version(), buffer, new VulkanInstanceGroup(buffer, buildDraws(chunk.batches())));
    }

    // Each batch uses a slice of its group's instance buffer, in the same order fillMatrices writes them
    private List<VulkanMeshDraw> buildDraws(List<DrawBatch> batches) {
        List<VulkanMeshDraw> built = new ArrayList<>(batches.size());

        int firstInstance = 0;
        for (DrawBatch batch : batches) {
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

    private void fillMatrices(List<DrawBatch> batches, int instanceCount) {
        int bytes = instanceCount * MATRIX_BYTES;
        if (matrices.capacity() < bytes) {
            matrices = memRealloc(matrices, Math.max(bytes, matrices.capacity() * 2));
        }

        matrices.clear();

        int offset = 0;
        for (DrawBatch batch : batches) {
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
        for (GpuChunk chunk : staticChunks.values()) chunk.buffer().dispose();
        retired.dispose();

        for (VulkanMeshBuffers buffers : meshes.values()) {
            buffers.indexBuffer().dispose();
            buffers.vertexBuffer().dispose();
        }

        memFree(matrices);
    }
}
