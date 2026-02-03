package me.matl114.utils.commands;

public class ArgumentReader {
    String[] args;
    int currentCursor;

    public ArgumentReader(String command, String[] args) {
        this.args = new String[args.length + 1];
        System.arraycopy(args, 0, this.args, 1, args.length);
        //replace the name with our main command name
        this.args[0] = command;
        this.currentCursor = 1;
    }

    public ArgumentReader(ArgumentReader reader) {
        this.args = new String[reader.args.length];
        System.arraycopy(reader.args, 0, this.args, 0, reader.args.length);
        this.currentCursor = reader.currentCursor;

    }


    public String peekPosition(int index){
        return args[index];
    }

    public int cursor(){
        return currentCursor;
    }

    public ArgumentReader setCursor(int cursor){
        this.currentCursor = cursor;
        return this;
    }

    public ArgumentReader(String[] args) {
        this.args = args;
        this.currentCursor = 0;
    }
    public boolean hasNext(){
        return currentCursor < args.length;
    }
    public String next(){
        String arg = args[currentCursor];
        currentCursor++;
        return arg;
    }

    public String peek(){
        return args[currentCursor];
    }

    public ArgumentReader step(){
        currentCursor++;
        return this;
    }

    public ArgumentReader stepBack(){
        currentCursor--;
        return this;
    }

    public String getRemainingArgStr(){
        return String.join(" ", getRemainingArgs());
    }

    public String[] getRemainingArgs(){
        String[] remainingArgs = new String[args.length-currentCursor];
        System.arraycopy(args, currentCursor, remainingArgs, 0, remainingArgs.length);
        return remainingArgs;
    }

    public String getAlreadyReadArgStr(){
        return String.join(" ", getAlreadyReadArgs());
    }

    public String getAlreadyReadCmdStr(){
        StringBuilder builder = new StringBuilder();
        for (var str: getAlreadyReadArgs()){
            builder.append(str).append(" ");
        }
        return builder.toString();
    }

    public String[] getAlreadyReadArgs(){
        String[] remainingArgs = new String[currentCursor];
        System.arraycopy(args, 0, remainingArgs, 0, remainingArgs.length);
        return remainingArgs;
    }

}
