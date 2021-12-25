package cuchaz.enigma.translation.mapping.serde.srg;

import com.google.common.collect.Lists;
import cuchaz.enigma.ProgressListener;
import cuchaz.enigma.translation.MappingTranslator;
import cuchaz.enigma.translation.Translator;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.mapping.MappingDelta;
import cuchaz.enigma.translation.mapping.VoidEntryResolver;
import cuchaz.enigma.translation.mapping.serde.LfPrintWriter;
import cuchaz.enigma.translation.mapping.serde.MappingSaveParameters;
import cuchaz.enigma.translation.mapping.serde.MappingsWriter;
import cuchaz.enigma.translation.mapping.tree.EntryTree;
import cuchaz.enigma.translation.mapping.tree.EntryTreeNode;
import cuchaz.enigma.translation.representation.entry.*;
import cuchaz.enigma.utils.I18n;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public enum SrgMappingsWriter implements MappingsWriter {
    INSTANCE;

    public static BufferedWriter parameterBufferWriter = null;
    public static BufferedWriter javadocBufferWriter = null;

    @Override
    public void write(EntryTree<EntryMapping> mappings, MappingDelta<EntryMapping> delta, Path path, ProgressListener progress, MappingSaveParameters saveParameters) {
        try {
            Files.deleteIfExists(path);
            Files.createFile(path);
            parameterBufferWriter = Files.newBufferedWriter(new File(path.getParent().toFile(), "params.exc").toPath());
            javadocBufferWriter = Files.newBufferedWriter(new File(path.getParent().toFile(), "javadocs.javadoc").toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }

        List<String> classLines = new ArrayList<>();
        List<String> fieldLines = new ArrayList<>();
        List<String> methodLines = new ArrayList<>();

        Collection<Entry<?>> rootEntries = Lists.newArrayList(mappings).stream()
                .map(EntryTreeNode::getEntry)
                .collect(Collectors.toList());
        progress.init(rootEntries.size(), I18n.translate("progress.mappings.srg_file.generating"));

        int steps = 0;
        for (Entry<?> entry : sorted(rootEntries)) {
            progress.step(steps++, entry.getName());
            writeEntry(path, classLines, fieldLines, methodLines, mappings, entry);
        }

        progress.init(3, I18n.translate("progress.mappings.srg_file.writing"));
        try (PrintWriter writer = new LfPrintWriter(Files.newBufferedWriter(path))) {
            progress.step(0, I18n.translate("type.classes"));
            classLines.forEach(writer::println);
            progress.step(1, I18n.translate("type.fields"));
            fieldLines.forEach(writer::println);
            progress.step(2, I18n.translate("type.methods"));
            methodLines.forEach(writer::println);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                parameterBufferWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void writeEntry(Path path, List<String> classes, List<String> fields, List<String> methods, EntryTree<EntryMapping> mappings, Entry<?> entry) {
        EntryTreeNode<EntryMapping> node = mappings.findNode(entry);
        if (node == null) {
            return;
        }

        try {
            javadocBufferWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Translator translator = new MappingTranslator(mappings, VoidEntryResolver.INSTANCE);
        if (entry instanceof ClassEntry) {
            try {
                String name = translator.translate(entry).getFullName();
                if (translator.translate(entry) != null && translator.translate(entry).getJavadocs() != null) {
                    javadocBufferWriter.write("c " + name.replace(".", "/") + "=" + "\"" + translator.translate(entry).getJavadocs() + "\"" + "\n");
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            classes.add(generateClassLine((ClassEntry) entry, translator));
        } else if (entry instanceof FieldEntry) {
            try {
                if (translator.translate(entry) != null && translator.translate(entry).getJavadocs() != null) {
                    javadocBufferWriter.write("f " + translator.translate(entry).getFullName().replace(".", "/") + "(" + translator.translate((FieldEntry) entry).getDesc() + ")=" + "\"" + translator.translate(entry).getJavadocs() + "\"" + "\n");
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            fields.add(generateFieldLine((FieldEntry) entry, translator));
        } else if (entry instanceof MethodEntry) {
            MethodEntry methodEntry = (MethodEntry) entry;
            try {
                if (translator.translate(entry) != null && translator.translate(entry).getJavadocs() != null) {
                    javadocBufferWriter.write("m " + translator.translate(entry).getFullName().replace(".", "/") + translator.translate((MethodEntry) entry).getDesc() + "=" + "\"" + translator.translate(entry).getJavadocs() + "\"" + "\n");
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            // TODO: Make this actually verify that all parameters are mapped
            long result = node.getChildNodes().stream().map((child -> {
                Entry<?> translatedChildEntry = translator.translate(child.getEntry());
                return translatedChildEntry instanceof LocalVariableEntry;
            })).count();
            if (node.getChildNodes().size() == result && result != 0) {
                try {
                    String methodName;
                    if (translator.translate(entry) != null) {
                        methodName = translator.translate(entry).getFullName();
                    } else {
                        methodName = entry.getFullName();
                    }

                    parameterBufferWriter.write(methodName);
                    parameterBufferWriter.write(translator.translate(methodEntry).getDesc().toString());
                    parameterBufferWriter.write("=|");

                    String parameters = node.getChildNodes().stream().map((child) -> {
                        Entry<?> translatedChildEntry = translator.translate(child.getEntry());
                        if (translatedChildEntry instanceof LocalVariableEntry) {
                            return ((LocalVariableEntry) translatedChildEntry);
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingInt(LocalVariableEntry::getIndex))
                    .map(ParentedEntry::getSimpleName)
                    .collect(Collectors.joining(","));

                    parameterBufferWriter.write(parameters);
                    parameterBufferWriter.write("\n");
                    parameterBufferWriter.flush();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }

                methods.add(generateMethodLine(methodEntry, translator));
            }
        }
    }

    private String generateClassLine(ClassEntry sourceEntry, Translator translator) {
        ClassEntry targetEntry = translator.translate(sourceEntry);
        return "CL: " + sourceEntry.getFullName() + " " + targetEntry.getFullName();
    }

    private String generateMethodLine(MethodEntry sourceEntry, Translator translator) {
        MethodEntry targetEntry = translator.translate(sourceEntry);
        return "MD: " + describeMethod(sourceEntry) + " " + describeMethod(targetEntry);
    }

    private String describeMethod(MethodEntry entry) {
        return entry.getParent().getFullName() + "/" + entry.getName() + " " + entry.getDesc();
    }

    private String generateFieldLine(FieldEntry sourceEntry, Translator translator) {
        FieldEntry targetEntry = translator.translate(sourceEntry);
        return "FD: " + describeField(sourceEntry) + " " + describeField(targetEntry);
    }

    private String describeField(FieldEntry entry) {
        return entry.getParent().getFullName() + "/" + entry.getName();
    }

    private Collection<Entry<?>> sorted(Iterable<Entry<?>> iterable) {
        ArrayList<Entry<?>> sorted = Lists.newArrayList(iterable);
        sorted.sort(Comparator.comparing(Entry::getName));
        return sorted;
    }
}
