# Vulkan Learning Context (Bloom engine)

> Purpose: summary of a prior chat session, written so Claude Code can read it and continue guiding me with the full picture. Place this file in the project root (or a `docs/` folder) and tell Claude Code to read it first.

---

## 1. Who I am / how to help me

- I am **learning Vulkan** using **Java + LWJGL 3**. I want to use **modern Vulkan (1.3 / 1.4 features)**, not the legacy render pass / framebuffer / fence-heavy style.
- I got a triangle rendering, but **I do not understand most of what I wrote after the shader setup**: swapchain, image views, command buffers, frame sync, synchronization2, semaphores, dynamic rendering.
- I feel lost. **Please explain the *why* before the *how***, in small steps, tied to my actual code.
- Preferred teaching style:
  - Explain concepts using my own classes and variable names.
  - One step at a time; don't dump a whole engine rewrite.
  - Prefer making something visibly change on screen (color, motion) after each step so concepts click.
  - Point out when something I wrote is a stub or a shortcut that will break later.
  - Use modern APIs: dynamic rendering, synchronization2 (`vkCmdPipelineBarrier2`, `vkQueueSubmit2`), timeline semaphores, VMA for memory.

---

## 2. Project structure (as known)

Project name: **Bloom** (engine, package `com.restonic4.bloom`), with a test app **Unlock** (`com.restonic4.unlock`).

Vulkan backend classes (package `com.restonic4.bloom.backends.vulkan.*`), each wrapping one Vulkan object and implementing `Disposable` (with a `dispose()` method), created through static `create(...)` factories:

| Area | Class | Role |
|---|---|---|
| window | `glfw.GlfwWindow` | GLFW window wrapper (`getHandle()`, `shouldClose()`, `setShouldClose()`, `dispose()`) |
| instance | `instance.VulkanInstance` | `VkInstance`, optional validation layers |
| surface | `surface.VulkanSurface` | `VkSurfaceKHR` |
| device | `device.VulkanPhysicalDevice` | picks best GPU (`pickBestDevice`) |
| device | `device.VulkanLogicalDevice` | `VkDevice`, graphics queue + present queue, graphics queue family index |
| swapchain | `swapchain.VulkanSwapChain` | `VkSwapchainKHR`, image handles, format, width/height |
| swapchain | `swapchain.VulkanSwapChainImageViews` | one `VkImageView` per swapchain image |
| shader | `shader.VulkanGlslCompiler`, `VulkanShader`, `VulkanShaderStage` | GLSL to SPIR-V, shader modules, stage + module pairing |
| pipeline | `pipeline.VulkanPipelineLayout`, `VulkanGraphicsPipeline` | pipeline layout and graphics pipeline (created with the swapchain color format for dynamic rendering) |
| command | `command.VulkanCommandPool` | pool with `RESET_COMMAND_BUFFER_BIT`, allocates primary command buffers |
| command | `command.VulkanCommandBufferRecorder` | static `record(...)` that records one frame's commands |
| sync | `sync.VulkanFrameSync` | binary + timeline semaphores |

Core helpers: `core.Disposable`, `core.graphics.ShaderSource`, `core.graphics.ShaderStage`, `core.platform.Version`.

Test driver: `VulkanTest.init()` in `com.restonic4.unlock` builds everything, records command buffers, runs the loop, then tears everything down.

---

## 3. Current state of the code

### Setup order in `VulkanTest.init()`
1. `GlfwWindow(500, 500)`
2. `VulkanInstance.create(...)` (validation enabled via `Configuration.DEBUG.get(true)`)
3. `VulkanSurface`, `VulkanPhysicalDevice.pickBestDevice`, `VulkanLogicalDevice`
4. `VulkanSwapChain` + `VulkanSwapChainImageViews`
5. Compile `triangle.vert` / `triangle.frag` to shader modules (compiler disposed afterward)
6. `VulkanPipelineLayout` (empty so far) + `VulkanGraphicsPipeline`
7. `VulkanCommandPool` + one primary command buffer **per swapchain image**
8. **Record every command buffer once, before the loop**
9. `VulkanFrameSync.create(device, swapchainImageCount)`
10. Main loop: `glfwPollEvents()` then `drawFrame(...)`
11. `vkDeviceWaitIdle`, then dispose in reverse order

