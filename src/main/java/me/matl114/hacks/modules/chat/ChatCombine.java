package me.matl114.hacks.modules.chat;

import me.matl114.accessors.access.ChatHudAccess;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.ChatUtils;
import me.matl114.events.Event;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatCombine  extends BaseModule {
    public static final String[] COMBINE_SAME_CHAT = {"chat-combine", "combine-same-chat"};
//    public static final String[] COMBINER_FORMAT = {"chat-combine", "combiner-format"};
    private static final String formatCombinedMessage = " &r&7&l[x&a%d&7&l]";
    private static final Pattern matcherCombinedMessageSuffix = Pattern.compile("^\\s*?\\[x(\\d*?)\\]$");
    public ChatCombine() {
        bindFlag(enable);
    }
    public final FlagRef enable = flagBuilder(Configs.CHAT_CONFIG, COMBINE_SAME_CHAT)
        .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getMessageAddToVisible(), this::onAddVisibleMessage);
    }

    public void onAddVisibleMessage(Event<ChatHudLine> textEvent){
        if (!enable.get()){
            return;
        }
        ChatHudLine line = textEvent.context();
        Text text = line.content();
        //use translated
        String rawString = ChatUtils.getOrderedTextString(text.asOrderedText());
//       rawString = rawString.replaceAll("§.", "");
        ChatHud hud = mc.inGameHud.getChatHud();
        int amount = 0;
        if(hud != null){
            //fixme: shit, they may split lines in list
            //fixme: shit, color and formats EVERYWHERE!
            var visibleHistory = ChatHudAccess.of(hud).getVisibleLines();
            ListIterator<ChatHudLine.Visible> lineIterator  = visibleHistory.listIterator();
            List<OrderedText> textList = new ArrayList<>();
            while (lineIterator.hasNext()){
                var visible = lineIterator.next();
                textList.add(0, visible.content());
                String rawLine1 = ChatUtils.getOrderedTextString(textList.toArray(OrderedText[]::new));
                //remove all fucking shits
                if(rawLine1.length() > rawString.length() + 10 + formatCombinedMessage.length()){
                    break;
                }
                if(rawLine1.startsWith(rawString)){
                    String suffix = rawLine1.substring(rawString.length());
                    if(suffix.isEmpty()){
                        //absolutely equals
                        amount += 1;
                        lineIterator.remove();
                        while (lineIterator.hasPrevious()){
                            lineIterator.previous();
                            lineIterator.remove();
                        }
//                       do {
//                           lineIterator.remove();
//                       }while (lineIterator.hasPrevious());
                        textList.clear();
                    }else {
                        //check if with suffix
                        Matcher matcher = matcherCombinedMessageSuffix.matcher(suffix);
                        if(matcher.find()){
                            try{
                                //combine amount and remove line
                                amount += Integer.parseInt(matcher.group(1));
                                lineIterator.remove();
                                while (lineIterator.hasPrevious()){
                                    lineIterator.previous();
                                    lineIterator.remove();
                                }
                                textList.clear();
                            }catch (Throwable e){
                                //break combine
                                continue;
                            }
                        }
                    }
                }else {
                    //break combine
                    continue;
                }
            }
        }
        if (amount > 0) {

            String newLineLegacy = formatCombinedMessage.formatted( amount + 1);
            MutableText newLine = ChatUtils.copyText(text);
            newLine.append(ChatUtils.stringToText(newLineLegacy));
            textEvent.context(new ChatHudLine(line.creationTick(), newLine, line.signature(), line.indicator()));
        }
    }


}
