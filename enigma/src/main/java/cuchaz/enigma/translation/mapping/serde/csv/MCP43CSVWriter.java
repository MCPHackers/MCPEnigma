package cuchaz.enigma.translation.mapping.serde.csv;

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
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public enum MCP43CSVWriter implements MappingsWriter {
    CLIENT(0),
    SERVER(1);

    public int side;

    MCP43CSVWriter(int side) {
        this.side = side;
    }

    @Override
    public void write(EntryTree<EntryMapping> mappings, MappingDelta<EntryMapping> delta, Path basePath, ProgressListener progress, MappingSaveParameters saveParameters) {
        List<EntryTreeNode<EntryMapping>> classes = StreamSupport.stream(mappings.spliterator(), false).filter(node -> node.getEntry() instanceof ClassEntry).collect(Collectors.toList());
        List<EntryTreeNode<EntryMapping>> methods = StreamSupport.stream(mappings.spliterator(), false).filter(node -> node.getEntry() instanceof MethodEntry).collect(Collectors.toList());
        List<EntryTreeNode<EntryMapping>> fields = StreamSupport.stream(mappings.spliterator(), false).filter(node -> node.getEntry() instanceof FieldEntry).collect(Collectors.toList());

        try {
            writeClasses(mappings, basePath, classes);
            writeMethods(mappings, basePath, methods);
            writeFields(mappings, basePath, fields);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void writeClasses(EntryTree<EntryMapping> mappings, Path basePath, List<EntryTreeNode<EntryMapping>> classes) {
        try (PrintWriter writer = new LfPrintWriter(Files.newBufferedWriter(basePath.resolve("classes.csv")))) {
            // Header: "name","notch","supername","package","side"
            writer.println("\"name\",\"notch\",\"supername\",\"package\",\"side\"");

            for (EntryTreeNode<EntryMapping> node : classes) {
                // Write to classes.csv

                ClassEntry classEntry = (ClassEntry) node.getEntry();
                String parentEntry = classEntry.getParent() != null ? classEntry.getParent().getSimpleName() : "";
                EntryMapping mapping = mappings.get(classEntry);

                //String targetName = mapping != null ? getClassWithoutPackage(mapping.getTargetName()) : classEntry.getSimpleName();
                Translator translator = new MappingTranslator(mappings, VoidEntryResolver.INSTANCE);
                String packageName = translator.translate(classEntry).getPackageName() != null ? translator.translate(classEntry).getPackageName() : classEntry.getPackageName() != null ? classEntry.getPackageName() : "net/minecraft/src";

                writer.println(
                        "\"" + translator.translate(classEntry).getSimpleName() + "\"," +
                        "\"" + classEntry.getFullName() + "\"," +
                        "\"" + parentEntry + "\"," +
                        "\"" + packageName + "\"," +
                        "\"" + this.side +"\""
                );
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void writeMethods(EntryTree<EntryMapping> mappings, Path basePath, List<EntryTreeNode<EntryMapping>> methods) {
        try (PrintWriter writer = new LfPrintWriter(Files.newBufferedWriter(basePath.resolve("methods.csv")))) {
            // Header: "searge","name","notch","sig","notchsig","classname","classnotch","package","side"
            writer.println("\"searge\",\"name\",\"notch\",\"sig\",\"notchsig\",\"classname\",\"classnotch\",\"package\",\"side\"");

            int i = 0;
            for (EntryTreeNode<EntryMapping> node : methods) {
                // Write to methods.csv

                MethodEntry methodEntry = (MethodEntry) node.getEntry();
                ClassEntry classEntry = methodEntry.getContainingClass();
                EntryMapping mapping = mappings.get(methodEntry);

                Translator translator = new MappingTranslator(mappings, VoidEntryResolver.INSTANCE);

                if (!methodEntry.isConstructor()) {
                    String targetName = mapping != null ? mapping.getTargetName() : methodEntry.getName();
                    String packageName = translator.translate(classEntry).getPackageName() != null ? translator.translate(classEntry).getPackageName() : classEntry.getPackageName() != null ? classEntry.getPackageName() : "net/minecraft/src";

                    writer.println(
                            "\"" + targetName + "\"," +
                            "\"" + targetName + "\"," +
                            "\"" + methodEntry.getSimpleName() + "\"," +
                            "\"" + translator.translate(methodEntry).getDesc() + "\"," +
                            "\"" + methodEntry.getDesc() + "\"," +
                            "\"" + translator.translate(classEntry).getFullName() + "\"," +
                            "\"" + classEntry.getFullName() + "\"," +
                            "\"" + packageName + "\"," +
                            "\"" + side + "\""
                    );
                }

                i++;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void writeFields(EntryTree<EntryMapping> mappings, Path basePath, List<EntryTreeNode<EntryMapping>> fields) {
        try (PrintWriter writer = new LfPrintWriter(Files.newBufferedWriter(basePath.resolve("fields.csv")))) {
            // Header: "searge","name","notch","sig","notchsig","classname","classnotch","package","side"
            writer.println("\"searge\",\"name\",\"notch\",\"sig\",\"notchsig\",\"classname\",\"classnotch\",\"package\",\"side\"");

            int i = 0;
            for (EntryTreeNode<EntryMapping> node : fields) {
                // Write to fields.csv

                FieldEntry fieldEntry = (FieldEntry) node.getEntry();
                ClassEntry classEntry = fieldEntry.getContainingClass();
                EntryMapping mapping = mappings.get(fieldEntry);

                Translator translator = new MappingTranslator(mappings, VoidEntryResolver.INSTANCE);

                String targetName = mapping.getTargetName() != null ? mapping.getTargetName() : fieldEntry.getName();
                String packageName = translator.translate(classEntry).getPackageName() != null ? translator.translate(classEntry).getPackageName() : classEntry.getPackageName() != null ? classEntry.getPackageName() : "net/minecraft/src";

                writer.println(
                        "\"" + targetName + "\"," +
                        "\"" + targetName + "\"," +
                        "\"" + fieldEntry.getSimpleName() + "\"," +
                        "\"" + translator.translate(fieldEntry).getDesc() + "\"," +
                        "\"" + fieldEntry.getDesc() + "\"," +
                        "\"" + translator.translate(classEntry).getFullName() + "\"," +
                        "\"" + classEntry.getFullName() + "\"," +
                        "\"" + packageName + "\"," +
                        "\"" + side + "\""
                );

                i++;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // TODO: Make this work to normalize all packages.
    private String getPackage(String packageName) {
        if (packageName != null && (packageName.equals("net/minecraft/src") || packageName.equals("net/minecraft/client") || packageName.equals("net/minecraft/server") || packageName.equals("net/minecraft/isom"))) {
            return packageName;
        } else {
            return "net/minecraft/src";
        }
    }



    // Utility methods
    private String getClassWithoutPackage(String classWithPackage) {
        int lastSlash = classWithPackage.lastIndexOf("/");
        if (lastSlash == -1) {
            return classWithPackage;
        } else {
            return classWithPackage.substring(lastSlash + 1);
        }
    }
}