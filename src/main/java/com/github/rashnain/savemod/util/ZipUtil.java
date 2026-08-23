package com.github.rashnain.savemod.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class ZipUtil {

    private ZipUtil() {
    }

    public static void createBackup(
            String worldDir,
            String targetFile
    ) throws IOException {

        Path source =
                Path.of(worldDir)
                        .toAbsolutePath()
                        .normalize();

        Path finalTarget =
                Path.of(targetFile)
                        .toAbsolutePath()
                        .normalize();

        if (!Files.isDirectory(source)) {
            throw new IOException(
                    "Dossier du monde introuvable : "
                            + source
            );
        }

        Path parent =
                finalTarget.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        /*
         * Création d'un dossier temporaire à côté
         * du dossier de sauvegarde.
         */
        Path tempDir =
                Files.createTempDirectory(
                        parent != null
                                ? parent
                                : source.getParent(),
                        ".autosave_snapshot_"
                );

        Path tempZip =
                finalTarget.resolveSibling(
                        finalTarget.getFileName()
                                + ".tmp"
                );

        try {

            /*
             * 1. Copie du monde vers le snapshot.
             *
             * session.lock est volontairement ignoré.
             */
            copyWorld(
                    source,
                    tempDir
            );

            /*
             * 2. Compression du snapshot.
             */
            createZip(
                    tempDir,
                    tempZip
            );

            /*
             * 3. Le ZIP est terminé.
             * On le déplace ensuite vers son nom définitif.
             */
            Files.move(
                    tempZip,
                    finalTarget,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );

        } catch (IOException e) {

            Files.deleteIfExists(
                    tempZip
            );

            throw e;

        } finally {

            /*
             * Nettoyage du snapshot temporaire.
             */
            deleteDirectory(
                    tempDir
            );
        }
    }

    private static void copyWorld(
            Path source,
            Path target
    ) throws IOException {

        Files.createDirectories(target);

        try (var stream =
                     Files.walk(source)) {

            stream.forEach(path -> {

                try {

                    Path relative =
                            source.relativize(path);

                    /*
                     * On ignore le verrou Minecraft.
                     */
                    if (relative
                            .getFileName()
                            .toString()
                            .equals("session.lock")) {
                        return;
                    }

                    Path destination =
                            target.resolve(
                                    relative
                            );

                    if (Files.isDirectory(path)) {

                        Files.createDirectories(
                                destination
                        );

                    } else {

                        Files.createDirectories(
                                destination.getParent()
                        );

                        Files.copy(
                                path,
                                destination,
                                StandardCopyOption
                                        .REPLACE_EXISTING,
                                StandardCopyOption
                                        .COPY_ATTRIBUTES
                        );
                    }

                } catch (IOException e) {

                    throw new UncheckedIOException(
                            e
                    );
                }
            });

        } catch (UncheckedIOException e) {

            throw e.getCause();
        }
    }

    private static void createZip(
            Path source,
            Path target
    ) throws IOException {

        try (
                ZipOutputStream zip =
                        new ZipOutputStream(
                                new BufferedOutputStream(
                                        Files.newOutputStream(
                                                target
                                        )
                                )
                        );

                var stream =
                        Files.walk(source)
        ) {

            stream
                    .filter(Files::isRegularFile)
                    .forEach(path -> {

                        try {

                            String name =
                                    source.relativize(path)
                                            .toString()
                                            .replace(
                                                    '\\',
                                                    '/'
                                            );

                            ZipEntry entry =
                                    new ZipEntry(name);

                            zip.putNextEntry(
                                    entry
                            );

                            try (
                                    BufferedInputStream input =
                                            new BufferedInputStream(
                                                    Files.newInputStream(
                                                            path
                                                    )
                                            )
                            ) {

                                input.transferTo(
                                        zip
                                );
                            }

                            zip.closeEntry();

                        } catch (IOException e) {

                            throw new UncheckedIOException(
                                    e
                            );
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

        Path archive =
                Path.of(sourceFile)
                        .toAbsolutePath()
                        .normalize();

        Path target =
                Path.of(targetDir)
                        .toAbsolutePath()
                        .normalize();

        Files.createDirectories(
                target
        );

        try (
                ZipInputStream zip =
                        new ZipInputStream(
                                new BufferedInputStream(
                                        Files.newInputStream(
                                                archive
                                        )
                                )
                        )
        ) {

            ZipEntry entry;

            while (
                    (entry = zip.getNextEntry())
                            != null
            ) {

                Path destination =
                        target
                                .resolve(
                                        entry.getName()
                                )
                                .normalize();

                /*
                 * Protection contre les ZIP dangereux.
                 */
                if (!destination.startsWith(
                        target
                )) {

                    throw new IOException(
                            "Archive path traversal detected"
                    );
                }

                if (entry.isDirectory()) {

                    Files.createDirectories(
                            destination
                    );

                } else {

                    Path parent =
                            destination.getParent();

                    if (parent != null) {
                        Files.createDirectories(
                                parent
                        );
                    }

                    Files.copy(
                            zip,
                            destination,
                            StandardCopyOption
                                    .REPLACE_EXISTING
                    );
                }

                zip.closeEntry();
            }
        }
    }

    private static void deleteDirectory(
            Path root
    ) throws IOException {

        if (root == null
                || !Files.exists(root)) {
            return;
        }

        try (var stream =
                     Files.walk(root)) {

            stream
                    .sorted(
                            Comparator.reverseOrder()
                    )
                    .forEach(path -> {

                        try {

                            Files.deleteIfExists(
                                    path
                            );

                        } catch (IOException e) {

                            throw new UncheckedIOException(
                                    e
                            );
                        }
                    });

        } catch (UncheckedIOException e) {

            throw e.getCause();
        }
    }
}
