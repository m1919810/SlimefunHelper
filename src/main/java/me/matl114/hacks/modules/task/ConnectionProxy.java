package me.matl114.hacks.modules.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.function.Consumer;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.gui.Constants;
import me.matl114.gui.WidgetUtils;
import me.matl114.gui.basic.*;
import me.matl114.gui.complex.config.ListModifyWidget;
import me.matl114.gui.elements.ButtonElement;
import me.matl114.gui.elements.IconElement;
import me.matl114.gui.elements.LabelElement;
import me.matl114.gui.elements.MultiLineTextElement;
import me.matl114.gui.presets.lists.ListEntryWidgetController;
import me.matl114.gui.presets.single.CenterScreen;
import me.matl114.gui.presets.single.ConfirmingWidgetScreen;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.FileManager;
import me.matl114.managers.config.*;
import me.matl114.managers.file.FileStorage;
import me.matl114.utils.CodecUtils;
import me.matl114.utils.Debug;
import me.matl114.utils.collections.MutableRecord;
import net.minecraft.text.Text;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jetbrains.annotations.ApiStatus;

public class ConnectionProxy extends BaseModule {
    private final ModulePath proxyServer = makePath(Configs.MISC_CONFIG, "proxy-server");

    public ConnectionProxy() {
        super("Proxy");
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(proxyServer.add("enable")).build();

    //    public final IntRef port = builder(proxyServer.add("port"), IntRef.TYPE)
    //            .defaultValue(7890)
    //            .validator(Configs.intRange(0, 65536))
    //            .build();
    //
    //    public final StringRef ip = builder(proxyServer.add("host"), StringRef.TYPE)
    //            .defaultValue("127.0.0.1")
    //            .build();
    //
    //    public final EnumRef<Type> proxyType = builder(proxyServer.add("type"), Type.class)
    //            .defaultValue(Type.SOCKS)
    //            .build();
    //
    //    public final StringRef userName = builder(proxyServer.add("username"), StringRef.TYPE)
    //            .defaultValue("")
    //            .build();

    public FileStorage fileStorage = FileManager.getInstance().getInternalStorage("proxies.nbt");

    ProxyList proxyList = fileStorage.read(ProxyList.CODEC, () -> new ProxyList(-1, List.of()));

    public void setProxyList(ProxyList proxyList) {
        this.proxyList = proxyList;
        fileStorage.write(ProxyList.CODEC, proxyList);
    }

    @Override
    public void addCustomWidgets(Consumer<DrawableWidget> acceptor, int dx, int dy, int dblank) {
        acceptor.accept(ExecutableWidget.instance(0, dblank, dx, dy)
                .setElementHandler(new ButtonElement(
                                (el -> {
                                    var currentSelected = proxyList.getSelected();
                                    if (currentSelected != null) {
                                        return Text.translatable("widget.connection-proxy.proxy-list-editor")
                                                .append(Text.literal(currentSelected.name()
                                                        + "(%s:%s:%d)"
                                                                .formatted(
                                                                        currentSelected
                                                                                .type()
                                                                                .name(),
                                                                        currentSelected.address(),
                                                                        currentSelected.port())));
                                    } else {
                                        return Text.translatable("widget.connection-proxy.proxy-list-editor")
                                                .append("None");
                                    }
                                }),
                                ButtonAction.run(this::openProxyListEditScreen))
                        .withTooltips(TooltipHandler.of(Constants.openListEditTooltips()))));
    }

    public void openProxyListEditScreen() {
        List<MutableRecord> currentList = new ArrayList<>(proxyList.entries().stream()
                .map(s -> MutableRecord.of(ProxyEntry.KEYS, s))
                .toList());
        int indexList = proxyList.selected();
        MutableObject<MutableRecord> index = new MutableObject<>(
                (indexList >= 0 && indexList < currentList.size()) ? currentList.get(indexList) : null);
        ListEntryWidgetController controller = ListEntryWidgetController.mutable(
                currentList,
                () -> MutableRecord.of(ProxyEntry.KEYS, ProxyEntry.EMPTY),
                (v) -> createEditRenderHandler(v, index),
                30,
                220);
        ListModifyWidget listSelect = new ListModifyWidget(controller, 0, 0, 320, 260);
        ConfirmingWidgetScreen confirmScreen = new ConfirmingWidgetScreen(
                Text.translatable("widget.connection-proxy.proxy-list-editor.title"), listSelect, () -> true, () -> {
                    List<ProxyEntry> newProxies = currentList.stream()
                            .map(s -> s.toRecord(ProxyEntry.class))
                            .toList();
                    setProxyList(new ProxyList(
                            index.getValue() == null ? -1 : currentList.indexOf(index.getValue()), newProxies));
                });
        confirmScreen.access().openFromCurrent();
    }

    public DrawableWidget createEditRenderHandler(MutableRecord argsMap, MutableObject<MutableRecord> index) {
        SubScreenWidget subScreen = new SubScreenWidget(0, 0, 220, 20);
        ExecutableWidget.instance(2, 2, 16, 16)
                .setElementHandler(IconElement.statedGuiPredicate(
                        ButtonElement.BUTTON,
                        ButtonElement.BUTTON_INACTIVE,
                        ButtonAction.run(() -> {
                            if (argsMap != index.getValue()) {
                                index.setValue(argsMap);
                            } else {
                                index.setValue(null);
                            }
                        }),
                        (bl) -> {
                            return argsMap == index.getValue();
                        }))
                .addToSub(subScreen);
        DisplayWidget.instance(45, 0, 100, 20)
                .setRenderHandler(
                        new LabelElement(ClickGui.INSTANCE.backGroundColor.get().withAlpha(64)))
                .addToSub(subScreen);
        ExecutableWidget.instance(45, 0, 100, 20)
                .setElementHandler(new MultiLineTextElement(
                        (el) -> {
                            ProxyEntry entry = argsMap.toRecord(ProxyEntry.class);
                            return Text.literal("%s\n(%s:%s:%d)"
                                    .formatted(entry.name(), entry.type().name(), entry.address(), entry.port()));
                        },
                        ClickGui.INSTANCE.configColor.get().withAlpha(255),
                        0))
                .addToSub(subScreen);

        ExecutableWidget.instance(155, 0, 60, 20)
                .setElementHandler(new ButtonElement(
                        TextProvider.of(Text.translatable("widget.connection-proxy.open-editor")),
                        ButtonAction.run(() -> {
                            var re = WidgetUtils.createMutableRecordEditScreen(
                                    Text.translatable("widget.connection-proxy.open-editor.title"),
                                    List::of,
                                    argsMap,
                                    s -> "widget.connection-proxy." + s,
                                    WidgetUtils.DEFAULT_CONFIG_SCREEN_LAYOUT,
                                    WidgetUtils.DEFAULT_PALETTE);
                            new CenterScreen(re).access().openFromCurrent();
                        })))
                .addToSub(subScreen);
        return subScreen;
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getConnectionChannelInitialize(), this::onPipelineInitialize);
    }