### What `VulkanCommandBufferRecorder.record` does
- `vkBeginCommandBuffer`
- Barrier: swapchain image `UNDEFINED -> COLOR_ATTACHMENT_OPTIMAL` (src stage/access NONE, dst `COLOR_ATTACHMENT_OUTPUT` / `COLOR_ATTACHMENT_WRITE`)
- Build `VkRenderingAttachmentInfo` (image view, `CLEAR` load op, `STORE` store op, dark clear color `0.02, 0.02, 0.03, 1.0`)
- `vkCmdBeginRendering` (dynamic rendering, no render pass or framebuffer)
- Bind graphics pipeline, set dynamic viewport and scissor
- `vkCmdDraw(3, 1, 0, 0)`: three **hardcoded vertices in the vertex shader**
- `vkCmdEndRendering`
- Barrier: `COLOR_ATTACHMENT_OPTIMAL -> PRESENT_SRC_KHR`
- `vkEndCommandBuffer`

Note: it uses `VkRenderingInfo.ncolorAttachmentCount(address, 1)` as a workaround for the color attachment count not being set by `pColorAttachments(...)`.

### What `VulkanFrameSync` holds
- `imageAvailableSemaphore`: **one** binary semaphore (signaled by acquire)
- `renderFinishedSemaphores[imageCount]`: one binary semaphore **per swapchain image** (signaled by submit, waited by present). Per-image is the correct pattern.
- `timelineSemaphore`: a single timeline semaphore, plus `lastSubmittedTimelineValue`
- `nextTimelineValue()`, `markSubmitted(value)` (enforces +1 increments), `waitForLastSubmission()` (`vkWaitSemaphores` with infinite timeout)

