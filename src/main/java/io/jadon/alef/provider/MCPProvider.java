package io.jadon.alef.provider;

import io.jadon.alef.MinecraftVersion;
import lombok.SneakyThrows;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.MappingFormats;
import org.cadixdev.lorenz.model.*;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MCPProvider extends MappingProvider {

    protected MCPProvider() {
    }

    public static final String LEGACY_URL = "http://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp/%s/mcp-%s-csrg.zip";
    public static final String MODERN_URL = "https://raw.githubusercontent.com/MinecraftForge/MCPConfig/master/versions/release/%s/joined.tsrg";
    public static final String SNAPSHOT_URL = "http://export.mcpbot.bspk.rs/mcp_snapshot_nodoc/%s/mcp_snapshot_nodoc-%s.zip";

    public Optional<String> getMcpVersion(MinecraftVersion minecraftVersion) {
        switch (minecraftVersion) {
            case v1_15_1:
                return Optional.of("20200729-1.15.1");
            case v1_14_4:
            case v1_14_3:
                return Optional.of("20200119-1.14.3");
            default:
                return Optional.empty();
        }
    }

    @SneakyThrows
    public void downloadLegacy(File destination, MinecraftVersion version) {
        if (destination.exists()) return;
        destination.getParentFile().mkdirs();
        URL url = new URL(LEGACY_URL.replaceAll("%s", version.toString()));
        ZipInputStream inputStream = new ZipInputStream(url.openStream());
        ZipEntry entry = inputStream.getNextEntry();
        while (entry != null) {
            if (entry.getName().equals("joined.csrg")) {
                copyToFile(inputStream, destination);
                return;
            }
            entry = inputStream.getNextEntry();
        }
        inputStream.close();
    }

    @SneakyThrows
    public void downloadModernSrg(File destination, MinecraftVersion version) {
        if (destination.exists()) return;
        destination.getParentFile().mkdirs();
        URL url = new URL(MODERN_URL.replaceAll("%s", version.toString()));
        InputStream inputStream = url.openStream();
        MappingProvider.copyToFile(inputStream, destination);
        inputStream.close();
    }

    @SneakyThrows
    public void downloadModernSnapshotCsvs(File destinationDir, String mcpVersion) {
        destinationDir.mkdirs();
        URL url = new URL(SNAPSHOT_URL.replaceAll("%s", mcpVersion));
        ZipInputStream inputStream = new ZipInputStream(url.openStream());
        ZipEntry entry = inputStream.getNextEntry();
        while (entry != null) {
            if (entry.getName().equals("fields.csv") || entry.getName().equals("methods.csv")) {
                copyToFile(inputStream, new File(destinationDir, entry.getName()));
            }
            entry = inputStream.getNextEntry();
        }
        inputStream.close();
    }

    @SneakyThrows
    public Map<String, String> parseCsv(File csv) {
        HashMap<String, String> mappings = new HashMap<>();
        List<String> lines = Files.readAllLines(csv.toPath());
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().isEmpty()) continue;
            String[] parts = line.split(",");
            mappings.put(parts[0], parts[1]);
        }
        return mappings;
    }

    @SneakyThrows
    public MappingSet getLegacyMappings(MinecraftVersion minecraftVersion) {
        File csrgFile = new File(CACHE_DIR, minecraftVersion.toString() + "/mcp.csrg");
        downloadLegacy(csrgFile, minecraftVersion);
        return MappingFormats.CSRG.read(csrgFile.toPath());
    }

    public Optional<MappingSet> getModernMappings(MinecraftVersion version) {
        return getMcpVersion(version).map(mcpVersion -> getModernMappings(version, mcpVersion));
    }

    @SneakyThrows
    public MappingSet getModernMappings(MinecraftVersion minecraftVersion, String mcpVersion) {
        File versionDir = new File(CACHE_DIR, minecraftVersion.toString());
        File seargeFile = new File(versionDir, "searge.tsrg");
        File mcpFile = new File(versionDir, "mcp.tsrg");
        downloadModernSnapshotCsvs(versionDir, mcpVersion);
        downloadModernSrg(seargeFile, minecraftVersion);
        Map<String, String> mcpFields = parseCsv(new File(versionDir, "fields.csv"));
        Map<String, String> mcpMethods = parseCsv(new File(versionDir, "methods.csv"));
        MappingSet srgMappings = MappingFormats.TSRG.read(seargeFile.toPath());

        for (TopLevelClassMapping classMapping : srgMappings.getTopLevelClassMappings()) {
            replaceSrgNames(mcpFields, mcpMethods, classMapping);
        }

        MappingFormats.TSRG.write(srgMappings, mcpFile.toPath());
        return srgMappings;
    }

    private void replaceSrgNames(Map<String, String> mcpFields, Map<String, String> mcpMethods, ClassMapping<?, ?> classMapping) {
        for (FieldMapping fieldMapping : classMapping.getFieldMappings()) {
            String mcpField = mcpFields.get(fieldMapping.getDeobfuscatedName());
            if (mcpField != null) {
                fieldMapping.setDeobfuscatedName(mcpField);
            }
        }
        for (MethodMapping methodMapping : classMapping.getMethodMappings()) {
            String mcpMethod = mcpMethods.get(methodMapping.getDeobfuscatedName());
            if (mcpMethod != null) {
                methodMapping.setDeobfuscatedName(mcpMethod);
            }
        }
        for (InnerClassMapping innerClassMapping : classMapping.getInnerClassMappings()) {
            replaceSrgNames(mcpFields, mcpMethods, innerClassMapping);
        }
    }

    @Override
    public Optional<MappingSet> getMappings(MinecraftVersion minecraftVersion) {
        if (!minecraftVersion.isRelease()) return Optional.empty();
        if (minecraftVersion.ordinal() <= MinecraftVersion.v1_12_2.ordinal()) {
            return Optional.of(getLegacyMappings(minecraftVersion));
        }
        return getModernMappings(minecraftVersion);
    }
}
