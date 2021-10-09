package cuchaz.enigma.translation.mapping.serde.csv;

import com.google.common.base.Charsets;
import cuchaz.enigma.ProgressListener;
import cuchaz.enigma.translation.mapping.AccessModifier;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.mapping.MappingPair;
import cuchaz.enigma.translation.mapping.serde.MappingParseException;
import cuchaz.enigma.translation.mapping.serde.MappingSaveParameters;
import cuchaz.enigma.translation.mapping.serde.MappingsReader;
import cuchaz.enigma.translation.mapping.serde.RawEntryMapping;
import cuchaz.enigma.translation.mapping.tree.EntryTree;
import cuchaz.enigma.translation.mapping.tree.HashEntryTree;
import cuchaz.enigma.translation.representation.MethodDescriptor;
import cuchaz.enigma.translation.representation.TypeDescriptor;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import cuchaz.enigma.utils.I18n;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

public enum MCP43CSVReader implements MappingsReader {
    INSTANCE;

    @Override
    public EntryTree<EntryMapping> read(Path root, ProgressListener progress, MappingSaveParameters saveParameters) throws IOException, MappingParseException {
        EntryTree<EntryMapping> mappings = new HashEntryTree<>();

        List<Path> files = Files.walk(root)
                .filter(f -> !Files.isDirectory(f))
                .filter(f -> f.toString().endsWith(".csv"))
                .collect(Collectors.toList());

        progress.init(files.size(), I18n.translate("progress.mappings.csv_directory.loading"));
        int step = 0;

        for (Path file : files) {
            progress.step(step++, root.relativize(file).toString());
            if (Files.isHidden(file)) {
                continue;
            }

            if (file.endsWith("classes.csv")) {
                readClasses(file, mappings);
            } else if (file.endsWith("methods.csv")) {
                readMethods(file, mappings);
            } else if (file.endsWith("fields.csv")) {
                readFields(file, mappings);
            }
        }

        return mappings;
    }

    private static void readClasses(Path path, EntryTree<EntryMapping> mappings) throws IOException, MappingParseException {
        List<String> lines = Files.readAllLines(path, Charsets.UTF_8);
        Deque<MappingPair<?, RawEntryMapping>> mappingStack = new ArrayDeque<>();

        // Skip over header
        for (int lineNumber = 1; lineNumber < lines.size(); lineNumber++) {
            String line = lines.get(lineNumber);

            cleanMappingStack(0, mappingStack, mappings);

            try {
                String[] tokens = line.trim().split(",");

                @Nullable MappingPair<?, RawEntryMapping> parent = mappingStack.peek();
                Entry<?> parentEntry = parent == null ? null : parent.getEntry();

                MappingPair<?, RawEntryMapping> pair = parseClassLine(parentEntry, tokens);
                if (pair != null) {
                    mappingStack.push(pair);
                }
            } catch (Throwable t) {
                t.printStackTrace();
                throw new MappingParseException(path::toString, lineNumber, t.toString());
            }
        }
    }

    private static void readMethods(Path path, EntryTree<EntryMapping> mappings) throws IOException, MappingParseException {
        List<String> lines = Files.readAllLines(path, Charsets.UTF_8);
        Deque<MappingPair<?, RawEntryMapping>> mappingStack = new ArrayDeque<>();

        // Skip over header
        for (int lineNumber = 1; lineNumber < lines.size(); lineNumber++) {
            String line = lines.get(lineNumber);

            cleanMappingStack(0, mappingStack, mappings);

            try {
                String[] tokens = line.trim().split(",");

                @Nullable MappingPair<?, RawEntryMapping> parent = mappingStack.peek();
                ClassEntry parentEntry = new ClassEntry(tokens[6].replaceAll("\"", ""));

                MappingPair<?, RawEntryMapping> pair = parseMethodLine(parentEntry, tokens);
                if (pair != null) {
                    mappingStack.push(pair);
                }
            } catch (Throwable t) {
                t.printStackTrace();
                throw new MappingParseException(path::toString, lineNumber, t.toString());
            }
        }
    }

