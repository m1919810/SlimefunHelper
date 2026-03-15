package me.matl114.utils.commands.interruption;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.api.ArgumentType;
import me.matl114.utils.commands.params.api.CommandExecution;

@Getter
@AllArgsConstructor
public class TypeError extends ArgumentException {
    ArgumentReader reader;
    String argument;
    BaseArgumentType typeName;
    String input;

    public TypeError(ArgumentType<?> argument, BaseArgumentType typeName, String input) {
        this(null, argument == null ? null : argument.getArgsName(), typeName, input);
    }

    public TypeError(String argument, BaseArgumentType typeName, String input) {
        this(null, argument, typeName, input);
    }

    @Override
    public void handleAbort(CommandExecution sender, InterruptionHandler command) {
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
