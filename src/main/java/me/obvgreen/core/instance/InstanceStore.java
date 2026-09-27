package me.obvgreen.core.instance;

import me.obvgreen.util.Json;
import me.obvgreen.util.Paths;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class InstanceStore {
    private static List<Instance> instances;

    public static synchronized List<Instance> all() {
        if (instances != null) return instances;
        instances = new ArrayList<>();
        if (!java.nio.file.Files.exists(Paths.INSTANCES_DIR)) return instances;
        try (Stream<Path> s = java.nio.file.Files.list(Paths.INSTANCES_DIR)) {
            for (Path d : s.collect(Collectors.toList())) {
                Path f = d.resolve("instance.json");
                if (java.nio.file.Files.exists(f)) {
                    Instance i = Json.fromFile(f, Instance.class);
                    if (i != null) instances.add(i);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return instances;
    }

    public static synchronized void save(Instance i) throws IOException {
        i.save();
        if (!all().contains(i)) all().add(i);
    }

    public static synchronized void remove(Instance i) throws IOException {
        i.delete();
        all().remove(i);
    }

    public static synchronized List<String> groups() {
        return all().stream().map(i -> i.group).filter(g -> g != null && !g.isBlank())
                .distinct().sorted().collect(Collectors.toList());
    }
}

