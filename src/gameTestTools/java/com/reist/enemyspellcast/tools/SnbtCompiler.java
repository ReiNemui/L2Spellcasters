package com.reist.enemyspellcast.tools;

import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.TagParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Converts every editable SNBT template below an input root into its runtime NBT counterpart. */
public final class SnbtCompiler {
    private SnbtCompiler() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected <input-root> <output-root>");
        }

        Path inputRoot = Path.of(args[0]);
        Path outputRoot = Path.of(args[1]);
        if (Files.notExists(inputRoot)) {
            return;
        }

        try (var files = Files.walk(inputRoot)) {
            for (Path input : files.filter(path -> path.toString().endsWith(".snbt")).toList()) {
                Path relative = inputRoot.relativize(input);
                String fileName = relative.getFileName().toString().replaceFirst("\\.snbt$", ".nbt");
                Path relativeOutput = relative.resolveSibling(fileName);
                Path output = outputRoot.resolve(relativeOutput);
                Files.createDirectories(output.getParent());
                var tag = TagParser.parseTag(Files.readString(input, StandardCharsets.UTF_8));
                NbtIo.writeCompressed(tag, output);
            }
        }
    }
}
