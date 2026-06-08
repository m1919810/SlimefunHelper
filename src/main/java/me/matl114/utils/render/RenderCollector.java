package me.matl114.utils.render;

import java.util.ArrayList;
import java.util.List;
import me.matl114.utils.collections.IndexEntry;
import net.minecraft.client.util.math.MatrixStack;

public interface RenderCollector<B> {
    void submit(B val, int color);

    void clear();

    void render(MatrixStack matrices);

    public abstract static class Impl<B> implements RenderCollector<B> {
        protected List<IndexEntry<B>> entries = new ArrayList<>();

        @Override
        public void submit(B val, int color) {
            entries.add(new IndexEntry<>(color, val));
        }

        public void clear() {
            entries.clear();
        }
    }
}
