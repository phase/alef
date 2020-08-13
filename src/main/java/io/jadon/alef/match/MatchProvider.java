package io.jadon.alef.match;

import io.jadon.alef.MinecraftVersion;
import lombok.AllArgsConstructor;

import java.io.File;
import java.util.Optional;

@AllArgsConstructor
public enum MatchProvider {
    ALEF("mappings/matches"),
    LEGACY("mappings/legacy-intermediary/matches/"),
    MODERN("mappings/modern-intermediary/matches/");

    private final String directory;

    public Optional<File> findMatchFile(MinecraftVersion from, MinecraftVersion to) {
        File match = new File(this.directory, from.toString() + "-" + to.toString() + ".match");
        if (match.exists() && match.isFile()) {
            return Optional.of(match);
        }
        match = new File(this.directory, from.toString() + "-" + to.toString() + ".csrg");
        if (match.exists() && match.isFile()) {
            return Optional.of(match);
        }
        return Optional.empty();
    }

    public static Optional<File> getMatchFile(MinecraftVersion from, MinecraftVersion to) {
        Optional<File> match = ALEF.findMatchFile(from, to);
        if (match.isPresent()) return match;
        match = LEGACY.findMatchFile(from, to);
        if (match.isPresent()) return match;
        return MODERN.findMatchFile(from, to);
    }

    public static Optional<Match> getMatch(MinecraftVersion from, MinecraftVersion to) {
        return getMatchFile(from, to).map(Match::parse);
    }

    public static Optional<Match> chainMatches(MinecraftVersion from, MinecraftVersion to) {
        int fromOrdinal = from.ordinal();
        int toOrdinal = to.ordinal();
        assert fromOrdinal < toOrdinal : from.toString() + " is after " + to.toString();

        Match chainedMatch = null;
        for (int i = fromOrdinal; i < toOrdinal; i++) {
            MinecraftVersion fFrom = MinecraftVersion.values()[i];
            MinecraftVersion fTo = MinecraftVersion.values()[i + 1];
            System.out.println("Using match " + fFrom.toString() + " -> " + fTo.toString());

            Match match = getMatch(fFrom, fTo)
                    .orElseThrow(() -> new IllegalStateException("Can't find match from " + fFrom.toString()
                            + " to " + fTo.toString()));
            if (chainedMatch == null) {
                chainedMatch = match;
            } else {
                chainedMatch = chainedMatch.chain(match);
                System.out.println("Found " + chainedMatch.getClassMatches().size() + " class matches");
            }
        }
        return Optional.ofNullable(chainedMatch);
    }

    public static Optional<Match> chainMatches(MinecraftVersion... versions) {
        Match chainedMatch = null;
        for (int i = 0; i < versions.length - 1; i++) {
            MinecraftVersion from = versions[i];
            MinecraftVersion to = versions[i + 1];
            Match match = getMatch(from, to)
                    .orElseThrow(() -> new IllegalStateException("Can't find match from " + from.toString()
                            + " to " + to.toString()));
            if (chainedMatch == null) {
                chainedMatch = match;
            } else {
                chainedMatch = chainedMatch.chain(match);
                System.out.println("Found " + chainedMatch.getClassMatches().size() + " class matches");
            }
        }
        return Optional.ofNullable(chainedMatch);
    }

}
