package net.vulkanmod.config.option;

import net.minecraft.network.chat.Component;
import net.vulkanmod.config.gui.widget.ListOptionWidget;
import net.vulkanmod.config.gui.widget.OptionWidget;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ListOption<T> extends Option<T> {
    private final Component name;
    private final Supplier<List<T>> valuesSupplier;
    private final Consumer<T> valueConsumer;
    private final Supplier<T> currentValueSupplier;

    public ListOption(Component name, Supplier<List<T>> valuesSupplier, Consumer<T> valueConsumer, Supplier<T> currentValueSupplier) {
        super(name, valueConsumer, currentValueSupplier);
        this.name = name;
        this.valuesSupplier = valuesSupplier;
        this.valueConsumer = valueConsumer;
        this.currentValueSupplier = currentValueSupplier;
    }

    @Override
    public OptionWidget<?> createOptionWidget(int x, int y, int width, int height) {
        return new ListOptionWidget<>(x, y, width, height, this);

    }

    public Component getName() {
        return name;
    }

    public List<T> getValues() {
        return valuesSupplier.get();
    }

    public void setValue(T value) {
        valueConsumer.accept(value);
    }

    public T getCurrentValue() {
        return currentValueSupplier.get();
    }
}