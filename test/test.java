import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

public class test {

    public static void main(String[] args) {
        GitService service = new GitService();

        if (args.length == 0) {
            System.out.println("No command provided");
            return;
        }

        String command = args[0];

        switch (command) {
            case "init":
                service.init();
                break;

            case "add":
                if (args.length < 2) {
                    System.out.println("Specify file to add");
                    return;
                }
                service.add(args[1]);
                break;

            case "commit":
                if (args.length < 2) {
                    System.out.println("Provide commit message");
                    return;
                }
                service.commit(args[1]);
                break;

            case "log":
                service.log();
                break;

            case "checkout":
                if (args.length < 2) {
                    System.out.println("Provide commit id");
                    return;
                }
                service.checkout(args[1]);
                break;

            case "rm":
                if (args.length < 2) {
                    System.out.println("Specify file to remove");
                    return;
                }
                service.remove(args[1]);
                break;

            case "status":
                service.status();
                break;

            default:
                System.out.println("Unknown command. Available: init, add, commit, log, checkout, rm, status");
        }
    }

    // ---------------- Git Service ----------------
    static class GitService {
        private Repository repo = new Repository();

        public void init() {
            repo.init();
        }

        public void add(String fileName) {
            try {
                Path filePath = Paths.get(fileName);

                if (!Files.exists(filePath)) {
                    System.out.println("File does not exist");
                    return;
                }

                Blob blob = Blob.fromFile(filePath);
                repo.getObjectStore().saveBlob(blob);

                Index index = repo.loadIndex();
                index.add(fileName, blob.getHash());
                repo.saveIndex(index);

                System.out.println("Added " + fileName);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void commit(String message) {
            try {
                Index index = repo.loadIndex();

                if (index.isEmpty()) {
                    System.out.println("Nothing to commit");
                    return;
                }

                String parent = repo.getHead();

                Commit commit = new Commit(
                        parent,
                        message,
                        LocalDateTime.now(),
                        new HashMap<>(index.getEntries())
                );

                commit.generateHash();

                repo.getCommitStore().saveCommit(commit);
                repo.updateHead(commit.getHash());

                index.clear();
                repo.saveIndex(index);

                System.out.println("Committed: " + commit.getHash());

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void log() {
            try {
                String current = repo.getHead();

                if (current == null || current.isEmpty()) {
                    System.out.println("No commits yet");
                    return;
                }

                CommitStore commitStore = repo.getCommitStore();

                while (current != null && !current.isEmpty()) {

                    if (!commitStore.exists(current)) break;

                    Commit commit = commitStore.loadCommit(current);

                    System.out.println("Commit: " + current);
                    System.out.println("message: " + commit.getMessage());
                    System.out.println("time: " + commit.getTimestamp());
                    System.out.println("------------------------");

                    current = commit.getParent();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void checkout(String commitId) {
            try {
                CommitStore commitStore = repo.getCommitStore();

                if (!commitStore.exists(commitId)) {
                    System.out.println("Commit not found");
                    return;
                }

                Commit commit = commitStore.loadCommit(commitId);
                ObjectStore objectStore = repo.getObjectStore();

                for (Map.Entry<String, String> entry : commit.getFiles().entrySet()) {

                    String fileName = entry.getKey();
                    String hash = entry.getValue();

                    Blob blob = objectStore.loadBlob(hash);

                    if (blob == null) {
                        System.out.println("Missing object for " + fileName);
                        continue;
                    }

                    Files.writeString(Paths.get(fileName), blob.getContent());
                    System.out.println("Restored " + fileName);
                }

                repo.updateHead(commitId);
                System.out.println("Checkout complete: " + commitId);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /**
         * Removes a file from the staging index (like `git rm --cached`).
         * The actual file on disk is NOT deleted.
         */
        public void remove(String fileName) {
            try {
                Index index = repo.loadIndex();

                if (!index.contains(fileName)) {
                    System.out.println("'" + fileName + "' is not staged – nothing to remove");
                    return;
                }

                index.remove(fileName);
                repo.saveIndex(index);

                System.out.println("Removed '" + fileName + "' from staging area");

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /**
         * Shows which files are currently staged for the next commit.
         */
        public void status() {
            try {
                Index index = repo.loadIndex();
                String head = repo.getHead();

                System.out.println("=== MiniGit Status ===");
                System.out.println("HEAD: " + (head.isEmpty() ? "(no commits yet)" : head));

                if (index.isEmpty()) {
                    System.out.println("Nothing staged for commit.");
                } else {
                    System.out.println("Staged files:");
                    for (Map.Entry<String, String> entry : index.getEntries().entrySet()) {
                        System.out.println("  + " + entry.getKey() + " [" + entry.getValue().substring(0, 7) + "...]");
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // ---------------- Repository ----------------
    static class Repository {
        private final Path root = Paths.get(".minigit");
        private final Path objects = root.resolve("objects");
        private final Path commits = root.resolve("commits");
        private final Path index = root.resolve("index");
        private final Path head = root.resolve("HEAD");

        public void init() {
            try {
                if (!Files.exists(root)) {
                    Files.createDirectory(root);
                    Files.createDirectory(objects);
                    Files.createDirectory(commits);
                    Files.createFile(index);
                    Files.createFile(head);

                    System.out.println("Initialized empty MiniGit repository");
                } else {
                    System.out.println("Repository already exists");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public Index loadIndex() throws IOException {
            return Index.load(index);
        }

        public void saveIndex(Index idx) throws IOException {
            idx.save(index);
        }

        public String getHead() throws IOException {
            if (!Files.exists(head)) return "";
            return Files.readString(head).trim();
        }

        public void updateHead(String commitHash) throws IOException {
            Files.writeString(head, commitHash);
        }

        public ObjectStore getObjectStore() {
            return new ObjectStore(objects);
        }

        public CommitStore getCommitStore() {
            return new CommitStore(commits);
        }
    }

    // ---------------- Index ----------------
    static class Index {
        private Map<String, String> entries = new HashMap<>();

        public void add(String fileName, String hash) {
            entries.put(fileName, hash);
        }

        public Map<String, String> getEntries() {
            return entries;
        }

        public boolean isEmpty() {
            return entries.isEmpty();
        }

        public boolean contains(String fileName) {
            return entries.containsKey(fileName);
        }

        public void remove(String fileName) {
            entries.remove(fileName);
        }

        public void clear() {
            entries.clear();
        }

        public static Index load(Path path) throws IOException {
            Index index = new Index();

            if (!Files.exists(path)) return index;

            List<String> lines = Files.readAllLines(path);

            for (String line : lines) {
                if (line.trim().isEmpty()) continue;

                String[] parts = line.split(" ");
                if (parts.length >= 2) {
                    index.entries.put(parts[0], parts[1]);
                }
            }

            return index;
        }

        public void save(Path path) throws IOException {
            StringBuilder sb = new StringBuilder();

            for (Map.Entry<String, String> entry : entries.entrySet()) {
                sb.append(entry.getKey()).append(" ")
                        .append(entry.getValue()).append("\n");
            }

            Files.writeString(path, sb.toString());
        }
    }

    // ---------------- Blob ----------------
    static class Blob {
        private String hash;
        private String content;

        private Blob(String content) throws Exception {
            this.content = content;
            this.hash = generateHash(content);
        }

        public static Blob fromFile(Path path) throws Exception {
            String content = Files.readString(path);
            return new Blob(content);
        }

        private String generateHash(String content) throws Exception {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] bytes = md.digest(content.getBytes());

            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }

        public String getHash() { return hash; }
        public String getContent() { return content; }
    }

    // ---------------- ObjectStore ----------------
    static class ObjectStore {
        private Path objectsPath;

        public ObjectStore(Path path) {
            this.objectsPath = path;
        }

        public void saveBlob(Blob blob) throws Exception {
            Path objPath = objectsPath.resolve(blob.getHash());
            if (!Files.exists(objPath)) {
                Files.writeString(objPath, blob.getContent());
            }
        }

        public Blob loadBlob(String hash) throws Exception {
            Path path = objectsPath.resolve(hash);
            if (!Files.exists(path)) return null;

            String content = Files.readString(path);
            return new Blob(content);
        }
    }

    // ---------------- Commit ----------------
    static class Commit {
        private String hash;
        private String parent;
        private String message;
        private LocalDateTime timestamp;
        private Map<String, String> files;

        public Commit(String parent, String message, LocalDateTime time, Map<String, String> files) {
            this.parent = parent;
            this.message = message;
            this.timestamp = time;
            this.files = files;
        }

        public void generateHash() throws Exception {
            String data = parent + message + timestamp.toString() + files.toString();
            this.hash = generateHashStatic(data);
        }

        public String serialize() {
            StringBuilder sb = new StringBuilder();
            sb.append("parent: ").append(parent).append("\n");
            sb.append("message: ").append(message).append("\n");
            sb.append("time: ").append(timestamp).append("\n");
            sb.append("files:\n");

            for (Map.Entry<String, String> entry : files.entrySet()) {
                sb.append(entry.getKey()).append(" ")
                        .append(entry.getValue()).append("\n");
            }
            return sb.toString();
        }

        public static Commit deserialize(List<String> lines) {
            String parent = "", message = "";
            LocalDateTime time = null;
            Map<String, String> files = new HashMap<>();

            boolean fileSection = false;

            for (String line : lines) {
                if (line.startsWith("parent:"))
                    parent = line.substring(8).trim();
                else if (line.startsWith("message:"))
                    message = line.substring(8).trim();
                else if (line.startsWith("time:"))
                    time = LocalDateTime.parse(line.substring(5).trim());
                else if (line.equals("files:"))
                    fileSection = true;
                else if (fileSection && !line.trim().isEmpty()) {
                    String[] parts = line.split(" ");
                    files.put(parts[0], parts[1]);
                }
            }

            return new Commit(parent, message, time, files);
        }

        public String getHash() { return hash; }
        public String getParent() { return parent; }
        public String getMessage() { return message; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public Map<String, String> getFiles() { return files; }
    }

    // ---------------- CommitStore ----------------
    static class CommitStore {
        private Path commitsPath;

        public CommitStore(Path path) {
            this.commitsPath = path;
        }

        public void saveCommit(Commit commit) throws Exception {
            Path path = commitsPath.resolve(commit.getHash());
            Files.writeString(path, commit.serialize());
        }

        public Commit loadCommit(String hash) throws Exception {
            Path path = commitsPath.resolve(hash);
            if (!Files.exists(path)) return null;

            List<String> lines = Files.readAllLines(path);
            Commit commit = Commit.deserialize(lines);
            commit.generateHash(); // regenerate

            return commit;
        }

        public boolean exists(String hash) {
            return Files.exists(commitsPath.resolve(hash));
        }
    }

    // ---------------- Utils ----------------
    static String generateHashStatic(String content) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] bytes = md.digest(content.getBytes());

        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}