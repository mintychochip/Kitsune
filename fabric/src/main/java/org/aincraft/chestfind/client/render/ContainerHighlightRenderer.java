package org.aincraft.chestfind.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders container highlights in the world.
 */
@Environment(EnvType.CLIENT)
public class ContainerHighlightRenderer {
    private static final Map<BlockPos, HighlightEntry> highlights = new ConcurrentHashMap<>();

    private ContainerHighlightRenderer() {
    }

    /**
     * Register the world render callback.
     */
    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ContainerHighlightRenderer::render);
    }

    /**
     * Add a container highlight.
     *
     * @param pos        Block position
     * @param color      Highlight color (RGB)
     * @param durationMs Duration in milliseconds
     */
    public static void addHighlight(BlockPos pos, int color, long durationMs) {
        highlights.put(pos, new HighlightEntry(color, System.currentTimeMillis() + durationMs));
    }

    /**
     * Clear all highlights.
     */
    public static void clearHighlights() {
        highlights.clear();
    }

    private static void render(WorldRenderContext context) {
        if (highlights.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();

        // Remove expired highlights
        Iterator<Map.Entry<BlockPos, HighlightEntry>> iterator = highlights.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, HighlightEntry> entry = iterator.next();
            if (entry.getValue().expireTime < now) {
                iterator.remove();
            }
        }

        if (highlights.isEmpty()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d cameraPos = context.camera().getPos();

        MatrixStack matrices = context.matrixStack();
        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        for (Map.Entry<BlockPos, HighlightEntry> entry : highlights.entrySet()) {
            BlockPos pos = entry.getKey();
            HighlightEntry highlight = entry.getValue();

            // Calculate alpha based on time remaining
            float timeRemaining = (highlight.expireTime - now) / 1000f;
            float alpha = Math.min(1.0f, timeRemaining / 2.0f) * 0.5f;

            renderBlockHighlight(matrices, buffer, tessellator, pos, highlight.color, alpha);
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();

        matrices.pop();
    }

    private static void renderBlockHighlight(MatrixStack matrices, BufferBuilder buffer,
                                             Tessellator tessellator, BlockPos pos,
                                             int color, float alpha) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        int a = (int) (alpha * 255);

        float x = pos.getX();
        float y = pos.getY();
        float z = pos.getZ();

        // Offset slightly to avoid z-fighting
        float offset = 0.002f;
        float minX = x - offset;
        float minY = y - offset;
        float minZ = z - offset;
        float maxX = x + 1 + offset;
        float maxY = y + 1 + offset;
        float maxZ = z + 1 + offset;

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        // Draw box faces
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        // Bottom face
        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, alpha).next();
        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, alpha).next();
        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, alpha).next();
        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, alpha).next();

        // Top face
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, alpha).next();
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, alpha).next();
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, alpha).next();
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, alpha).next();

        // Front face
        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, alpha).next();
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, alpha).next();
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, alpha).next();
        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, alpha).next();

        // Back face
        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, alpha).next();
        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, alpha).next();
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, alpha).next();
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, alpha).next();

        // Left face
        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, alpha).next();
        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, alpha).next();
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, alpha).next();
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, alpha).next();

        // Right face
        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, alpha).next();
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, alpha).next();
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, alpha).next();
        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, alpha).next();

        tessellator.draw();
    }

    private record HighlightEntry(int color, long expireTime) {
    }
}
