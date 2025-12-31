package org.aincraft.kitsune.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.aincraft.kitsune.network.ClientNetworkHandler;
import org.lwjgl.glfw.GLFW;

/**
 * Search screen for entering container search queries.
 */
@Environment(EnvType.CLIENT)
public class SearchScreen extends Screen {
    private static final int SEARCH_BOX_WIDTH = 200;
    private static final int SEARCH_BOX_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_HEIGHT = 20;

    private TextFieldWidget searchBox;
    private ButtonWidget searchButton;

    public SearchScreen() {
        super(Text.translatable("gui.kitsune.search.title"));
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Search box
        searchBox = new TextFieldWidget(
                this.textRenderer,
                centerX - SEARCH_BOX_WIDTH / 2,
                centerY - 20,
                SEARCH_BOX_WIDTH,
                SEARCH_BOX_HEIGHT,
                Text.translatable("gui.kitsune.search.placeholder")
        );
        searchBox.setMaxLength(256);
        searchBox.setFocused(true);
        this.addDrawableChild(searchBox);

        // Search button
        searchButton = ButtonWidget.builder(
                        Text.translatable("gui.kitsune.search.button"),
                        button -> performSearch())
                .dimensions(centerX - BUTTON_WIDTH / 2, centerY + 10, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        this.addDrawableChild(searchButton);

        // Set initial focus
        this.setInitialFocus(searchBox);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        // Draw title
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                this.title,
                this.width / 2,
                this.height / 2 - 50,
                0xFFFFFF
        );

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            performSearch();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void performSearch() {
        String query = searchBox.getText().trim();
        if (query.isEmpty()) {
            return;
        }

        // Send search request to server
        ClientNetworkHandler.sendSearchRequest(query, 10);

        // Close this screen (results screen will open when results arrive)
        this.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
