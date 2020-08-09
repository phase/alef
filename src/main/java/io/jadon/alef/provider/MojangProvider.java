package io.jadon.alef.provider;

import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.jadon.alef.MinecraftVersion;
import lombok.SneakyThrows;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.proguard.ProGuardReader;

import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Optional;

public class MojangProvider extends MappingProvider {

    public static final String MANIFEST = "https://launchermeta.mojang.com/mc/game/version_manifest.json";

    protected MojangProvider() {
    }

    /**
     * Download the version manifest json and parse it
     *
     * @return version manifest
     */
    @SneakyThrows
    public static JsonObject getVersionManifestJson() {
        String manifest = Resources.toString(new URL(MANIFEST), Charset.defaultCharset());
        return JsonParser.parseString(manifest).getAsJsonObject();
    }

    /**
     * Download a specific version's json and parse it
     *
     * @param minecraftVersion version to download
     * @return json if it exists
     */
    @SneakyThrows
    public static Optional<String> getVersionJson(MinecraftVersion minecraftVersion) {
        JsonObject versionManifestJson = getVersionManifestJson();
        // find the version we want
        for (JsonElement version : versionManifestJson.getAsJsonArray("versions")) {
            JsonObject versionObj = version.getAsJsonObject();
            if (versionObj.get("id").getAsString().equals(minecraftVersion.toString())) {
                // grab the url and read it
                URL url = new URL(versionObj.get("url").getAsString());
                String versionJson = Resources.toString(url, Charset.defaultCharset());
                return Optional.of(versionJson);
            }
        }
        return Optional.empty();
    }

    @SneakyThrows
    public static Optional<JsonObject> downloadVersionJson(MinecraftVersion version, File file) {
        if (file.exists()) return Optional.of(JsonParser.parseReader(new FileReader(file)).getAsJsonObject());
        String versionJson = getVersionJson(version).orElse(null);
        if (versionJson == null) return Optional.empty();
        Files.write(file.toPath(), versionJson.getBytes());
        return Optional.of(JsonParser.parseString(versionJson).getAsJsonObject());
    }

    @Override
    @SneakyThrows
    public Optional<MappingSet> getMappings(MinecraftVersion minecraftVersion) {
        if (minecraftVersion.ordinal() < MinecraftVersion.v1_14_4.ordinal() && !minecraftVersion.name().contains("combat")) return Optional.empty();

        MappingSet complete = MappingSet.create();
        for (String side : Lists.newArrayList("server", "client")) {
            File proguardFile = new File(CACHE_DIR, minecraftVersion.toString() + "/mojang-" + side + ".proguard");
            if (!proguardFile.exists()) {
                proguardFile.getParentFile().mkdirs();
                JsonObject versionJson = downloadVersionJson(minecraftVersion, new File(CACHE_DIR, minecraftVersion.toString() + "/" + minecraftVersion.toString() + ".json")).orElse(null);
                if (versionJson == null) {
                    System.out.println("version json is null for "+  minecraftVersion.name() + " " + minecraftVersion.toString());
                    return Optional.empty();}
                JsonObject downloads = versionJson.getAsJsonObject("downloads");
                // todo: server?
                String mappingUrl = downloads.getAsJsonObject(side + "_mappings").get("url").getAsString();
                InputStream inputStream = new URL(mappingUrl).openStream();
                copyToFile(inputStream, proguardFile);
                inputStream.close();
            }
            MappingSet sideMappings = new ProGuardReader(new FileReader(proguardFile)).read().reverse();
            complete = complete.merge(sideMappings);
        }

        return Optional.of(complete);
    }
}
