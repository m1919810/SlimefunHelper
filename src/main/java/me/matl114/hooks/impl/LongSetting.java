package me.matl114.hooks.impl;

import java.util.function.Consumer;
import meteordevelopment.meteorclient.settings.IVisible;
import meteordevelopment.meteorclient.settings.Setting;
import net.minecraft.nbt.NbtCompound;

public class LongSetting extends Setting<Long> {
    public final long min, max;
    public final long sliderMin, sliderMax;
    public final boolean noSlider;

    private LongSetting(
            String name,
            String description,
            long defaultValue,
            Consumer<Long> onChanged,
            Consumer<Setting<Long>> onModuleActivated,
            IVisible visible,
            long min,
            long max,
            long sliderMin,
            long sliderMax,
            boolean noSlider) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);

        this.min = min;
        this.max = max;
        this.sliderMin = sliderMin;
        this.sliderMax = sliderMax;
        this.noSlider = noSlider;
    }

    @Override
    protected Long parseImpl(String str) {
        try {
            return Long.parseLong(str.trim());
        } catch (NumberFormatException aaa) {
            return null;
        }
    }

    @Override
    protected boolean isValueValid(Long value) {
        return value >= min && value <= max;
    }

    @Override
    public NbtCompound save(NbtCompound tag) {
        tag.putLong("value", get());

        return tag;
    }

    @Override
    public Long load(NbtCompound tag) {
        set(tag.getLong("value", 0L));

        return get();
    }

    public static class Builder extends Setting.SettingBuilder<LongSetting.Builder, Long, LongSetting> {
        private long min = Long.MIN_VALUE, max = Long.MAX_VALUE;
        private long sliderMin = 0, sliderMax = 10;
        private boolean noSlider = false;

        public Builder() {
            super(0L);
        }

        public LongSetting.Builder min(long min) {
            this.min = min;
            return this;
        }

        public LongSetting.Builder max(long max) {
            this.max = max;
            return this;
        }

        public LongSetting.Builder range(long min, long max) {
            this.min = Math.min(min, max);
            this.max = Math.max(min, max);
            return this;
        }

        public LongSetting.Builder sliderMin(long min) {
            this.sliderMin = min;
            return this;
        }

        public LongSetting.Builder sliderMax(long max) {
            this.sliderMax = max;
            return this;
        }

        public LongSetting.Builder sliderRange(long min, long max) {
            this.sliderMin = min;
            this.sliderMax = max;
            return this;
        }

        public LongSetting.Builder noSlider() {
            noSlider = true;
            return this;
        }

        @Override
        public LongSetting build() {
            return new LongSetting(
                    name,
                    description,
                    defaultValue,
                    onChanged,
                    onModuleActivated,
                    visible,
                    min,
                    max,
                    Math.max(sliderMin, min),
                    Math.min(sliderMax, max),
                    noSlider);
        }
    }
}
