package io.jadon.alef.provider;

import io.jadon.alef.MinecraftVersion;
import lombok.SneakyThrows;
import org.cadixdev.lorenz.MappingSet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.Optional;

public abstract class MappingProvider {

    public static MCPProvider MCP = new MCPProvider();
    protected static File CACHE_DIR = new File("cache/");

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