### What `drawFrame` does
1. `frameSync.waitForLastSubmission()` (CPU waits for the previous frame's GPU work to finish)
2. `vkAcquireNextImageKHR` signals `imageAvailable`, returns `imageIndex`. `OUT_OF_DATE` returns false.
3. `vkQueueSubmit2` with:
   - wait: `imageAvailable` at stage `COLOR_ATTACHMENT_OUTPUT`
   - command buffer: `commandBuffers.get(imageIndex)`
   - signal: `renderFinished[imageIndex]` (binary, value 0) and the timeline semaphore (value = next)
4. `vkQueuePresentKHR` waits on `renderFinished[imageIndex]`
5. If present or acquire returns `OUT_OF_DATE` / `SUBOPTIMAL`, `drawFrame` returns false and **the app just closes** (swapchain recreation not implemented)

### Command pool
`VulkanCommandPool.create` uses `VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT` on the graphics queue family. `allocatePrimaryCommandBuffers(count)` returns `List<VkCommandBuffer>`.

---

## 4. Concepts explained to me (reference)

**CPU and GPU are two independent workers.** The CPU records commands and submits them, then continues without waiting. Vulkan makes me spell out all ordering.

**Swapchain**: a small pool (2-3) of presentable images owned by the window system. `vkAcquireNextImageKHR` returns an *index* of a free image (not necessarily sequential); `vkQueuePresentKHR` gives it back for display.

**VkImage vs VkImageView**: the image is raw memory plus format; the view describes how to interpret it. Barriers take the **image**, attachments take the **view**. `swapChainImages[i]` and `imageViews.get(i)` are the same pixels.

**Image layouts**: how the GPU arranges the image for its current use.
- `UNDEFINED`: contents don't matter (fine because we clear)
- `COLOR_ATTACHMENT_OPTIMAL`: for rendering into
- `PRESENT_SRC_KHR`: for the display engine

**`transitionImage`** is both a layout change and a memory/execution barrier ("later work at stage X waits for earlier work at stage Y"). Called twice per frame (before and after rendering). It uses synchronization2.

**Dynamic rendering** (core 1.3): replaces `VkRenderPass` and `VkFramebuffer`. `vkCmdBeginRendering` with attachment infos, draw, `vkCmdEndRendering`. The pipeline needs `VkPipelineRenderingCreateInfo` with the attachment formats instead of a render pass.

**Command buffers**: recorded lists of GPU commands. Recording executes nothing; `vkQueueSubmit2` does. The command pool is the allocator.

**Synchronization (three problems, three tools)**

| Problem | Tool | In my code |
|---|---|---|
| GPU must not render before the swapchain image is free | binary semaphore | `imageAvailableSemaphore` |
| Present must not show the image before rendering is done | binary semaphore | `renderFinishedSemaphores[i]` |
| CPU must not reuse resources the GPU still uses | timeline semaphore | `timelineSemaphore` + `waitForLastSubmission()` |

- Binary semaphore: signaled/unsignaled flag; a wait consumes the signal. `value` fields must be 0 for binary semaphores.
- Timeline semaphore: monotonically increasing counter; CPU or GPU can wait for "value >= N". Replaces fences.
- The submit waits on `imageAvailable` only at the `COLOR_ATTACHMENT_OUTPUT` stage so earlier pipeline stages can overlap.

---

## 5. Known problems / limitations in the current code

1. **Command buffers are recorded once, before the loop.** Only works for a static scene. Needs re-recording every frame.
2. **No frames in flight.** The CPU waits for the GPU to finish the previous frame every frame, so there is no CPU/GPU overlap. Standard fix: 2 frames in flight, with per-frame command buffers (and per-frame command pools or resettable buffers) and per-frame `imageAvailable` semaphores. The CPU waits only on the timeline value of frame `N - framesInFlight`.
3. **Swapchain recreation is a stub.** On resize, minimize, or `OUT_OF_DATE`/`SUBOPTIMAL`, the app stops. Needs: wait idle (or retire old swapchain), recreate swapchain + image views (and later depth image), handle 0x0 framebuffer (minimized) by waiting for events.
4. `SUBOPTIMAL` is currently treated as fatal in `drawFrame`; it should usually just trigger recreation after presenting.
5. Pipeline layout is empty: no push constants or descriptors yet.
6. Vertex data is hardcoded in the shader: no vertex buffers yet.
7. Resource teardown in `init()` is manual; fine for now, but a cleanup/ownership structure will help as more resources appear.

---

## 6. Roadmap (agreed order)

1. **Frames in flight + per-frame command recording** (restructure `drawFrame` / `VulkanFrameSync`; mostly mechanical).
   - First visible goal: make the triangle change color or rotate each frame using a **push constant** (proves per-frame recording works).
2. **Vertex buffers** replacing hardcoded vertices. Introduces `VkBuffer` and memory allocation. Use **VMA** (`org.lwjgl.util.vma`) rather than raw `vkAllocateMemory`.
3. **Staging buffers**: upload CPU-visible to device-local memory.
4. **Push constants**, then **uniform/storage buffers + descriptors**; pass a transform matrix.
5. **Index buffers**, then **textures** (images, samplers, descriptor sets).
6. **Depth buffer** (extra attachment in `VkRenderingInfo`; easy with dynamic rendering).
7. **Swapchain recreation + resize handling.**

Modern features to learn once buffers exist: **buffer device address** (raw GPU pointers in shaders), **descriptor indexing / bindless**, **push descriptors** (core in 1.4).

---

## 7. Recommended resources

- Official **Khronos Vulkan Tutorial** (docs.vulkan.org, "Tutorial"): rewritten for dynamic rendering, timeline semaphores, synchronization2.
- **vkguide.dev**: built around 1.3 features, explains the "why" well. It is C++, but calls map 1:1 to LWJGL.

---

## 8. Notes for Claude Code when helping me

- Read the actual classes in `com.restonic4.bloom.backends.vulkan.*` before suggesting changes; follow the existing style (`create(...)` factories, `Disposable`, `MemoryStack` try-with-resources, `sType$Default()`).
- Target Vulkan 1.3 core features at minimum (dynamic rendering, synchronization2, timeline semaphores); 1.4 where useful. Verify the logical device enables these features/extensions if something fails.
- Keep validation layers on while developing and help me interpret validation messages.
- When adding a feature, explain which of these it touches: lifetime (who creates/destroys), synchronization (what must finish before what), and layout/state transitions.
- Suggest the *smallest* next step that produces a visible result.
- The next concrete task I would like help with: **implement frames in flight and per-frame command buffer recording in `VulkanTest.drawFrame` / `VulkanFrameSync`**, then add a push-constant color or rotation.