    public void onPipelineInitialize(Event<ChannelPipeline> chEvent) {
        var ch = chEvent.context();
        if (isActive() && !chEvent.<Boolean>getArgs(1)) {
            var proxyEntry = proxyList.getSelected();
            if (proxyEntry != null) {
                int port = proxyEntry.port();
                if (port > 0) {
                    String username = proxyEntry.userName();
                    boolean userNo = username.isEmpty();
                    String password = proxyEntry.password();
                    InetSocketAddress addr;
                    try {
                        addr = new InetSocketAddress(proxyEntry.address(), port);
                    } catch (Throwable e) {
                        Debug.info("Invalid address :", proxyEntry.address(), port, e);
                        Debug.info(e);
                        return;
                    }

                    switch (proxyEntry.type()) {
                        case Type.SOCKS -> {
                            if (password.isEmpty()) {
                                ch.addFirst(
                                        "socks4ClientProxy", new Socks4ProxyHandler(addr, userNo ? null : username));
                            } else {
                                ch.addFirst(
                                        "socks5ClientProxy",
                                        new Socks5ProxyHandler(addr, userNo ? null : username, password));
                            }
                        }

                        case Type.HTTP -> {
                            ch.addFirst(
                                    "httpClientProxy",
                                    new HttpProxyHandler(
                                            addr, userNo ? null : username, (password.isEmpty()) ? null : password));
                        }

                        case Type.HTTPS -> {
                            // not developed yet
                        }
                    }
                }
            }
        }
    }

    public static record ProxyEntry(
            String name, Type type, String address, int port, String userName, String password) {
        public static final List<String> KEYS = List.of("name", "type", "address", "port", "userName", "password");
        public static final ProxyEntry EMPTY = new ProxyEntry("", Type.SOCKS, "", 0, "", "");
        public static Codec<ProxyEntry> CODEC = RecordCodecBuilder.create(oinstance -> oinstance
                .group(
                        Codec.STRING.fieldOf("name").forGetter(ProxyEntry::name),
                        CodecUtils.enumCodec(Type.class).fieldOf("type").forGetter(ProxyEntry::type),
                        Codec.STRING.fieldOf("address").forGetter(ProxyEntry::address),
                        Codec.INT.fieldOf("port").forGetter(ProxyEntry::port),
                        Codec.STRING.fieldOf("userName").forGetter(ProxyEntry::userName),
                        Codec.STRING.fieldOf("password").forGetter(ProxyEntry::password))
                .apply(oinstance, ProxyEntry::new));
    }

    public static record ProxyList(int selected, List<ProxyEntry> entries) {
        public static Codec<ProxyList> CODEC = RecordCodecBuilder.create(oinstance -> oinstance
                .group(
                        Codec.INT.fieldOf("index").forGetter(ProxyList::selected),
                        Codec.list(ProxyEntry.CODEC).fieldOf("entries").forGetter(ProxyList::entries))
                .apply(oinstance, ProxyList::new));

        public ProxyEntry getSelected() {
            if (entries.size() > selected && selected >= 0) {
                return entries.get(selected);
            }
            return null;
        }
    }

    public enum Type implements ConfigEnum {
        SOCKS,
        HTTP,
        @ApiStatus.Experimental
        HTTPS;

        public Text getDisplay() {
            return Text.literal(name().toLowerCase(Locale.ROOT));
        }
    }
}
