package com.github.rahmnathan.directory.monitor;

import com.sun.nio.file.SensitivityWatchEventModifier;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

public class DirectoryMonitor {
    private static final Logger logger = Logger.getLogger(DirectoryMonitor.class.getName());
    private final ExecutorService executor;
    private final Collection<DirectoryMonitorObserver> observers;
    private final Map<WatchKey, Path> keys = new HashMap<>();
    private WatchService watchService;
    private Consumer<Path> register;

    public DirectoryMonitor(Collection<DirectoryMonitorObserver> observers) {
        this.observers = observers;
        this.executor = Executors.newSingleThreadExecutor();
        startRecursiveWatcher();
    }

    private void notifyObservers(WatchEvent event, Path absolutePath) {
        observers.forEach(observer -> CompletableFuture.runAsync(() -> observer.directoryModified(event, absolutePath)));
    }

    public void registerDirectory(String pathToMonitor) {
        register.accept(Paths.get(pathToMonitor));
    }

    private void startRecursiveWatcher() {
        logger.info("Starting Recursive Watcher");

        try {
            this.watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            logger.severe(e.toString());
            return;
        }

        register = p -> {
            try {
                Files.walkFileTree(p, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        logger.info("registering " + dir + " in watcher service");
                        WatchKey watchKey = dir.register(watchService, new WatchEvent.Kind[]{ENTRY_CREATE, ENTRY_DELETE}, SensitivityWatchEventModifier.HIGH);
                        keys.put(watchKey, dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                logger.severe(e.toString());
            }
        };

        executor.submit(() -> {
            while (true) {
                final WatchKey key;
                try {
                    key = watchService.take();
                } catch (InterruptedException e) {
                    logger.severe(e.toString());
                    continue;
                }

                final Path dir = keys.get(key);

                key.pollEvents().stream()
                        .map(e -> ((WatchEvent<Path>) e))
                        .forEach(event -> {
                            if (!event.kind().equals(OVERFLOW)) {
                                final Path absPath = dir.resolve(event.context());
                                notifyObservers(event, absPath);
                                if (absPath.toFile().isDirectory()) {
                                    register.accept(absPath);
                                }
                            }
                        });
                key.reset();
            }
        });
    }
}