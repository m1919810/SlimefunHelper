package me.matl114.utils.commands.params;

public class ArgumentReader {
    private final String[] args;
    int currentCursor;

    public ArgumentReader(String command, String[] args) {
        String[] arr = new String[args.length + 1];
        System.arraycopy(args, 0, arr, 1, args.length);
        // replace the name with our main command name
        arr[0] = command;
        this.args = arr;
        this.currentCursor = 1;
    }

    public ArgumentReader(ArgumentReader reader) {
        // trusted array with no copy
        this.args = reader.args; // new String[reader.args.length];
        // System.arraycopy(reader.args, 0, this.args, 0, reader.args.length);
        this.currentCursor = reader.currentCursor;
    }

    public String peekPosition(int index) {
        return args[index];
    }

    public int cursor() {
        return currentCursor;
    }

    public ArgumentReader setCursor(int cursor) {
        this.currentCursor = cursor;
        return this;
    }

    public ArgumentReader(String[] args) {
        this.args = args;
        this.currentCursor = 0;
    }

    public boolean hasNext() {
        return currentCursor < args.length;
    }

    public String next() {
        String arg = args[currentCursor];
        currentCursor++;
        return arg;
    }

    public String peek() {
        return args[currentCursor];
    }

    public ArgumentReader step() {
        currentCursor++;
        return this;
    }

    public ArgumentReader stepBack() {
        currentCursor--;
        return this;
    }

    public String getRemainingArgStr() {
        return String.join(" ", getRemainingArgs());
    }

    public String[] getRemainingArgs() {
        return getArgsInRange(currentCursor, args.length);
    }

    public String getAlreadyReadArgStr() {
        return String.join(" ", getAlreadyReadArgs());
    }

    public String getAlreadyReadCmdStr() {
        StringBuilder builder = new StringBuilder();
        for (var str : getAlreadyReadArgs()) {
            builder.append(str).append(" ");
        }
        return builder.toString();
    }

    public String[] getAlreadyReadArgs() {
        return getArgsInRange(0, currentCursor);
    }

    public String[] getArgsInRange(int startIndex, int endIndex) {
        String[] args = new String[endIndex - startIndex];
        System.arraycopy(this.args, startIndex, args, 0, args.length);
        return args;
    }

    public String getArgsAt(int index) {
        return args[index];
    }

    public int getLength() {
        return args.length;
    }
}
