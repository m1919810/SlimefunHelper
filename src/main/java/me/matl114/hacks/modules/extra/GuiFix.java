package me.matl114.hacks.modules.extra;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.Debug;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

public class GuiFix extends BaseModule {
    public final ModulePath guiFix = makePath(Configs.TEST_CONFIG, "other.gui-fix");

    public GuiFix() {}

    public final FlagRef noTerrain =
            flagBuilder(guiFix.add("disable-terrain-load-screen")).build();

    public final FlagRef optimizeGameMenu = builder(guiFix.add("optimize-game-menu"), FlagRef.TYPE)
            .defaultValue(true)
            .build();

    public final FlagRef optimizeServerScreen = builder(guiFix.add("optimize-server-screen"), FlagRef.TYPE)
            .defaultValue(true)
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreSetScreen().getChannel(LevelLoadingScreen.class), this::onTerrainScreenLoad);
        registerListener(
                Listener.getPostInitializeScreen().getChannel(GameMenuScreen.class),
                this::onGameMenuScreenRelocateWurstButton,
                Integer.MAX_VALUE - 1);
        registerListener(
                Listener.getPostInitializeScreen().getChannel(MultiplayerScreen.class),
                this::onServerListMenuRelocateButtons,
                Integer.MAX_VALUE - 1);
    }

    public void onTerrainScreenLoad(Event<Screen> event) {
        if (noTerrain.get()) {
            event.context(null);
            // event.cancel();
        }
    }

    private static final Set<Text> VANILLA_BUTTON_TEXT;

    static {
        Set<Text> texts = new LinkedHashSet<>();
        Field[] fields = GameMenuScreen.class.getDeclaredFields();
        for (var re : fields) {
            try {
                if (Modifier.isStatic(re.getModifiers()) && Text.class.isAssignableFrom(re.getType())) {
                    re.setAccessible(true);
                    Text text = (Text) re.get(null);
                    if (text instanceof MutableText text0
                            && text0.getContent() instanceof TranslatableTextContent translate) {
                        texts.add(text);
                    }
                }
            } catch (Throwable e) {
            }
        }

        VANILLA_BUTTON_TEXT = texts;
    }

    public void onGameMenuScreenRelocateWurstButton(Event<GameMenuScreen> screenEvent) {
        if (optimizeGameMenu.get()) {
            var screen = screenEvent.context;
            List<? extends Element> elements = screen.children();
            int extraButtons = 0;
            int lastLineY = 0;
            List<ButtonWidget> extraElements = new ArrayList<>();
            for (var el : elements) {
                if (el instanceof ButtonWidget button) {
                    Text text = button.getMessage();
                    if (VANILLA_BUTTON_TEXT.contains(text)) {
                        if (!button.visible) {
                            button.visible = true;
                        }
                        lastLineY = Math.max(lastLineY, button.getY());
                    } else {
                        extraElements.add(button);
                    }
                }
            }
            if (lastLineY > 0 && !extraElements.isEmpty()) {
                for (var entry : extraElements) {
                    if (entry.getWidth() > 100) {
                        extraButtons += 1;
                        entry.setY(lastLineY + 24 * extraButtons);
                    }
                }
            }
        }
    }

    public void onServerListMenuRelocateButtons(Event<MultiplayerScreen> screenEvent) {
        if (optimizeServerScreen.get()) {
            var screen = screenEvent.context;
            // reschedule top buttons

            try {
                // filter title, only buttons
                List<Widget> widgets = screen.children().stream()
                        .filter(s -> s instanceof Widget && s instanceof Selectable && !(s instanceof TextWidget))
                        .map(Widget.class::cast)
                        .filter(s -> s.getY() < 20)
                        .collect(Collectors.toCollection(ArrayList::new));
                onResize(widgets);
            } catch (Throwable e) {
                Debug.info(e, "Error while rescheduling server list screen");
            }
        }
    }

    private void onResize(List<Widget> widgets) {
        widgets.sort(Comparator.comparingInt(s -> -(s.getX() + s.getWidth())));
        int width = Integer.MAX_VALUE;
        Deque<Widget> forReschedule = new ArrayDeque<>();
        for (Widget widget : widgets) {
            // same height
            if (widget.getY() < 5) {
                widget.setY(5);
            }
            int xR = widget.getX() + widget.getWidth();
            if (xR < width) {
                // choice whether to place the reschedule
                if (forReschedule.isEmpty()) {
                    // no reschedule, keep the button
                    width = widget.getX();
                } else {
                    while (!forReschedule.isEmpty()) {
                        Widget tryWidget = forReschedule.peekFirst();
                        int tryWidth = tryWidget.getWidth();
                        if (xR < width - tryWidth) {
                            // we should just put it here
                            // update current placed border
                            width = width - tryWidth;
                            tryWidget.setX(width);
                            forReschedule.removeFirst();
                        } else if (widget.getX() < width - tryWidth) {
                            width = width - tryWidth;
                            tryWidget.setX(width);
                            forReschedule.removeFirst();
                            forReschedule.addLast(widget);
                            break;
                        } else {
                            // keep widget here,
                            // move next
                            width = widget.getX();
                            break;
                        }
                    }
                }
            } else {
                // must reschedule
                forReschedule.addLast(widget);
            }
        }
        while (!forReschedule.isEmpty()) {
            Widget widget = forReschedule.removeFirst();
            int tryWidth = widget.getWidth();
            width = width - tryWidth;
            widget.setX(width);
        }
    }
}
