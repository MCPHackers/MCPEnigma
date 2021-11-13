package cuchaz.enigma.translation.mapping.serde.srg;

import com.google.common.base.Charsets;
import cuchaz.enigma.EnigmaProfile;
import cuchaz.enigma.ProgressListener;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.mapping.MappingPair;
import cuchaz.enigma.translation.mapping.serde.MappingParseException;
import cuchaz.enigma.translation.mapping.serde.MappingSaveParameters;
import cuchaz.enigma.translation.mapping.serde.MappingsReader;
import cuchaz.enigma.translation.mapping.tree.EntryTree;
import cuchaz.enigma.translation.mapping.tree.HashEntryTree;
import cuchaz.enigma.translation.representation.MethodDescriptor;
import cuchaz.enigma.translation.representation.TypeDescriptor;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import cuchaz.enigma.utils.I18n;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class SrgMappingsReader implements MappingsReader {

    @Override
    public EntryTree<EntryMapping> read(Path path, ProgressListener progress, MappingSaveParameters saveParameters) throws MappingParseException, IOException {
        return read(path, Files.readAllLines(path, Charsets.UTF_8), progress);
    }

    private EntryTree<EntryMapping> read(Path path, List<String> lines, ProgressListener progress) throws MappingParseException {
        EntryTree<EntryMapping> mappings = new HashEntryTree<>();

        progress.init(lines.size(), I18n.translate("progress.mappings.tiny_file.loading"));

        for (int lineNumber = 0; lineNumber < lines.size(); lineNumber++) {
            progress.step(lineNumber, "");

            String line = lines.get(lineNumber);

            if (line.trim().startsWith("#") || line.trim().startsWith("PK:") || line.split(" ")[2].contains("$VALUES")) {
                continue;
            }

            try {
                MappingPair<?, EntryMapping> mapping = parseLine(line);
                mappings.insert(mapping.getEntry(), mapping.getMapping());
            } catch (Throwable t) {
                t.printStackTrace();
                throw new MappingParseException(path::toString, lineNumber, t.toString());
            }
        }

        return mappings;
    }

    private MappingPair<?, EntryMapping> parseLine(String line) {
        String[] tokens = line.split(" ");

        String key = tokens[0];
        switch (key) {
            case "CL:":
                return parseClass(tokens);
            case "FD:":
                return parseField(tokens);
            case "MD:":
                return parseMethod(tokens);
            default:
                throw new RuntimeException("Unknown token '" + key + "'!");
        }
    }

    private MappingPair<ClassEntry, EntryMapping> parseClass(String[] tokens) {
        // CL: <obfuscated> <package>/<remapped>
        ClassEntry obfuscatedEntry = new ClassEntry(tokens[1]);
        String mapping = tokens[2];
        return new MappingPair<>(obfuscatedEntry, new EntryMapping(mapping));
    }

    private MappingPair<FieldEntry, EntryMapping> parseField(String[] tokens) {
        ClassEntry ownerClass = new ClassEntry(tokens[1].substring(0, tokens[1].lastIndexOf('/')));
        String obfuscatedFieldName =  tokens[1].substring(tokens[1].lastIndexOf('/') + 1);
        String fieldSignature = EnigmaProfile.fieldSignatures.get(ownerClass.getName() + "/" + obfuscatedFieldName);
        if (fieldSignature == null) System.out.println(ownerClass.getName() + "/" + obfuscatedFieldName);
        TypeDescriptor descriptor = new TypeDescriptor(fieldSignature);

        FieldEntry obfuscatedEntry = new FieldEntry(ownerClass, obfuscatedFieldName, descriptor);
        String mapping = tokens[2].substring(tokens[2].lastIndexOf('/') + 1);
        return new MappingPair<>(obfuscatedEntry, new EntryMapping(mapping));
    }

    private MappingPair<MethodEntry, EntryMapping> parseMethod(String[] tokens) {
        ClassEntry ownerClass = new ClassEntry(tokens[1].substring(0, tokens[1].lastIndexOf('/')));
        MethodDescriptor descriptor = new MethodDescriptor(tokens[2]);

        MethodEntry obfuscatedEntry = new MethodEntry(ownerClass, tokens[1].substring(tokens[1].lastIndexOf('/') + 1), descriptor);
        String mapping = tokens[3].substring(tokens[3].lastIndexOf('/') + 1);
        return new MappingPair<>(obfuscatedEntry, new EntryMapping(mapping));
    }
}
