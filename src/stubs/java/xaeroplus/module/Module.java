package xaeroplus.module;

public abstract class Module {
    private boolean enabled = false;

    protected void onEnable() {}

    protected void onDisable() {}

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        if (enabled) {
            enable();
        } else {
            disable();
        }
    }

    public void enable() {
        if (this.isEnabled()) return;
        this.enabled = true;

        try {
            onEnable();
        } catch (final Exception e) {

        }
    }

    public void disable() {
        if (!this.isEnabled()) return;
        this.enabled = false;

        try {
            onDisable();
        } catch (Exception e) {

        }
    }

    public void toggle() {
        if (isEnabled()) {
            disable();
        } else {
            enable();
        }
    }
}
