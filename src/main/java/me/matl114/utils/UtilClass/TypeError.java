package me.matl114.utils.UtilClass;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.client.network.ClientPlayerEntity;

@Getter
@AllArgsConstructor
public class TypeError extends ArgumentException{
    String argument;
    BaseArgumentType typeName;
    String input;
    public TypeError(SimpleCommandArgs.Argument arg, BaseArgumentType typeName, String  input){
        this(arg == null?null:arg.getArgsName(), typeName, input);
    }
    @Override
    public void handleAbort(ClientPlayerEntity sender, InterruptionHandler command) {
        command.handleTypeError(sender, argument, typeName, input);
    }

    @Getter
    public static enum BaseArgumentType{
        INT("整形","Integer"),
        FLOAT("浮点型","Float"),
        BOOLEAN("布尔型","Boolean"),
        STRING("字符串","String");
        String displayNameZHCN;
        String displayNameENUS;
        BaseArgumentType(String display, String display2){
            this.displayNameZHCN = display;
            this.displayNameENUS = display2;
        }
    }
}

