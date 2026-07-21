package net.caffeinemc.mods.sodium.client.util;

import org.joml.Vector4f;

public record FogParameters(
        float red,
        float green,
        float blue,
        float alpha,
        float environmentalStart,
        float environmentalEnd,
        float renderStart,
        float renderEnd,
        float cullDistance) {
    public static final FogParameters NONE = new FogParameters(
            Float.MAX_VALUE,
            Float.MAX_VALUE,
            Float.MAX_VALUE,
            Float.MAX_VALUE,
            Float.MAX_VALUE,
            -3.4028235E38F,
            Float.MAX_VALUE,
            -3.4028235E38F);

    public FogParameters(
            float red,
            float green,
            float blue,
            float alpha,
            float environmentalStart,
            float environmentalEnd,
            float renderStart,
            float renderEnd) {
        this(
                red,
                green,
                blue,
                alpha,
                environmentalStart,
                environmentalEnd,
                renderStart,
                renderEnd,
                Float.isNaN(environmentalEnd) ? renderEnd : Math.min(renderEnd, environmentalEnd));
    }

    public FogParameters(Vector4f color, FogParameters old) {
        this(
                color.x,
                color.y,
                color.z,
                color.w,
                old.environmentalStart,
                old.environmentalEnd,
                old.renderStart,
                old.renderEnd);
    }

    public FogParameters(
            float red,
            float green,
            float blue,
            float alpha,
            float environmentalStart,
            float environmentalEnd,
            float renderStart,
            float renderEnd,
            float cullDistance) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
        this.environmentalStart = environmentalStart;
        this.environmentalEnd = environmentalEnd;
        this.renderStart = renderStart;
        this.renderEnd = renderEnd;
        this.cullDistance = cullDistance;
    }

    public float red() {
        return this.red;
    }

    public float green() {
        return this.green;
    }

    public float blue() {
        return this.blue;
    }

    public float alpha() {
        return this.alpha;
    }

    public float environmentalStart() {
        return this.environmentalStart;
    }

    public float environmentalEnd() {
        return this.environmentalEnd;
    }

    public float renderStart() {
        return this.renderStart;
    }

    public float renderEnd() {
        return this.renderEnd;
    }

    public float cullDistance() {
        return this.cullDistance;
    }
}
