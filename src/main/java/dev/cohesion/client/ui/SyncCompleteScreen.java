package dev.cohesion.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class SyncCompleteScreen extends Screen {

    public SyncCompleteScreen() {
        super(Component.literal("Mods Synced!"));
    }

    @Override
    protected void init() {
        super.init();

        int buttonWidth = 200;
        int buttonHeight = 20;
        int centerX = this.width / 2;

        addRenderableWidget(Button.builder(
                Component.literal("Close Game"),
                button -> Minecraft.getInstance().stop()
        ).bounds(centerX - buttonWidth / 2, this.height / 2 + 30, buttonWidth, buttonHeight).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);

        graphics.drawCenteredString(this.font, this.title,
                this.width / 2, this.height / 2 - 30, 0xFF55FF55);

        graphics.drawCenteredString(this.font,
                "Mods have been downloaded and installed.",
                this.width / 2, this.height / 2 - 10, 0xFFFFFFFF);

        graphics.drawCenteredString(this.font,
                "Please restart your game to apply changes.",
                this.width / 2, this.height / 2 + 5, 0xFFAAAAAA);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