    private static void readFields(Path path, EntryTree<EntryMapping> mappings) throws IOException, MappingParseException {
        List<String> lines = Files.readAllLines(path, Charsets.UTF_8);
        Deque<MappingPair<?, RawEntryMapping>> mappingStack = new ArrayDeque<>();

        // Skip over header
        for (int lineNumber = 1; lineNumber < lines.size(); lineNumber++) {
            String line = lines.get(lineNumber);

            cleanMappingStack(0, mappingStack, mappings);

            try {
                String[] tokens = line.trim().split(",");

                @Nullable MappingPair<?, RawEntryMapping> parent = mappingStack.peek();
                ClassEntry parentEntry = new ClassEntry(tokens[6].replaceAll("\"", ""));

                MappingPair<?, RawEntryMapping> pair = parseFieldLine(parentEntry, tokens);
                if (pair != null) {
                    mappingStack.push(pair);
                }
            } catch (Throwable t) {
                t.printStackTrace();
                throw new MappingParseException(path::toString, lineNumber, t.toString());
            }
        }
    }

    // Utility methods
    private static MappingPair<ClassEntry, RawEntryMapping> parseClassLine(@Nullable Entry<?> parent, String[] tokens) {
        // Header: "name","notch","supername","package","side"
        String obfuscatedName = ClassEntry.getInnerName(tokens[1].replaceAll("\"", ""));
        ClassEntry obfuscatedEntry = parent != null ? new ClassEntry((ClassEntry) parent, obfuscatedName) : new ClassEntry(obfuscatedName);

        String mapping = ClassEntry.getInnerName(tokens[0].replaceAll("\"", ""));
        return new MappingPair<>(obfuscatedEntry, new RawEntryMapping(mapping, AccessModifier.UNCHANGED));
    }

    private static MappingPair<MethodEntry, RawEntryMapping> parseMethodLine(@Nullable Entry<?> parent, String[] tokens) {
        // Header: "searge","name","notch","sig","notchsig","classname","classnotch","package","side"
        if (!(parent instanceof ClassEntry)) {
            throw new RuntimeException("Method must be a child of a class!");
        }
        ClassEntry ownerEntry = (ClassEntry) parent;

        String obfuscatedName = tokens[2].replaceAll("\"", "");
        String mapping = tokens[1].replaceAll("\"", "");
        AccessModifier modifier = AccessModifier.UNCHANGED;
        MethodDescriptor descriptor = new MethodDescriptor(tokens[4].replaceAll("\"", ""));

        MethodEntry obfuscatedEntry = new MethodEntry(ownerEntry, obfuscatedName, descriptor);
        return new MappingPair<>(obfuscatedEntry, new RawEntryMapping(mapping, modifier));
    }

    private static MappingPair<FieldEntry, RawEntryMapping> parseFieldLine(@Nullable Entry<?> parent, String[] tokens) {
        // Header: "searge","name","notch","sig","notchsig","classname","classnotch","package","side"
        if (!(parent instanceof ClassEntry)) {
            throw new RuntimeException("Field must be a child of a class!");
        }

        ClassEntry ownerEntry = (ClassEntry) parent;

        String obfuscatedName = tokens[2].replaceAll("\"", "");
        String mapping = tokens[1].replaceAll("\"", "");
        AccessModifier modifier = AccessModifier.UNCHANGED;
        TypeDescriptor descriptor = new TypeDescriptor(tokens[4].replaceAll("\"", ""));

        FieldEntry obfuscatedEntry = new FieldEntry(ownerEntry, obfuscatedName, descriptor);
        return new MappingPair<>(obfuscatedEntry, new RawEntryMapping(mapping, modifier));
    }

    private static void cleanMappingStack(int indentation, Deque<MappingPair<?, RawEntryMapping>> mappingStack, EntryTree<EntryMapping> mappings) {
        while (indentation < mappingStack.size()) {
            MappingPair<?, RawEntryMapping> pair = mappingStack.pop();
            if (pair.getMapping() != null) {
                mappings.insert(pair.getEntry(), pair.getMapping().bake());
            }
        }
    }
}