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
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.stream.Collectors;

public enum MCP43CSVReader implements MappingsReader {
    CLIENT(0),
    SERVER(1);

    public int side;
    
    private Hashtable classes;

    MCP43CSVReader(int side) {
        this.side = side;
        this.classes = new Hashtable<String, String>();
    }

    @Override
    public EntryTree<EntryMapping> read(Path root, ProgressListener progress, MappingSaveParameters saveParameters) throws IOException, MappingParseException {
        EntryTree<EntryMapping> mappings = new HashEntryTree<>();

        List<Path> files = Files.walk(root)
                .filter(f -> !Files.isDirectory(f))
                .filter(f -> f.toString().endsWith(".csv"))
                .collect(Collectors.toList());

        progress.init(files.size(), I18n.translate("progress.mappings.csv_directory.loading"));
        int step = 0;
        this.classes.clear();

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
        System.out.println(classes.toString());
        this.classes.clear();
        return mappings;
    }
    
    private String fixFullName(String token, boolean isSignature)
    {
        Enumeration<String> e = classes.keys();
        String s = token;
        while (e.hasMoreElements()) {
        	String key = e.nextElement();
        	String replace = isSignature ? new StringBuilder().append("L").append(key).append(";").toString() : new StringBuilder().append("\"").append(key).append("\"").toString();
        	String replaceWith = isSignature ? new StringBuilder().append("L").append((String)classes.get(key)).append(";").toString() : new StringBuilder().append("\"").append((String)classes.get(key)).append("\"").toString();
        	s = s.replaceAll(replace, replaceWith);
        }
        return s;
    }

    private void readClasses(Path path, EntryTree<EntryMapping> mappings) throws IOException, MappingParseException {
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

    private void readMethods(Path path, EntryTree<EntryMapping> mappings) throws IOException, MappingParseException {
        List<String> lines = Files.readAllLines(path, Charsets.UTF_8);
        Deque<MappingPair<?, RawEntryMapping>> mappingStack = new ArrayDeque<>();

        // Skip over header
        for (int lineNumber = 1; lineNumber < lines.size(); lineNumber++) {
            String line = lines.get(lineNumber);

            cleanMappingStack(0, mappingStack, mappings);

            try {
                String[] tokens = line.trim().split(",");

                @Nullable MappingPair<?, RawEntryMapping> parent = mappingStack.peek();
                String s = fixFullName(tokens[6], false);
                ClassEntry parentEntry = new ClassEntry(s.replaceAll("\"", ""));

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

    private void readFields(Path path, EntryTree<EntryMapping> mappings) throws IOException, MappingParseException {
        List<String> lines = Files.readAllLines(path, Charsets.UTF_8);
        Deque<MappingPair<?, RawEntryMapping>> mappingStack = new ArrayDeque<>();

        // Skip over header
        for (int lineNumber = 1; lineNumber < lines.size(); lineNumber++) {
            String line = lines.get(lineNumber);

            cleanMappingStack(0, mappingStack, mappings);

            try {
                String[] tokens = line.trim().split(",");

                @Nullable MappingPair<?, RawEntryMapping> parent = mappingStack.peek();
                String s = fixFullName(tokens[6], false);
                ClassEntry parentEntry = new ClassEntry(s.replaceAll("\"", ""));

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
    private MappingPair<ClassEntry, RawEntryMapping> parseClassLine(@Nullable Entry<?> parent, String[] tokens) {
        // Header: "name","notch","supername","package","side"
    	String name = tokens[3].replaceAll("\"", "").equals("net/minecraft/src") ? tokens[1].replaceAll("\"", "") : new StringBuilder().append(tokens[3].replaceAll("\"", "")).append("/").append(tokens[1].replaceAll("\"", "")).toString();
    	if (!tokens[3].replaceAll("\"", "").equals("net/minecraft/src")) this.classes.put(tokens[1].replaceAll("\"", ""), name);
        String obfuscatedName = ClassEntry.getInnerName(name);
        ClassEntry obfuscatedEntry = parent != null ? new ClassEntry((ClassEntry) parent, obfuscatedName) : new ClassEntry(obfuscatedName);

        	   name = tokens[3].replaceAll("\"", "").equals("net/minecraft/src") ? tokens[0].replaceAll("\"", "") : new StringBuilder().append(tokens[3].replaceAll("\"", "")).append("/").append(tokens[0].replaceAll("\"", "")).toString();
        String mapping = ClassEntry.getInnerName(name);
        if (Integer.parseInt(tokens[4].replaceAll("\"", "")) == this.side) {
            return new MappingPair<>(obfuscatedEntry, new RawEntryMapping(mapping, AccessModifier.UNCHANGED));
        } else {
            return null;
        }
    }

    private MappingPair<MethodEntry, RawEntryMapping> parseMethodLine(@Nullable Entry<?> parent, String[] tokens) {
        // Header: "searge","name","notch","sig","notchsig","classname","classnotch","package","side"
        if (!(parent instanceof ClassEntry)) {
            throw new RuntimeException("Method must be a child of a class!");
        }
        ClassEntry ownerEntry = (ClassEntry) parent;
        String obfuscatedName = tokens[2].replaceAll("\"", "");
        String mapping = tokens[1].replaceAll("\"", "");
        AccessModifier modifier = AccessModifier.UNCHANGED;
        MethodDescriptor descriptor = new MethodDescriptor(fixFullName(tokens[4], true).replaceAll("\"", ""));

        MethodEntry obfuscatedEntry = new MethodEntry(ownerEntry, obfuscatedName, descriptor);
        if (Integer.parseInt(tokens[8].replaceAll("\"", "")) == this.side) {
            return new MappingPair<>(obfuscatedEntry, new RawEntryMapping(mapping, modifier));
        } else {
            return null;
        }
    }

    private MappingPair<FieldEntry, RawEntryMapping> parseFieldLine(@Nullable Entry<?> parent, String[] tokens) {
        // Header: "searge","name","notch","sig","notchsig","classname","classnotch","package","side"
        if (!(parent instanceof ClassEntry)) {
            throw new RuntimeException("Field must be a child of a class!");
        }

        ClassEntry ownerEntry = (ClassEntry) parent;
        System.out.println(ownerEntry.getFullName());
        String obfuscatedName = tokens[2].replaceAll("\"", "");
        String mapping = tokens[1].replaceAll("\"", "");
        AccessModifier modifier = AccessModifier.UNCHANGED;
        TypeDescriptor descriptor = new TypeDescriptor(fixFullName(tokens[4], true).replaceAll("\"", ""));

        FieldEntry obfuscatedEntry = new FieldEntry(ownerEntry, obfuscatedName, descriptor);

        if (Integer.parseInt(tokens[8].replaceAll("\"", "")) == this.side) {
            return new MappingPair<>(obfuscatedEntry, new RawEntryMapping(mapping, modifier));
        } else {
            return null;
        }
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