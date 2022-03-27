import java.util.*;
import java.util.stream.*;
import java.nio.file.*;
import java.io.IOException;

public class Day12 {
    private static boolean isSmallCaveName(String name) {
        return name.codePoints().allMatch(Character::isLowerCase);
    }

    private static record Node(String name, Set<String> neighbors) {
        public boolean isSmallCave() {
            return Day12_v2.isSmallCaveName(name());
        }
    }

    private static Deque<Node> appendNode(Deque<Node> path, Node newNode) {
        var newPath = new ArrayDeque<Node>(path);
        newPath.addLast(newNode);
        return newPath;
    }

    private static void part1(Map<String, Node> graph) {
        Deque<Deque<Node>> search_space = new ArrayDeque<Deque<Node>>();
        search_space.addLast(new ArrayDeque<Node>());
        search_space.getLast().addLast(graph.get("start"));
        int nPaths = 0;
        while (!search_space.isEmpty()) {
            var current_path = search_space.removeFirst();
            Node current_node = current_path.getLast();
            for (String caveName : current_node.neighbors()) {
                if (caveName.equals("start")) {
                    continue;
                } else if (caveName.equals("end")) {
                    nPaths++;
                } else if (isSmallCaveName(caveName)) {
                    var cave = graph.get(caveName);
                    if (!current_path.contains(cave)) {
                        search_space.addLast(appendNode(current_path, cave));
                    }
                } else {
                    var cave = graph.get(caveName);
                    search_space.addLast(appendNode(current_path, cave));
                }
            }
        }
        System.out.println("Part 1: " + nPaths);
    }

    private static void part2(Map<String, Node> graph) {
        Deque<Deque<Node>> search_space = new ArrayDeque<Deque<Node>>();
        search_space.addLast(new ArrayDeque<Node>());
        search_space.getLast().addLast(graph.get("start"));
        int nPaths = 0;
        while (!search_space.isEmpty()) {
            var current_path = search_space.removeFirst();
            var current_node = current_path.getLast();
            for (String caveName : current_node.neighbors()) {
                if (caveName.equals("start")) {
                    continue;
                } else if (caveName.equals("end")) {
                    nPaths++;
                } else {
                    Node cave = graph.get(caveName);
                    if (cave.isSmallCave()) {
                        var counts = current_path.stream()
                            .filter(Node::isSmallCave)
                            .collect(Collectors.groupingBy(Node::name,
                                                           Collectors.counting()));
                        if (counts.containsKey(caveName)) {
                            // this cave has already been traversed
                            if (counts.values().stream().noneMatch(c -> c > 1)) {
                                // No small caves have been visted twice.
                                // Add a path that re-visits this one.
                                search_space.addLast(appendNode(current_path, cave));
                            }
                        } else {
                            // This room has not yet been traversed
                            search_space.addLast(appendNode(current_path, cave));
                        }
                    } else {
                        search_space.addLast(appendNode(current_path, cave));
                    }
                }
            }
        }
        System.out.println("Part 2: " + nPaths);
    }

    public static void main(String[] args) {
        try {
            var input = Files.lines(Path.of(args[0]))
                .map(line -> line.split("-")).toList();
            var graph = new HashMap<String, Node>();
            for (var pair : input) {
                graph.computeIfAbsent(pair[0],
                                      k -> new Node(k, new HashSet<String>()))
                    .neighbors()
                    .add(pair[1]);
                graph.computeIfAbsent(pair[1],
                                      k -> new Node(k, new HashSet<String>()))
                    .neighbors()
                    .add(pair[0]);
            }
            part1(graph);
            part2(graph);
        } catch (IOException e) {
            System.err.println(e);
            System.exit(1);
        }
    }
}
