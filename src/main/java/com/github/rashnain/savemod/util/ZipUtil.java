package com.github.rashnain.savemod.util;

import java.io.*;
import java.nio.file.*;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.zip.*;

public final class ZipUtil {
    private ZipUtil() {}

    public static void createBackup(
            String worldDir,
            String targetFile
    ) throws IOException {
        Path source = Path.of(worldDir);

        try (ZipOutputStream zip =
                     new ZipOutputStream(
                             new BufferedOutputStream(
                                     Files.newOutputStream(
                                             Path.of(targetFile)
                                     )
                             )
                     );
             var stream = Files.walk(source)) {

            stream.filter(Files::isRegularFile)
                    .forEach(path -> {
                        if (path.getFileName().toString().equals("session.lock"))
                            return;

                        String name =
                                source.relativize(path)
                                        .toString()
                                        .replace(File.separatorChar, '/');

                        try {
                            zip.putNextEntry(new ZipEntry(name));
                            Files.copy(path, zip);
                            zip.closeEntry();
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    public static void unzipFile(
            String sourceFile,
            String targetDir
    ) throws IOException {
        Path target = Path.of(targetDir).toAbsolutePath().normalize();

        try (ZipInputStream zip =
                     new ZipInputStream(
                             new BufferedInputStream(
                                     Files.newInputStream(
                                             Path.of(sourceFile)
                                     )
                             )
                     )) {

            ZipEntry entry;

            while ((entry = zip.getNextEntry()) != null) {
                Path destination =
                        target.resolve(entry.getName())
                                .normalize();

                if (!destination.startsWith(target)) {
                    throw new IOException("Archive path traversal detected");
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(
                            zip,
                            destination,
                            StandardCopyOption.REPLACE_EXISTING
                    );
                }

                zip.closeEntry();
            }
        }
    }
}
