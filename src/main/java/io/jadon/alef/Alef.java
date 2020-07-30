package io.jadon.alef;

import io.jadon.alef.match.Match;
import io.jadon.alef.match.MatchProvider;
import io.jadon.alef.provider.MappingProvider;
import lombok.SneakyThrows;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.MappingFormats;

import java.nio.file.Paths;

/**
 * Main class
 */
public class Alef {

    @SneakyThrows
    public static void main(String[] args) {
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

}
