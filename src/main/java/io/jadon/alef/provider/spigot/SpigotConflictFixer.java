package io.jadon.alef.provider.spigot;

import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import org.cadixdev.bombe.type.signature.MethodSignature;
import org.cadixdev.lorenz.MappingSet;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Takes a spigot jar and generates mappings to resolve conflicts of methods that Spigot adds
 *
 * @author phase
 */
public class SpigotConflictFixer {

    private static final List<String> ALLOWED_METHODS = Lists.newArrayList("get", "getDouble");

    public static MappingSet generateMappingFixes(File jarFile) throws IOException {
        MappingSet fixes = MappingSet.create();

        JarFile jar = new JarFile(jarFile);
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.endsWith(".class") && !name.contains("/")) {
                InputStream stream = jar.getInputStream(entry);
                byte[] bytes = ByteStreams.toByteArray(stream);
                ClassNode node = parseNode(bytes);
                for (MethodNode method : node.methods) {
                    if (method.name.startsWith("get") && !ALLOWED_METHODS.contains(method.name)) {
                        // assume this is a method added by spigot
                        fixes.getOrCreateClassMapping(node.name)
                                .createMethodMapping(MethodSignature.of(method.name, method.desc), "spigot_" + method.name);
                    }
                }
                stream.close();
            }
        }
        jar.close();

        return fixes;
    }

    public static ClassNode parseNode(byte[] bytes) {
        ClassNode classNode = new ClassNode();
        ClassReader classReader = new ClassReader(bytes);
        classReader.accept(classNode, ClassReader.SKIP_FRAMES | ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
        return classNode;
    }

}
