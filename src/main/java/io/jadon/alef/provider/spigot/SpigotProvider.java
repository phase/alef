package io.jadon.alef.provider.spigot;

import com.google.common.io.Resources;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.jadon.alef.MinecraftVersion;
import io.jadon.alef.provider.MappingProvider;
import lombok.SneakyThrows;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.MappingFormats;
import org.cadixdev.lorenz.merge.*;
import org.cadixdev.lorenz.model.InnerClassMapping;
import org.cadixdev.lorenz.model.TopLevelClassMapping;

import java.io.File;
import java.io.FileReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Optional;

public class SpigotProvider extends MappingProvider {

    public static final String INFO_URL = "https://hub.spigotmc.org/versions/%s.json";
    public static final String STASH_URL = "https://hub.spigotmc.org/stash/projects/SPIGOT/repos/builddata/browse/";

    @SneakyThrows
    protected String getBuildDataCommit(MinecraftVersion version) {
        File infoJson = new File(CACHE_DIR, version.toString() + "/spigot.json");
        infoJson.getParentFile().mkdirs();
        if (!infoJson.exists()) {
            copyToFile(new URL(INFO_URL.replace("%s", version.toString())), infoJson);
        }
        JsonObject info = JsonParser.parseReader(new FileReader(infoJson)).getAsJsonObject();
        return info.getAsJsonObject("refs").get("BuildData").getAsString();
    }

    @SneakyThrows
    protected MappingSet combineMappings(MinecraftVersion version) {
        String buildDataCommit = getBuildDataCommit(version);
        File cacheDir = new File(CACHE_DIR, version.toString() + "/spigot-" + buildDataCommit);
        cacheDir.mkdirs();
        File classCsrg = new File(cacheDir, "classes.csrg");
        File memberCsrg = new File(cacheDir, "members.csrg");
        File packageCsrg = new File(cacheDir, "package.csrg");

        // something is missing!
        if (!(classCsrg.exists() && memberCsrg.exists() && packageCsrg.exists())) {
            // figure out where the files are from the info.json in the build data
            JsonObject info = JsonParser.parseString(Resources.toString(
                    new URL(STASH_URL + "info.json?at=" + buildDataCommit + "&raw"),
                    Charset.defaultCharset())).getAsJsonObject();
            String classMappingLocation = info.get("classMappings").getAsString();
            String memberMappingLocation = info.get("memberMappings").getAsString();
            String packageMappingLocation = info.get("packageMappings").getAsString();

            if (!classCsrg.exists()) {
                copyToFile(new URL(STASH_URL + "mappings/" + classMappingLocation + "?at=" + buildDataCommit + "&raw"), classCsrg);
            }
            if (!memberCsrg.exists()) {
                copyToFile(new URL(STASH_URL + "mappings/" + memberMappingLocation + "?at=" + buildDataCommit + "&raw"), memberCsrg);
            }
            if (!packageCsrg.exists()) {
                copyToFile(new URL(STASH_URL + "mappings/" + packageMappingLocation + "?at=" + buildDataCommit + "&raw"), packageCsrg);
            }
        }
        // this file isn't really csrg so we need to parse it ourselves
        // it will probably only contain "./ net/minecraft/server/" but we parse all the lines anyway
        HashMap<String, String> newPackages = new HashMap<>();
        for (String line : Files.readAllLines(packageCsrg.toPath())) {
            String[] parts = line.split(" ");
            // remove dots
            String from = parts[0].replaceAll("\\.", "");
            String to = parts[1].replaceAll("\\.", "");
            // make default package blank
            if ("/".equals(from)) from = "";
            if ("/".equals(to)) to = "";
            newPackages.put(from, to);
        }

        MappingSet classMappings = MappingFormats.CSRG.read(classCsrg.toPath());
        MappingSet memberMappings = MappingFormats.CSRG.read(memberCsrg.toPath());
        MappingSet merged = classMappings.merge(memberMappings);
        // fix merging
        for (TopLevelClassMapping classMapping : classMappings.getTopLevelClassMappings()) {
            for (InnerClassMapping innerClassMapping : classMapping.getInnerClassMappings()) {
                merged.getClassMapping(classMapping.getObfuscatedName()).ifPresent(c -> {
                    c.createInnerClassMapping(innerClassMapping.getObfuscatedName(), innerClassMapping.getDeobfuscatedName());
                });
            }
        }
        for (TopLevelClassMapping classMapping : merged.getTopLevelClassMappings()) {
            String newPackage = newPackages.getOrDefault(classMapping.getDeobfuscatedPackage(), classMapping.getDeobfuscatedPackage());
            classMapping.setDeobfuscatedName(newPackage + classMapping.getSimpleDeobfuscatedName());
        }
        return merged;
    }

    @Override
    public Optional<MappingSet> getMappings(MinecraftVersion minecraftVersion) {
        if (!minecraftVersion.isRelease()) return Optional.empty();
        return Optional.of(combineMappings(minecraftVersion));
    }
}
