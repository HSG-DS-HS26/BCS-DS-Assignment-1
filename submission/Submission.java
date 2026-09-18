import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes the PDF you upload to Canvas, after checking that GitHub has your work.
 *
 * <p>Run it from anywhere inside your copy of the repository:
 *
 * <pre>
 *   java submission/Submission.java
 * </pre>
 *
 * <p>or with the <b>Hand in</b> button in the VS Code status bar, or with
 * {@code ./submit.sh} or {@code .\submit.ps1}. All four run this file.
 *
 * <p>The PDF names your repository and the commit GitHub holds. The TA
 * grades that commit, so the program refuses to write a PDF for work that is not
 * on GitHub: uncommitted changes, commits you have not pushed, or a commit the
 * viva pushed that you have not pulled yet.
 *
 * <p>It needs git and a JDK, and nothing else.
 */
public final class Submission {

    private static final String TEMPLATE_OWNER = "HSG-DS-HS26";

    public static void main(String[] args) throws Exception {
        Path root = Path.of(git(null, "rev-parse", "--show-toplevel").trim());

        String remote = git(root, "remote", "get-url", "origin").trim();
        String repo = repoOf(remote);
        if (repo == null) {
            fail("origin is '" + remote + "', which is not a GitHub repository.",
                 "Hand in from the private copy you made with 'Use this template'.");
        }
        if (repo.startsWith(TEMPLATE_OWNER + "/")) {
            fail("this is the assignment template itself (" + repo + "), not your copy.",
                 "Open your own copy, github.com/<your-username>/<repo>, and run this there.");
        }

        // Its own output is not a change: .gitignore covers it, and this covers a
        // copy made before .gitignore did, where a second press would otherwise
        // refuse because of the PDF the first press wrote.
        boolean dirty = git(root, "status", "--porcelain").lines()
                .anyMatch(l -> !l.endsWith("-submission.pdf"));
        if (dirty) {
            fail("you have changes that are not committed.",
                 "Commit them, push, and run this again. The examiner only sees what is on GitHub.");
        }

        String branch = git(root, "rev-parse", "--abbrev-ref", "HEAD").trim();
        if (branch.equals("HEAD")) {
            fail("you are not on a branch (a detached HEAD).",
                 "Check out the branch you worked on, usually main, and run this again.");
        }

        // Compared with origin rather than with whatever this branch tracks: origin
        // is the repository the TA reads, and a branch can track something
        // else entirely, such as the template it was made from.
        System.out.println("Asking GitHub what it holds...");
        gitOrFail(root, "could not reach GitHub to compare your work with it.", "fetch", "--quiet", "origin");
        String upstream = "origin/" + branch;
        if (gitOrNull(root, "rev-parse", "--verify", "--quiet", upstream) == null) {
            fail("branch '" + branch + "' has never been pushed to GitHub.",
                 "Push it (Source Control -> Publish Branch, or: git push -u origin HEAD) and run this again.");
        }
        int ahead = count(git(root, "rev-list", "--count", upstream + "..HEAD"));
        int behind = count(git(root, "rev-list", "--count", "HEAD.." + upstream));
        if (behind > 0) {
            fail("GitHub has " + behind + " commit(s) you do not have yet. After the viva, that"
                 + " is the viva's own record.",
                 "Pull first (Source Control -> Sync Changes, or: git pull), then run this again.");
        }
        if (ahead > 0) {
            fail("you have " + ahead + " commit(s) that are not on GitHub yet.",
                 "Push them (Source Control -> Sync Changes, or: git push), then run this again.");
        }

        String commit = git(root, "rev-parse", "HEAD").trim();
        String committed = git(root, "log", "-1", "--format=%cI").trim();

        var checks = List.of(
                new Check("Task 4: the viva record in .viva/", hasFiles(root.resolve(".viva"))),
                new Check("Task 5: 5-presentation/slides.pdf",
                          Files.isRegularFile(root.resolve("5-presentation/slides.pdf"))),
                new Check("Task 5: 5-presentation/presentation.mp4",
                          Files.isRegularFile(root.resolve("5-presentation/presentation.mp4"))));

        String generated = ZonedDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'"));

        var lines = new ArrayList<Line>();
        lines.add(new Line(true, 20, "BCS-DS Assignment 1: submission"));
        lines.add(Line.gap());
        lines.add(new Line(true, 13, "Repository"));
        lines.add(new Line(false, 13, repo));
        lines.add(new Line(false, 11, "https://github.com/" + repo));
        lines.add(Line.gap());
        lines.add(new Line(true, 13, "Commit graded"));
        lines.add(new Line(false, 11, commit));
        lines.add(new Line(false, 11, "branch " + branch + ", committed " + committed));
        lines.add(Line.gap());
        lines.add(new Line(true, 13, "Checked when this PDF was written"));
        lines.add(new Line(false, 11, "[x] every change committed"));
        lines.add(new Line(false, 11, "[x] the commit above is on GitHub"));
        for (Check c : checks) lines.add(new Line(false, 11, (c.ok() ? "[x] " : "[ ] ") + c.what()));
        lines.add(Line.gap());
        lines.add(new Line(false, 9, "Written by submission/Submission.java on " + generated + "."));

        Path out = root.resolve(repo.replace('/', '-') + "-submission.pdf");
        Files.write(out, pdf(repo, lines));

        System.out.println();
        System.out.println("Wrote " + root.relativize(out));
        System.out.println("  repository  " + repo);
        System.out.println("  commit      " + commit);
        for (Check c : checks) {
            System.out.println("  " + (c.ok() ? "ok      " : "MISSING ") + c.what());
        }
        if (checks.stream().anyMatch(c -> !c.ok())) {
            System.out.println();
            System.out.println("Something above is missing. If that is not what you meant to hand in,");
            System.out.println("add it, commit, push, and run this again: the PDF lists what was missing.");
        }
        System.out.println();
        System.out.println("Next: upload that PDF to the assignment on Canvas.");
        System.out.println("If you change anything after this, commit, push, and write the PDF again.");
    }

