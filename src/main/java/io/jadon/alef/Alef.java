package io.jadon.alef;

import io.jadon.alef.match.Match;
import io.jadon.alef.match.MatchProvider;
import io.jadon.alef.provider.MappingProvider;
import io.jadon.alef.provider.spigot.SpigotConflictFixer;
import lombok.SneakyThrows;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.MappingFormats;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Main class
 */
public class Alef {

    @SneakyThrows
    public static void main(String[] args) {
        File obfSpigotJar = new File("/home/phase/projects/minecraft/spigot/obf-spigot-1.16.1.jar");
        MappingSet mappingSet = SpigotConflictFixer.generateMappingFixes(obfSpigotJar);
        MappingFormats.SRG.write(mappingSet, Paths.get("mappings/1.16.1-obf-spigot-conflict-fix.srg"));
    }

    @SneakyThrows
    public static void main5(String[] args) {
//        MappingSet mappings = MappingProvider.MOJANG.getMappings(MinecraftVersion.v1_16_1).orElse(null);
//        MappingFormats.TSRG.write(mappings, Paths.get("mappings/1.16.1-mojang.srg"));
        MappingSet mappingSet = MappingProvider.YARN.getMappings(MinecraftVersion.v1_16_1).orElse(null);
        MappingFormats.SRG.write(mappingSet, Paths.get("mappings/1.16.1-yarn.srg"));
        MappingSet spigot = MappingProvider.SPIGOT.getMappings(MinecraftVersion.v1_16_1).orElse(null);
        MappingFormats.SRG.write(spigot, Paths.get("mappings/1.16.1-spigot.srg"));
        MappingFormats.SRG.write(spigot.reverse(), Paths.get("mappings/1.16.1-spigot-reversed.srg"));
    }

    @SneakyThrows
    public static void main4(String[] args) {
        MinecraftVersion version = MinecraftVersion.s1_16_2_pre1;
        MappingSet yarn = MappingProvider.YARN.getMappings(version).orElse(null);
        MappingSet mojang = MappingProvider.MOJANG.getMappings(version).orElse(null);
        MappingSet merged = yarn.reverse().merge(mojang);
        MappingFormats.SRG.write(merged, Paths.get("mappings/" + version.toString() + "-yarn-to-mojang.srg"));
    }

    @SneakyThrows
    public static void main3() {
        MinecraftVersion latest = MinecraftVersion.v1_16_1;
        MinecraftVersion snapshot = MinecraftVersion.s1_16_2_pre1;
        MappingProvider provider = MappingProvider.MOJANG;

        createMigrationMappings(latest, snapshot, provider).ifPresent(mappings -> {
            try {
                MappingFormats.TSRG.write(mappings, Paths.get("mappings/" + latest.toString() + "-to-" + snapshot + ".tsrg"));
                MappingFormats.SRG.write(mappings, Paths.get("mappings/" + latest.toString() + "-to-" + snapshot + ".srg"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    @SneakyThrows
    public static void main2() {
        Match v1_12_2__to__v1_14_4 = MatchProvider.chainMatches(MinecraftVersion.v1_12_2, MinecraftVersion.v1_14_4).orElse(null);
        Match v1_14_4__to_v1_15_1 = MatchProvider.chainMatches(MinecraftVersion.v1_14_4, MinecraftVersion.v1_15_1).orElse(null);
        assert v1_12_2__to__v1_14_4 != null && v1_14_4__to_v1_15_1 != null : "failed somewhere?";
        MappingSet mcp_1_12_2 = MappingProvider.MCP.getMappings(MinecraftVersion.v1_12_2).get();
        MappingSet mcp_1_14_4 = MappingProvider.MCP.getMappings(MinecraftVersion.v1_14_4).get();
        MappingSet mcp_1_15_1 = MappingProvider.MCP.getMappings(MinecraftVersion.v1_15_1).get();
        MappingSet twelveToFourteen = v1_12_2__to__v1_14_4.combineMappings(mcp_1_12_2, mcp_1_14_4);
        MappingSet fourteenToFifteen = v1_14_4__to_v1_15_1.combineMappings(mcp_1_14_4, mcp_1_15_1);
        MappingFormats.SRG.write(twelveToFourteen, Paths.get("mappings/1.12.2-to-1.14.4.srg"));
        MappingFormats.SRG.write(fourteenToFifteen, Paths.get("mappings/1.14.4-to-1.15.1.srg"));
    }

    public static Optional<MappingSet> createMigrationMappings(MinecraftVersion from, MinecraftVersion to, MappingProvider provider) {
        return createMigrationMappings(from, to, provider, provider);
    }

    public static Optional<MappingSet> createMigrationMappings(MinecraftVersion from, MinecraftVersion to, MappingProvider fromProvider, MappingProvider toProvider) {
        MappingSet fromMappings = fromProvider.getMappings(from).orElse(null);
        MappingSet toMappings = toProvider.getMappings(to).orElse(null);
        if (fromMappings == null || toMappings == null) return Optional.empty();
        return createMigrationMappings(from, to, fromMappings, toMappings);
    }

    public static Optional<MappingSet> createMigrationMappings(MinecraftVersion from, MinecraftVersion to, MappingSet fromMappings, MappingSet toMappings) {
        Match match = MatchProvider.chainMatches(from, to).orElse(null);
        if (match == null) return Optional.empty();
        return Optional.of(match.combineMappings(fromMappings, toMappings));
    }

}
