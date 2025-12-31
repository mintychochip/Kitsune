package org.aincraft.chestfind.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.aincraft.chestfind.model.SearchResult;
import org.aincraft.chestfind.network.SearchResultHandler;
import org.aincraft.chestfind.platform.FabricLocationFactory;

import java.util.List;

/**
 * Screen that displays search results.
 */
@Environment(EnvType.CLIENT)
public class SearchResultsScreen extends Screen {
    private static final int RESULT_HEIGHT = 30;
    private static final int PADDING = 10;

    private final List<SearchResult> results;
    private int scrollOffset = 0;

    public SearchResultsScreen(List<SearchResult> results) {
        super(Text.translatable("gui.chestfind.results.title"));
        this.results = results;
    }

    @Override
    protected void init() {
        super.init();

        // Close button
        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("X").formatted(Formatting.RED),
                        button -> this.close())
                .dimensions(this.width - 25, 5, 20, 20)
                .build());

        // New search button
        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("New Search"),
                        button -> {
                            SearchResultHandler.clearResults();
                            MinecraftClient.getInstance().setScreen(new SearchScreen());
                        })
                .dimensions(this.width / 2 - 50, this.height - 30, 100, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        // Title
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                this.title,
                this.width / 2,
                PADDING,
                0xFFFFFF
        );

        if (results.isEmpty()) {
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.translatable("gui.chestfind.results.empty"),
                    this.width / 2,
                    this.height / 2,
                    0xAAAAAA
            );
        } else {
            // Render results
            int y = 30;
            MinecraftClient client = MinecraftClient.getInstance();

            for (int i = scrollOffset; i < results.size() && y < this.height - 40; i++) {
                SearchResult result = results.get(i);
                renderResult(context, result, PADDING, y, this.width - PADDING * 2, mouseX, mouseY);
                y += RESULT_HEIGHT + 5;
            }

            // Result count
            context.drawTextWithShadow(
                    this.textRenderer,
                    Text.literal(String.format("Showing %d results", results.size())).formatted(Formatting.GRAY),
                    PADDING,
                    this.height - 25,
                    0xAAAAAA
            );
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderResult(DrawContext context, SearchResult result, int x, int y, int width, int mouseX, int mouseY) {
        // Background
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + RESULT_HEIGHT;
        int bgColor = hovered ? 0x40FFFFFF : 0x20FFFFFF;
        context.fill(x, y, x + width, y + RESULT_HEIGHT, bgColor);

        // Coordinates
        String coords = String.format("[%d, %d, %d]",
                result.location().blockX(),
                result.location().blockY(),
                result.location().blockZ());
        context.drawTextWithShadow(this.textRenderer, Text.literal(coords).formatted(Formatting.YELLOW),
                x + 5, y + 5, 0xFFFF00);

        // Distance
        if (client != null && client.player != null) {
            double distance = FabricLocationFactory.distance(
                    FabricLocationFactory.toLocationData(
                            client.player.getWorld().getRegistryKey().getValue().toString(),
                            client.player.getBlockX(),
                            client.player.getBlockY(),
                            client.player.getBlockZ()),
                    result.location()
            );
            if (distance >= 0) {
                String distStr = String.format("%.0f blocks", distance);
                int distWidth = this.textRenderer.getWidth(distStr);
                context.drawTextWithShadow(this.textRenderer,
                        Text.literal(distStr).formatted(Formatting.GRAY),
                        x + width - distWidth - 5, y + 5, 0xAAAAAA);
            }
        }

        // Score
        String scoreStr = String.format("%.0f%%", result.score() * 100);
        context.drawTextWithShadow(this.textRenderer,
                Text.literal(scoreStr).formatted(Formatting.GREEN),
                x + 150, y + 5, 0x00FF00);

        // Preview
        String preview = result.preview();
        if (preview.length() > 60) {
            preview = preview.substring(0, 57) + "...";
        }
        context.drawTextWithShadow(this.textRenderer,
                Text.literal(preview).formatted(Formatting.GRAY),
                x + 5, y + 17, 0xAAAAAA);
    }

    private static org.aincraft.chestfind.api.LocationData toLocationData(String worldName, int x, int y, int z) {
        return org.aincraft.chestfind.api.LocationData.of(worldName, x, y, z);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int maxScroll = Math.max(0, results.size() - (this.height - 80) / (RESULT_HEIGHT + 5));
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) amount));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Check if clicking on a result
            int y = 30;
            for (int i = scrollOffset; i < results.size() && y < this.height - 40; i++) {
                if (mouseY >= y && mouseY < y + RESULT_HEIGHT && mouseX >= PADDING && mouseX < this.width - PADDING) {
                    // Clicked on result - close screen and let player navigate
                    this.close();
                    return true;
                }
                y += RESULT_HEIGHT + 5;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