    // ---------------------------------------------------------------- the checks

    private record Check(String what, boolean ok) {}

    /** owner/name out of an https or ssh GitHub remote, or null. */
    static String repoOf(String remote) {
        Matcher m = Pattern.compile("github\\.com[:/]([^/]+)/([^/]+?)(?:\\.git)?/?$").matcher(remote);
        return m.find() ? m.group(1) + "/" + m.group(2) : null;
    }

    private static boolean hasFiles(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return false;
        try (var files = Files.walk(dir)) {
            return files.anyMatch(Files::isRegularFile);
        }
    }

    private static int count(String s) { return Integer.parseInt(s.trim()); }

    private static void fail(String what, String todo) {
        System.err.println();
        System.err.println("No PDF written: " + what);
        System.err.println(todo);
        System.exit(1);
    }

    // ---------------------------------------------------------------------- git

    private static String git(Path dir, String... args) throws IOException, InterruptedException {
        String out = gitOrNull(dir, args);
        if (out == null) {
            fail("'git " + String.join(" ", args) + "' failed.",
                 "Run this from inside your copy of the repository.");
        }
        return out;
    }

    private static void gitOrFail(Path dir, String what, String... args)
            throws IOException, InterruptedException {
        if (gitOrNull(dir, args) == null) fail(what, "Check your network and run this again.");
    }

    /** Standard output, or null when git exits non-zero. */
    private static String gitOrNull(Path dir, String... args) throws IOException, InterruptedException {
        var cmd = new ArrayList<String>();
        cmd.add("git");
        cmd.addAll(List.of(args));
        var pb = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD);
        if (dir != null) pb.directory(dir.toFile());
        Process p;
        try {
            p = pb.start();
        } catch (IOException e) {
            fail("git is not installed or not on the PATH.", "Install git, or work in a Codespace.");
            return null;
        }
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return p.waitFor() == 0 ? out : null;
    }

    // ---------------------------------------------------------------------- pdf

    /** One line of text; a gap is a line with no text. */
    private record Line(boolean bold, int size, String text) {
        static Line gap() { return new Line(false, 8, ""); }
    }

    /**
     * A one-page PDF in the two fonts every reader has, with the repository as the
     * document title as well, so it shows in the reader's title bar.
     */
    static byte[] pdf(String title, List<Line> lines) {
        var content = new StringBuilder("BT\n50 790 Td\n");
        int previous = 0;
        for (Line l : lines) {
            if (previous > 0) content.append("0 -").append(previous + 6).append(" Td\n");
            if (!l.text().isEmpty()) {
                content.append(l.bold() ? "/F2 " : "/F1 ").append(l.size()).append(" Tf\n")
                       .append('(').append(escape(l.text())).append(") Tj\n");
            }
            previous = l.size();
        }
        content.append("ET\n");
        byte[] stream = content.toString().getBytes(StandardCharsets.US_ASCII);

        var objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842]"
                        + " /Resources << /Font << /F1 4 0 R /F2 5 0 R >> >> /Contents 6 0 R >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>",
                "<< /Length " + stream.length + " >>\nstream\n"
                        + new String(stream, StandardCharsets.US_ASCII) + "endstream",
                "<< /Title (" + escape(title) + ") /Creator (submission/Submission.java) >>");

        var out = new ByteArrayOutputStream();
        var offsets = new ArrayList<Integer>();
        write(out, "%PDF-1.4\n");
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            write(out, (i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n");
        }
        int xref = out.size();
        write(out, "xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
        for (int offset : offsets) write(out, String.format("%010d 00000 n \n", offset));
        write(out, "trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R /Info "
                + objects.size() + " 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        return out.toByteArray();
    }

    /** PDF string escaping, and anything outside printable ASCII becomes '?'. */
    private static String escape(String s) {
        var b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '(' || c == ')' || c == '\\') b.append('\\').append(c);
            else if (c < 32 || c > 126) b.append('?');
            else b.append(c);
        }
        return b.toString();
    }

    private static void write(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(StandardCharsets.US_ASCII));
    }
}
