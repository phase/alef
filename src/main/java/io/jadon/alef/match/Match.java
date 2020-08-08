package io.jadon.alef.match;

import lombok.*;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.model.TopLevelClassMapping;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@Data
public class Match {

    private final List<ClassMatch> classMatches;

    @Data
    public static class ClassMatch {
        private final String oldName;
        private final String newName;
        private final List<FieldMatch> fieldMatches = new ArrayList<>();
        private final List<MethodMatch> methodMatches = new ArrayList<>();
    }

    @Data
    public static class FieldMatch {
        private final String oldName;
        private final String oldFieldType;
        private final String newName;
        private final String newFieldType;
    }

    @Data
    public static class MethodMatch {
        private final String oldName;
        private final String oldSignature;
        private final String newName;
        private final String newSignature;
    }

    /**
     * Chain to matches together
     * If This: A -> B
     * and Other: B -> C
     * this method returns a match from A -> C
     *
     * @param other other match to chain
     * @return new match from this' old to other's new
     */
    public Match chain(Match other) {
        List<ClassMatch> chainedClasses = new ArrayList<>(this.classMatches.size());
        // chain classes
        for (ClassMatch classMatch : this.classMatches) {
            for (ClassMatch otherClassMatch : other.classMatches) {
                if (classMatch.newName.equals(otherClassMatch.oldName)) {
                    ClassMatch chainedClass = new ClassMatch(classMatch.oldName, otherClassMatch.newName);

                    // chain fields
                    for (FieldMatch fieldMatch : classMatch.fieldMatches) {
                        for (FieldMatch otherFieldMatch : otherClassMatch.fieldMatches) {
                            if (fieldMatch.newName.equals(otherFieldMatch.oldName)) {
                                chainedClass.fieldMatches.add(new FieldMatch(fieldMatch.oldName, fieldMatch.oldFieldType,
                                        otherFieldMatch.newName, otherFieldMatch.newFieldType));
                                break;
                            }
                        }
                    }

                    // chain methods
                    for (MethodMatch methodMatch : classMatch.methodMatches) {
                        for (MethodMatch otherMethodMatch : otherClassMatch.methodMatches) {
                            if (methodMatch.newName.equals(otherMethodMatch.oldName) && methodMatch.newSignature.equals(otherMethodMatch.oldSignature)) {
                                chainedClass.methodMatches.add(new MethodMatch(methodMatch.oldName,
                                        methodMatch.oldSignature, otherMethodMatch.newName, otherMethodMatch.newSignature));
                                break;
                            }
                        }
                    }

                    chainedClasses.add(chainedClass);
                    break;
                }
            }
        }
        return new Match(chainedClasses);
    }

    /**
     * Combine Mapping Sets using this Match
     *
     * @param oldMappings old mappings, obf -> named
     * @param newMappings new mappings, obf -> named
     * @return old named -> new named
     */
    public MappingSet combineMappings(MappingSet oldMappings, MappingSet newMappings) {
        MappingSet combinedMappings = MappingSet.create();
        for (ClassMatch classMatch : this.getClassMatches()) {
            String oldName = classMatch.oldName.substring(1, classMatch.oldName.length() - 1);
            String newName = classMatch.newName.substring(1, classMatch.newName.length() - 1);
            oldMappings.getTopLevelClassMapping(oldName).ifPresent(oldClassMapping -> {
                newMappings.getTopLevelClassMapping(newName).ifPresent(newClassMapping -> {
                    TopLevelClassMapping classMapping = combinedMappings.createTopLevelClassMapping(oldClassMapping.getFullDeobfuscatedName(), newClassMapping.getFullDeobfuscatedName());

                    // add field mappings
                    for (FieldMatch fieldMatch : classMatch.fieldMatches) {
                        oldClassMapping.getFieldMapping(fieldMatch.oldName).ifPresent(oldFieldMapping -> {
                            newClassMapping.getFieldMapping(fieldMatch.newName).ifPresent(newFieldMapping -> {
                                classMapping.createFieldMapping(oldFieldMapping.getDeobfuscatedName(), newFieldMapping.getDeobfuscatedName());
                            });
                        });
                    }

                    // add method mappings
                    for (MethodMatch methodMatch : classMatch.methodMatches) {
                        oldClassMapping.getMethodMapping(methodMatch.oldName, methodMatch.oldSignature).ifPresent(oldMethodMapping -> {
                            newClassMapping.getMethodMapping(methodMatch.newName, methodMatch.newSignature).ifPresent(newMethodMapping -> {
                                classMapping.createMethodMapping(oldMethodMapping.getDeobfuscatedSignature()).setDeobfuscatedName(newMethodMapping.getDeobfuscatedName());
                            });
                        });
                    }
                });
            });
        }
        return combinedMappings;
    }

    @SneakyThrows
    public static Match parse(File file) {
        assert file.exists() && file.isFile() : file.getAbsolutePath() + " is not a file!";

        List<ClassMatch> classMatches = new ArrayList<>();
        List<String> lines = Files.readAllLines(file.toPath());

        ClassMatch currentClass = null;

        for (String line : lines) {
            if (!line.contains("\t")) continue;
            String[] parts = line.split("\t");
            if (line.startsWith("c\t")) {
                // parse class
                currentClass = new ClassMatch(parts[1], parts[2]);
                classMatches.add(currentClass);
            } else if (line.startsWith("\tm\t")) {
                if (currentClass == null) continue;
                // parse method
                String oldDesc = parts[2];
                int pos = oldDesc.indexOf('(');
                String oldName = oldDesc.substring(0, pos);
                String oldSignature = oldDesc.substring(pos);

                String newDesc = parts[3];
                pos = newDesc.indexOf('(');
                String newName = newDesc.substring(0, pos);
                String newSignature = newDesc.substring(pos);

                currentClass.methodMatches.add(new MethodMatch(oldName, oldSignature, newName, newSignature));
            } else if (line.startsWith("\tf\t")) {
                if (currentClass == null) continue;
                // parse field
                String oldDesc = parts[2];
                String[] oldDescParts = oldDesc.split(";;");
                String oldName = oldDescParts[0];
                String oldSignature = oldDescParts[1];
                String newDesc = parts[3];
                String[] newDescParts = newDesc.split(";;");
                String newName = newDescParts[0];
                String newSignature = newDescParts[1];
                currentClass.fieldMatches.add(new FieldMatch(oldName, oldSignature, newName, newSignature));
            }

        }
        return new Match(classMatches);
    }

}
