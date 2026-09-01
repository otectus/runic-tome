package com.otectus.runictome.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.util.Mth;

/**
 * A {@link Button} that draws from the tome's own sprite sheet instead of vanilla's
 * {@code widgets.png}.
 *
 * <p>Vanilla's button is a grey stone slab, which is the single most jarring thing that can sit on
 * a leather panel, so leaving the two bottom buttons stock would undo most of the point of a
 * textured GUI. Only {@code renderWidget} changes; extending {@code Button} rather than
 * {@code AbstractWidget} keeps {@code onPress}, the click sound, keyboard activation, focus
 * handling, {@code alpha}, {@code setTooltip} and narration exactly as vanilla has them.
 *
 * <p>Construct through the normal builder with Forge's {@code build(Function)} overload —
 * {@code Button.builder(...).bounds(...).tooltip(...).build(TomeButton::new)} — so every existing
 * call site keeps its tooltip, which {@code Button(Builder)} applies for us.
 *
 * <p>{@code updateWidgetNarration} is deliberately <em>not</em> overridden: {@code Button} already
 * narrates the message, the "Press to activate" usage hint and any attached tooltip, and overriding
 * it would lose those. The pause button's state is carried by its own message text, so there is
 * nothing extra to announce.
 */
public class TomeButton extends Button {

    public TomeButton(Button.Builder builder) {
        super(builder);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int u = !this.active
                ? TomeSprites.BTN_DISABLED_U
                : (isHoveredOrFocused() ? TomeSprites.BTN_HOVER_U : TomeSprites.BTN_NORMAL_U);
        // isHoveredOrFocused() covers the keyboard focus ring too, exactly as vanilla's own
        // getTextureY() does, so focused buttons need no extra decoration.
        graphics.setColor(1.0F, 1.0F, 1.0F, this.alpha);
        TomeSprites.button(graphics, getX(), getY(), getWidth(), getHeight(), u);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        // getFGColor() rather than a hardcoded white, so setFGColor still works on these buttons.
        this.renderString(graphics, Minecraft.getInstance().font,
                getFGColor() | Mth.ceil(this.alpha * 255.0F) << 24);
    }
}
