package me.matl114.bukkit;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.comments.CommentLine;
import org.yaml.snakeyaml.comments.CommentType;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.*;
import org.yaml.snakeyaml.reader.UnicodeReader;
import org.yaml.snakeyaml.representer.Representer;

public class BukkitYaml {
    /** @deprecated */
    @Deprecated
    protected static final String COMMENT_PREFIX = "# ";
    /** @deprecated */
    @Deprecated
    protected static final String BLANK_CONFIG = "{}\n";

    private final DumperOptions yamlDumperOptions = new DumperOptions();
    private final LoaderOptions yamlLoaderOptions;
    private final YamlConstructor constructor;

    private final Yaml yaml;

    public BukkitYaml() {
        this.yamlDumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        this.yamlLoaderOptions = new LoaderOptions();
        this.yamlLoaderOptions.setMaxAliasesForCollections(Integer.MAX_VALUE);
        this.constructor = new YamlConstructor(this.yamlLoaderOptions);
        this.yaml = new Yaml(
                this.constructor,
                new Representer(this.yamlDumperOptions),
                this.yamlDumperOptions,
                this.yamlLoaderOptions);
    }

    private void adjustNodeComments(MappingNode node) {
        if (node.getBlockComments() == null && !node.getValue().isEmpty()) {
            Node firstNode = ((NodeTuple) node.getValue().get(0)).getKeyNode();
            List<CommentLine> lines = firstNode.getBlockComments();
            if (lines != null) {
                int index = -1;

                for (int i = 0; i < lines.size(); ++i) {
                    if (((CommentLine) lines.get(i)).getCommentType() == CommentType.BLANK_LINE) {
                        index = i;
                    }
                }

                if (index != -1) {
                    node.setBlockComments(lines.subList(0, index + 1));
                    firstNode.setBlockComments(lines.subList(index + 1, lines.size()));
                }
            }
        }
    }

    public static class InvalidConfigException extends Exception {
        public InvalidConfigException(String va) {
            super(va);
        }

        public InvalidConfigException(Throwable e) {
            super(e);
        }
    }

    public BukkitItemStack getItemStackFromString(String string) throws InvalidConfigException {

        // this.yamlLoaderOptions.setProcessComments(this.options().parseComments());
        MappingNode node;
        try {
            Throwable var20 = null;
            Object var4 = null;
            try {
                Reader reader = new UnicodeReader(new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8)));
                try {
                    Node rawNode = this.yaml.compose(reader);

                    try {
                        node = (MappingNode) rawNode;
                    } catch (ClassCastException var16) {
                        throw new InvalidConfigException("Top level is not a Map.");
                    }
                } finally {
                    if (reader != null) {
                        reader.close();
                    }
                }
            } catch (Throwable var18) {
                if (var20 == null) {
                    var20 = var18;
                } else if (var20 != var18) {
                    var20.addSuppressed(var18);
                }

                throw new IOException(var20);
            }
        } catch (IOException | ClassCastException | YAMLException var19) {
            Exception e = var19;
            throw new InvalidConfigException(e);
        }

        if (node != null) {
            this.adjustNodeComments(node);
            //            this.options().setHeader(this.loadHeader(this.getCommentLines(node.getBlockComments())));
            //            this.options().setFooter(this.getCommentLines(node.getEndComments()));
            this.constructor.flattenMapping(node);
            Iterator var4 = node.getValue().iterator();

            while (true) {
                while (var4.hasNext()) {
                    NodeTuple nodeTuple = (NodeTuple) var4.next();
                    Node key = nodeTuple.getKeyNode();
                    String keyString = String.valueOf(this.constructor.construct(key));

                    Node value;
                    for (value = nodeTuple.getValueNode();
                            value instanceof AnchorNode;
                            value = ((AnchorNode) value).getRealNode()) {}

                    if (value instanceof MappingNode && !this.hasSerializedTypeKey((MappingNode) value)) {
                        throw new UnsupportedOperationException();
                    } else {
                        return (BukkitItemStack) this.constructor.construct(value);
                    }
                }
            }
        }
        throw new InvalidConfigException("this config contains null");
    }

    private boolean hasSerializedTypeKey(MappingNode node) {
        Iterator var3 = node.getValue().iterator();

        while (var3.hasNext()) {
            NodeTuple nodeTuple = (NodeTuple) var3.next();
            Node keyNode = nodeTuple.getKeyNode();
            if (keyNode instanceof ScalarNode) {
                String key = ((ScalarNode) keyNode).getValue();
                if (key.equals("==")) {
                    return true;
                }
            }
        }

        return false;
    }

    public class YamlConstructor extends SafeConstructor {
        /** @deprecated */
        @Deprecated
        public YamlConstructor() {
            this(new LoaderOptions());
        }

        public YamlConstructor(@NotNull LoaderOptions loaderOptions) {
            super(loaderOptions);
            this.yamlConstructors.put(Tag.MAP, new YamlConstructor.ConstructCustomObject());
        }

        public void flattenMapping(@NotNull MappingNode node) {
            super.flattenMapping(node);
        }

        @Nullable
        public Object construct(@NotNull Node node) {
            return this.constructObject(node);
        }

        protected Map<Object, Object> newMap(MappingNode node) {
            return createDefaultMap(node.getValue().size());
        }

        protected List<Object> newList(SequenceNode node) {
            return createDefaultList(node.getValue().size());
        }

        private class ConstructCustomObject extends SafeConstructor.ConstructYamlMap {
            private ConstructCustomObject() {
                super();
            }

            @Nullable
            public Object construct(@NotNull Node node) {
                if (node.isTwoStepsConstruction()) {
                    throw new YAMLException("Unexpected referential mapping structure. Node: " + node);
                } else {
                    Map<?, ?> raw = (Map) super.construct(node);
                    if (!raw.containsKey("==")) {
                        return raw;
                    } else {
                        Map<String, Object> typed = new LinkedHashMap(raw.size());
                        Iterator var5 = raw.entrySet().iterator();

                        while (var5.hasNext()) {
                            Map.Entry<?, ?> entry = (Map.Entry) var5.next();
                            typed.put(entry.getKey().toString(), entry.getValue());
                        }

                        try {
                            return BukkitSerializationMock.deserializeObject(typed);
                        } catch (IllegalArgumentException var6) {
                            IllegalArgumentException ex = var6;
                            throw new YAMLException("Could not deserialize object", ex);
                        }
                    }
                }
            }

            public void construct2ndStep(@NotNull Node node, @NotNull Object object) {
                throw new YAMLException("Unexpected referential mapping structure. Node: " + node);
            }
        }
    }
}
