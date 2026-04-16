import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;

public class MiniGit {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("No command provided");
            return;
        }

        String command = args[0];

        if (command.equals("init")) {
            init();
        } else if (command.equals("add")) {
            if (args.length < 2) {
                System.out.println("Specify file to add");
                return;
            }
            add(args[1]);
        } else if (command.equals("commit")) {
            if (args.length < 2) {
                System.out.println("Provide commit message");
                return;
            }
            commit(args[1]);
        }
        else if (command.equals("log")) {
        log();
        }
        else if (command.equals("checkout")) {
            if (args.length < 2) {
                System.out.println("Provide commit id");
                return;
            }
            checkout(args[1]);
        }
        else {
            System.out.println("Unknown command");
        }
    }

    public static void init() {
        try {
            File repo = new File(".minigit");
            if (!repo.exists()) {
                repo.mkdir();

                new File(".minigit/objects").mkdir();
                new File(".minigit/commits").mkdir();

                new File(".minigit/index").createNewFile();
                new File(".minigit/HEAD").createNewFile();

                System.out.println("Initialized empty MiniGit repository");
            } else {
                System.out.println("Repository already exists");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void add(String fileName) {
        try {
            Path path = Paths.get(fileName);

            if (!Files.exists(path)) {
                System.out.println("File does not exist");
                return;
            }

            String content = Files.readString(path);
            String hash = generateHash(content);

            Path objectPath = Paths.get(".minigit/objects/" + hash);
            if (!Files.exists(objectPath)) {
                Files.writeString(objectPath, content);
            }

            String entry = fileName + " " + hash + "\n";
            Files.writeString(Paths.get(".minigit/index"), entry, StandardOpenOption.APPEND);

            System.out.println("Added " + fileName);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void commit(String message) {
        try {
            Path indexPath = Paths.get(".minigit/index");

            List<String> lines = Files.readAllLines(indexPath);

            if (lines.isEmpty()) {
                System.out.println("Nothing to commit");
                return;
            }

            // Read HEAD (parent commit)
            Path headPath = Paths.get(".minigit/HEAD");
            String parent = "";
            if (Files.exists(headPath)) {
                parent = Files.readString(headPath);
            }

            // Build commit content
            StringBuilder commitData = new StringBuilder();
            commitData.append("parent: ").append(parent).append("\n");
            commitData.append("message: ").append(message).append("\n");
            commitData.append("time: ").append(LocalDateTime.now()).append("\n");
            commitData.append("files:\n");

            for (String line : lines) {
                commitData.append(line).append("\n");
            }

            // Generate commit hash
            String commitHash = generateHash(commitData.toString());

            // Save commit
            Path commitPath = Paths.get(".minigit/commits/" + commitHash);
            Files.writeString(commitPath, commitData.toString());

            // Update HEAD
            Files.writeString(headPath, commitHash);

            // Clear index
            Files.writeString(indexPath, "");

            System.out.println("Committed: " + commitHash);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static void log() {
    try {
        Path headPath = Paths.get(".minigit/HEAD");

        if (!Files.exists(headPath)) {
            System.out.println("No repository found");
            return;
        }

        String current = Files.readString(headPath).trim();

        if (current.isEmpty()) {
            System.out.println("No commits yet");
            return;
        }

        while (current != null && !current.isEmpty()) {

            Path commitPath = Paths.get(".minigit/commits/" + current);

            if (!Files.exists(commitPath)) {
                break;
            }

            List<String> lines = Files.readAllLines(commitPath);

            System.out.println("Commit: " + current);

            String parent = "";

            for (String line : lines) {
                if (line.startsWith("message:")) {
                    System.out.println(line);
                }
                if (line.startsWith("time:")) {
                    System.out.println(line);
                }
                if (line.startsWith("parent:")) {
                    parent = line.substring(8).trim();
                }
            }

            System.out.println("------------------------");

            current = parent;
        }

    } catch (Exception e) {
        e.printStackTrace();
    }
}

    public static String generateHash(String content) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] hashBytes = md.digest(content.getBytes());

        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    public static void checkout(String commitId) {
    try {
        Path commitPath = Paths.get(".minigit/commits/" + commitId);

        if (!Files.exists(commitPath)) {
            System.out.println("Commit not found");
            return;
        }

        List<String> lines = Files.readAllLines(commitPath);

        boolean filesSection = false;

        for (String line : lines) {

            if (line.equals("files:")) {
                filesSection = true;
                continue;
            }

            if (filesSection) {
                if (line.trim().isEmpty()) continue;

                String[] parts = line.split(" ");

                if (parts.length < 2) continue;

                String fileName = parts[0];
                String hash = parts[1];

                Path objectPath = Paths.get(".minigit/objects/" + hash);

                if (!Files.exists(objectPath)) {
                    System.out.println("Missing object for " + fileName);
                    continue;
                }

                String content = Files.readString(objectPath);

                // overwrite file in working directory
                Files.writeString(Paths.get(fileName), content);

                System.out.println("Restored " + fileName);
            }
        }

        // update HEAD
        Files.writeString(Paths.get(".minigit/HEAD"), commitId);

        System.out.println("Checkout complete: " + commitId);

    } catch (Exception e) {
        e.printStackTrace();
    }
    }
}