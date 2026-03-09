package me.matl114.events.channels;

public class ListenerPoint<W> extends EntryPoint<W> {
    @Override
    public boolean handleValue(W express) {
        var iter = this.handlers.iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            if (!entry.test(express)) {
                iter.remove();
            }
        }
        return true;
    }
}
