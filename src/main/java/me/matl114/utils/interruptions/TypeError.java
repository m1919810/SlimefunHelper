package me.matl114.utils.interruptions;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.matl114.utils.commands.ArgumentReader;
import me.matl114.utils.commands.SimpleCommandArgs;
import net.minecraft.entity.player.PlayerEntity;

@Getter
@AllArgsConstructor
public class TypeError extends ArgumentException {
    ArgumentReader reader;
    String argument;
    BaseArgumentType typeName;
    String input;

    public TypeError(ArgumentReader reader, SimpleCommandArgs.Argument arg, BaseArgumentType typeName, String input){
        this(reader, arg == null ? null : arg.getArgsName(), typeName, input);
    }
    public TypeError( String argument, BaseArgumentType typeName, String input){
        this(null, argument, typeName, input);
    }

    public TypeError(SimpleCommandArgs.Argument arg, BaseArgumentType typeName, String input) {
        this(null, arg == null ? null : arg.getArgsName(), typeName, input);
    }

    @Override
    public void handleAbort(PlayerEntity sender, InterruptionHandler command) {
        command.handleTypeError(sender, reader, argument, typeName, input);
    }

    @Getter
    public static enum BaseArgumentType {
        INT("整形", "Integer"),
        FLOAT("浮点型", "Float"),
        BOOLEAN("布尔型", "Boolean"),
        STRING("字符串", "String"),
        ENUM("枚举型", "Enum");
        String displayNameZHCN;
        String displayNameENUS;

        BaseArgumentType(String display, String display2) {
            this.displayNameZHCN = display;
            this.displayNameENUS = display2;
        }
    }
}
