package me.matl114.hacks.modules.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import me.matl114.api.Displayable;
import me.matl114.events.Event;
import me.matl114.hacks.api.ModuleEntry;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.HackModules;
import me.matl114.managers.Configs;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.text.Text;

public class ModuleListHud extends IRender2DColoredModule {
    public final ModulePath hudRoot = makePath(Configs.RENDER_CONFIG, "in-game-hud");
    public final ModulePath hud = hudRoot.add("module-list-hud");

    public ModuleListHud() {
        super("ModuleList");
    }

    @Override
    protected ModulePath createRoot() {
        return makePath(Configs.RENDER_CONFIG, "in-game-hud").add("module-list-hud");
    }

    List<HudModuleEntry> moduleEntries = null;
    List<HudModuleEntry> moduleListRender = null;

    public void initializeModuleEntryList() {
        moduleEntries = new ArrayList<>();
        for (var re : HackModules.getModuleGroups()) {
            for (var module : re.registered) {
                module.getModuleEntries().map(HudModuleEntry::new).forEach(moduleEntries::add);
            }
        }
        for (var module : moduleEntries) {
            if (!ChatUtils.hasTranslation(module.moduleEntry.getTranslationKey())) {
                Debug.info("Missing translation key for", module.moduleEntry.getTranslationKey());
            }
        }
        sortModuleEntries();
    }

    private void sortModuleEntries() {
        moduleEntries.sort(Comparator.comparingDouble(
                s -> -mc.textRenderer.getTextHandler().getWidth(s.getDisplay())));
    }

    public void onUpdate(Event<Void> event) {

        if (!checkNull() && enable.get()) {
            if (moduleEntries == null) {
                initializeModuleEntryList();
                moduleListRender = moduleEntries.stream()
                        .filter(HudModuleEntry::shouldRender)
                        .toList();
            }
            boolean val = false;
            for (var re : moduleEntries) {
                if (re.tickUpdate()) {
                    val = true;
                }
            }
            if (val) {
                sortModuleEntries();
                moduleListRender = moduleEntries.stream()
                        .filter(HudModuleEntry::shouldRender)
                        .toList();
            }
        } else {
            moduleEntries = null;
            moduleListRender = null;
        }
    }

    @Override
    public void render2D(VDrawContext vdraw, float partialTicks) {
        handleModuleList(vdraw);
    }

    public static class HudModuleEntry implements Displayable {
        ModuleEntry moduleEntry;
        boolean lastState;
        double switchCountDown;

        public HudModuleEntry(ModuleEntry moduleEntry) {
            this.moduleEntry = moduleEntry;
            this.lastState = moduleEntry.getActiveState();
            this.switchCountDown = -1;
            this.lastDisplay = moduleEntry.getDisplay();
        }

        Text lastDisplay;
        Text lastMeta;

        public boolean tickUpdate() {
            boolean update = false;
            if (lastState != moduleEntry.getActiveState()) {
                lastState = moduleEntry.getActiveState();
                switchCountDown = HEIGHT + 1.0D;
                update = true;
            }
            if (switchCountDown >= 0.0D) {
                switchCountDown -= 1.5D;
            }

            if (!Objects.equals(lastMeta, moduleEntry.getMetaData())) {
                lastMeta = moduleEntry.getMetaData();
                lastDisplay =
                        ((lastMeta != null && mc.textRenderer.getTextHandler().getWidth(lastMeta) > 0.0F)
                                ? (moduleEntry
                                        .getDisplay()
                                        .append(Text.literal("["))
                                        .append(lastMeta)
                                        .append(Text.literal("]")))
                                : moduleEntry.getDisplay());
                update = true;
            }
            return update;
        }

        public double getAnimationHeight() {
            return switchCountDown < 0.0D
                    ? switchCountDown
                    : (lastState ? (HEIGHT - switchCountDown) : switchCountDown);
        }

        public boolean shouldRender() {
            return lastState || switchCountDown >= 0.0D;
        }

        @Override
        public Text getDisplay() {
            return lastDisplay;
        }
    }

    public void handleModuleList(VDrawContext vdraw) {
        if (moduleListRender != null) {
            int cnt = 0;
            int size = moduleListRender.size();
            for (int i = 0; i < size; ++i) {
                var text = moduleListRender.get(i);
                if (cnt >= 20) {
                    drawText(vdraw, "...%d more".formatted(moduleListRender.size() - cnt));
                    break;
                }
                double height = text.getAnimationHeight();
                if (height < 0 || i == size - 1) {
                    if (text.lastState) {
                        Text display = text.getDisplay();
                        drawText(vdraw, display);
                        cnt += 1;
                    }
                } else {
                    vdraw.getMatrices().translate(0.0F, (float) height);
                    cnt += 1;
                }
            }
        }
    }
}
