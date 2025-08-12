package net.vulkanmod.config.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.vulkanmod.config.option.ListOption;

import java.util.List;

import static net.vulkanmod.config.gui.GuiRenderer.drawCenteredString;

public class ListOptionWidget<T> extends OptionWidget<ListOption<T>> {
    private int currentIndex;

    public ListOptionWidget(int x, int y, int width, int height, ListOption<T> option) {
        super(x, y, width, height, option.getName());
        this.option = option;
        this.currentIndex = option.getValues().indexOf(option.getCurrentValue());
    }

    @Override
    protected void renderControls(double mouseX, double mouseY) {
        List<T> values = option.getValues();

        for (int i = 0; i < values.size(); i++) {
            T value = values.get(i);
            Component valueText = Component.literal(value.toString());
            int textWidth = Minecraft.getInstance().font.width(valueText);
            int textX = controlX + (controlWidth - textWidth) / 2;
            int textY = y + (height - Minecraft.getInstance().font.lineHeight) / 2;

            if (i == currentIndex) {
                drawCenteredString(Minecraft.getInstance().font, valueText, textX, textY, 0xFFFFFF);
            } else {
                drawCenteredString(Minecraft.getInstance().font, valueText, textX, textY, 0xAAAAAA);
            }
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        List<T> values = option.getValues();
        if (!values.isEmpty()) {
            currentIndex = (currentIndex + 1) % values.size();
            option.setValue(values.get(currentIndex));
        }
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double deltaX, double deltaY) {
    }
}
