package io.jadon.alef.provider;

import io.jadon.alef.MinecraftVersion;
import io.jadon.alef.provider.spigot.SpigotProvider;
import lombok.SneakyThrows;
import org.cadixdev.lorenz.MappingSet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.Optional;

public abstract class MappingProvider {

    public static MCPProvider MCP = new MCPProvider();
    public static MojangProvider MOJANG = new MojangProvider();
    public static YarnProvider YARN = new YarnProvider();
    public static SpigotProvider SPIGOT = new SpigotProvider();
    protected static File CACHE_DIR = new File("cache/");

    @SneakyThrows
    protected static void copyToFile(URL url, File file) {
        InputStream inputStream = url.openStream();
        copyToFile(inputStream, file);
        inputStream.close();
    }

    @SneakyThrows
    protected static void copyToFile(InputStream inputStream, File file) {
        ReadableByteChannel readChannel = Channels.newChannel(inputStream);
        FileOutputStream output = new FileOutputStream(file);
        FileChannel writeChannel = output.getChannel();
        writeChannel.transferFrom(readChannel, 0, Long.MAX_VALUE);
        writeChannel.force(false);
        writeChannel.close();
        output.close();
    }

    public abstract Optional<MappingSet> getMappings(MinecraftVersion minecraftVersion);

}
