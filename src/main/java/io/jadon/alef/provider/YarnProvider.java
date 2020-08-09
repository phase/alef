package io.jadon.alef.provider;

import com.google.common.io.ByteStreams;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.jadon.alef.MinecraftVersion;
import lombok.SneakyThrows;
import net.fabricmc.lorenztiny.TinyMappingFormat;
import org.cadixdev.lorenz.MappingSet;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class YarnProvider extends MappingProvider {

    public static final String VERSIONS_URL = "https://meta.fabricmc.net/v1/versions/mappings/";
    public static final String YARN_MAPPINGS_URL = "https://maven.fabricmc.net/net/fabricmc/yarn/%s/yarn-%s.jar";
    public static final String YARN_MERGED_MAPPINGS_URL = "https://maven.fabricmc.net/net/fabricmc/yarn/%s/yarn-%s-mergedv2.jar";
    public static final String INTERMEDIARY_MAPPINGS_URL = "https://maven.fabricmc.net/net/fabricmc/intermediary/%s/intermediary-%s.jar";

    /**
     * Get the latest Yarn build version for a given Minecraft version
     *
     * @param minecraftVersion Minecraft Version
     * @return Yarn build version as a string, usually looks like 1.16.1+build.12
     */
    @SneakyThrows
    public static String getLatestYarnVersion(MinecraftVersion minecraftVersion) {
        URL url = new URL(VERSIONS_URL + minecraftVersion.toString());
        InputStreamReader reader = new InputStreamReader(url.openStream());
        JsonObject versionObject = JsonParser.parseReader(reader).getAsJsonArray().get(0).getAsJsonObject();
        return versionObject.get("version").getAsString();
    }

    /**
     * Get the latest Yarn mappings
     *
     * @param minecraftVersion Minecraft Version
     * @return Obf to Yarn Mapping Set
     */
    public static MappingSet getLatestYarnMappings(MinecraftVersion minecraftVersion) {
        return getYarnMappings(minecraftVersion, getLatestYarnVersion(minecraftVersion));
    }

    /**
     * Get Yarn mappings for a given build version
     *
     * @param minecraftVersion Minecraft Version
     * @param buildVersion     Yarn build version
     * @return Obf to Yarn Mapping Set
     */
    public static MappingSet getYarnMappings(MinecraftVersion minecraftVersion, String buildVersion) {
        return getMappings("yarn", minecraftVersion, buildVersion, "official", "named");
    }

    /**
     * Get mappings from Intermediary to Yarn
     *
     * @param minecraftVersion Minecraft Version
     * @param buildVersion     Yarn build version
     * @return Intermediary to Yarn Mapping Set
     */
    public static MappingSet getIntermediaryToYarnMappings(MinecraftVersion minecraftVersion, String buildVersion) {
        return getMappings("yarn", minecraftVersion, buildVersion, "intermediary", "named");
    }

    /**
     * Get Intermediary mappings
     *
     * @param minecraftVersion Minecraft Version
     * @return Obf to Intermediary Mapping Set
     */
    public static MappingSet getIntermediaryMappings(MinecraftVersion minecraftVersion) {
        return getMappings("intermediary", minecraftVersion, minecraftVersion.toString(), "official", "intermediary");
    }

    @SneakyThrows
    private static MappingSet getMappings(String kind, MinecraftVersion minecraftVersion,
                                          String buildVersion, String from, String to) {
        File cache = new File(CACHE_DIR, "yarn");
        cache.mkdirs();
        File jarFile = new File(cache, "/" + kind + "-" + buildVersion + ".jar");

        if (!jarFile.exists()) {
            InputStream jarStream = null;
            if ("yarn".equals(kind)) {
                jarStream = getValidYarnStream(buildVersion);
            } else if ("intermediary".equals(kind)) {
                jarStream = new URL(INTERMEDIARY_MAPPINGS_URL.replaceAll("%s", buildVersion)).openStream();
            }
            ReadableByteChannel readChannel = Channels.newChannel(jarStream);
            FileOutputStream output = new FileOutputStream(jarFile);
            FileChannel writeChannel = output.getChannel();
            writeChannel.transferFrom(readChannel, 0, Long.MAX_VALUE);
        }

        File tinyFile = new File(cache, "/" + kind + "-" + buildVersion + ".tiny");

        if (!tinyFile.exists()) {
            JarFile jar = new JarFile(jarFile);
            final Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                final JarEntry entry = entries.nextElement();
                if (entry.getName().contains("mappings.tiny")) {
                    byte[] bytes = ByteStreams.toByteArray(jar.getInputStream(entry));
                    Files.write(tinyFile.toPath(), bytes);
                    break;
                }
            }
        }

        return TinyMappingFormat.DETECT.createReader(tinyFile.toPath(), from, to).read();
    }

    private static InputStream getValidYarnStream(String buildVersion) {
        InputStream stream = getUrlStream(YARN_MERGED_MAPPINGS_URL.replaceAll("%s", buildVersion));
        if (stream == null) {
            stream = getUrlStream(YARN_MAPPINGS_URL.replaceAll("%s", buildVersion));
        }
        return stream;
    }

    private static @Nullable InputStream getUrlStream(String url) {
        try {
            return new URL(url).openStream();
        } catch (Exception ignored) { }
        return null;
    }

    @Override
    public Optional<MappingSet> getMappings(MinecraftVersion minecraftVersion) {
        if (minecraftVersion.ordinal() < MinecraftVersion.v1_13_1.ordinal()) return Optional.empty();
        return Optional.of(getLatestYarnMappings(minecraftVersion));
    }
}
