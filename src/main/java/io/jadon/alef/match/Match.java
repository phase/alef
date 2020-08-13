package io.jadon.alef.match;

import lombok.Data;
import lombok.SneakyThrows;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.MappingFormats;
import org.cadixdev.lorenz.model.*;

import javax.sound.midi.Patch;
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
        for (ClassMatch classMatch : this.classMatches) {
            ClassMapping<?, ?> oldClassMapping = oldMappings.getOrCreateClassMapping(classMatch.oldName);
            ClassMapping<?, ?> newClassMapping = newMappings.getOrCreateClassMapping(classMatch.newName);
            ClassMapping<?, ?> classMapping = combinedMappings.getOrCreateClassMapping(oldClassMapping.getFullDeobfuscatedName());
            classMapping.setDeobfuscatedName(newClassMapping.getFullDeobfuscatedName());

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
        }
        return combinedMappings;
    }

    /**
     * Updates Mapping Sets with this Match
     *
     * @param oldMappings old mapping set to use, old obf -> named
     * @return new mapping set, new obf -> named
     */
    public MappingSet updateMappings(MappingSet oldMappings) {
        MappingSet updatedMappings = MappingSet.create();
        for (ClassMatch classMatch : this.classMatches) {
            oldMappings.getClassMapping(classMatch.oldName).ifPresent(oldClassMapping -> {
                ClassMapping<?, ?> classMapping = updatedMappings.getOrCreateClassMapping(classMatch.newName);
                classMapping.setDeobfuscatedName(oldClassMapping.getFullDeobfuscatedName());

                // add field mappings
                for (FieldMatch fieldMatch : classMatch.fieldMatches) {
                    oldClassMapping.getFieldMapping(fieldMatch.oldName).ifPresent(fieldMapping -> {
                        classMapping.getOrCreateFieldMapping(fieldMatch.newName)
                                .setDeobfuscatedName(fieldMapping.getFullObfuscatedName());
                    });
                }

                // add method mappings
                for (MethodMatch methodMatch : classMatch.methodMatches) {
                    oldClassMapping.getMethodMapping(methodMatch.oldName, methodMatch.oldSignature).ifPresent(oldMethodMapping -> {
                        classMapping.getOrCreateMethodMapping(methodMatch.newName, methodMatch.newSignature)
                                .setDeobfuscatedName(oldMethodMapping.getDeobfuscatedName());
                    });
                }
            });
        }
        return updatedMappings;
    }

    public Match reverse() {
        List<ClassMatch> reversedClassMatches = new ArrayList<>();
        for (ClassMatch classMatch : this.classMatches) {
            ClassMatch reversedClassMatch = new ClassMatch(classMatch.newName, classMatch.oldName);
            reversedClassMatches.add(reversedClassMatch);
            for (FieldMatch fieldMatch : classMatch.fieldMatches) {
                FieldMatch reversedFieldMatch = new FieldMatch(fieldMatch.newName, fieldMatch.newFieldType, fieldMatch.oldName, fieldMatch.oldFieldType);
                reversedClassMatch.fieldMatches.add(reversedFieldMatch);
            }
            for (MethodMatch methodMatch : classMatch.methodMatches) {
                MethodMatch reversedMethodMatch = new MethodMatch(methodMatch.newName, methodMatch.newSignature, methodMatch.oldName, methodMatch.newSignature);
                reversedClassMatch.methodMatches.add(reversedMethodMatch);
            }
        }

        return new Match(reversedClassMatches);
    }

    public MappingSet toMappingSet() {
        MappingSet mappings = MappingSet.create();
        for (ClassMatch classMatch : classMatches) {
            ClassMapping<?, ?> classMapping = mappings.getOrCreateClassMapping(classMatch.oldName);
            classMapping.setDeobfuscatedName(classMatch.newName);

            for (FieldMatch fieldMatch : classMatch.fieldMatches) {
                classMapping.createFieldMapping(fieldMatch.oldName).setDeobfuscatedName(fieldMatch.newName);
            }

            for (MethodMatch methodMatch : classMatch.methodMatches) {
                classMapping.createMethodMapping(methodMatch.oldName, methodMatch.oldSignature)
                        .setDeobfuscatedName(methodMatch.newName);
            }
        }
        return mappings;
    }

    @SneakyThrows
    public static Match parse(File file) {
        assert file.exists() && file.isFile() : file.getAbsolutePath() + " is not a file!";

        if (file.getAbsolutePath().endsWith(".csrg")) {
            MappingSet matchMappings = MappingFormats.CSRG.read(file.toPath());
            return parse(matchMappings);
        }

        List<ClassMatch> classMatches = new ArrayList<>();
        List<String> lines = Files.readAllLines(file.toPath());

        ClassMatch currentClass = null;

        for (String line : lines) {
            if (!line.contains("\t")) continue;
            String[] parts = line.split("\t");
            if (line.startsWith("c\t")) {
                // parse class
                String oldName = parts[1].substring(1, parts[1].length() - 1);
                String newName = parts[2].substring(1, parts[2].length() - 1);
                currentClass = new ClassMatch(oldName, newName);
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

    public static Match parse(MappingSet mappings) {
        List<ClassMatch> classMatches = new ArrayList<>();
        for (TopLevelClassMapping topLevelClassMapping : mappings.getTopLevelClassMappings()) {
            parseClassMapping(classMatches, topLevelClassMapping);
        }
        return new Match(classMatches);
    }

    private static void parseClassMapping(List<ClassMatch> classMatches, ClassMapping<?, ?> topLevelClassMapping) {
        ClassMatch classMatch = new ClassMatch(topLevelClassMapping.getFullObfuscatedName(), topLevelClassMapping.getFullDeobfuscatedName());
        classMatches.add(classMatch);
        for (FieldMapping fieldMapping : topLevelClassMapping.getFieldMappings()) {
            classMatch.fieldMatches.add(new FieldMatch(fieldMapping.getObfuscatedName(),
                    fieldMapping.getSignature().toString(), fieldMapping.getDeobfuscatedName(),
                    fieldMapping.getDeobfuscatedSignature().toString()));
        }
        for (MethodMapping methodMatch : topLevelClassMapping.getMethodMappings()) {
            classMatch.methodMatches.add(new MethodMatch(methodMatch.getObfuscatedName(),
                    methodMatch.getSignature().toString(), methodMatch.getDeobfuscatedName(),
                    methodMatch.getDeobfuscatedSignature().toString()));
        }
        for (InnerClassMapping innerClassMapping : topLevelClassMapping.getInnerClassMappings()) {
            parseClassMapping(classMatches, innerClassMapping);
        }
    }

    /**
     * Creates a match from matching deobfuscated names
     *
     * @param oldMappings Old mappings
     * @param newMappings New mappings
     * @return Match from the old obf to the new obf
     */
    public static Match from(MappingSet oldMappings, MappingSet newMappings) {
        List<ClassMatch> classMatches = new ArrayList<>();
        top: for (TopLevelClassMapping oldClassMapping : oldMappings.getTopLevelClassMappings()) {
            for (TopLevelClassMapping newClassMapping : newMappings.getTopLevelClassMappings()) {
                if (oldClassMapping.getFullDeobfuscatedName().equals(newClassMapping.getFullDeobfuscatedName())) {
                    matchClassMappings(classMatches, oldClassMapping, newClassMapping);
                    continue top;
                }
            }
            System.out.println("Couldn't find match for " + oldClassMapping.getFullDeobfuscatedName());
        }
        return new Match(classMatches);
    }

    private static void matchClassMappings(List<ClassMatch> classMatches, ClassMapping<?, ?> oldClassMapping, ClassMapping<?, ?> newClassMapping) {
        ClassMatch classMatch = new ClassMatch(oldClassMapping.getFullObfuscatedName(), newClassMapping.getFullObfuscatedName());
        classMatches.add(classMatch);

        for (FieldMapping oldFieldMapping : oldClassMapping.getFieldMappings()) {
            for (FieldMapping newFieldMapping : newClassMapping.getFieldMappings()) {
                if (oldFieldMapping.getDeobfuscatedName().equals(newFieldMapping.getDeobfuscatedName())) {
                    FieldMatch fieldMatch = new FieldMatch(oldFieldMapping.getObfuscatedName(), oldFieldMapping.getSignature().getType().map(Object::toString).orElse(""), newFieldMapping.getObfuscatedName(), newFieldMapping.getSignature().getType().map(Object::toString).orElse(""));
                    classMatch.fieldMatches.add(fieldMatch);
                    break;
                }
            }
        }

        for (MethodMapping oldMethodMapping : oldClassMapping.getMethodMappings()) {
            for (MethodMapping newMethodMapping : newClassMapping.getMethodMappings()) {
                if (oldMethodMapping.getDeobfuscatedName().equals(newMethodMapping.getDeobfuscatedName())
                        && oldMethodMapping.getSignature().getDescriptor().toString().equals(newMethodMapping.getSignature().getDescriptor().toString())) {
                    MethodMatch methodMatch = new MethodMatch(oldMethodMapping.getObfuscatedName(), oldMethodMapping.getSignature().getDescriptor().toString(), newMethodMapping.getObfuscatedName(), newMethodMapping.getSignature().getDescriptor().toString());
                    classMatch.methodMatches.add(methodMatch);
                    break;
                }
            }
        }
        for (InnerClassMapping oldInnerClassMapping : oldClassMapping.getInnerClassMappings()) {
            for (InnerClassMapping newInnerClassMapping : newClassMapping.getInnerClassMappings()) {
                matchClassMappings(classMatches, oldInnerClassMapping, newInnerClassMapping);
            }
        }
    }

}
