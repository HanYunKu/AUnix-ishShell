import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
/**
 * {@code wc [<file>...]}: prints newline, word, and byte counts for each file, and a total line
 * if more than one file is specified. A word is a non-zero-length sequence of characters
 * delimited by white space.
 */
public class Wc extends ShellCommand {
    /**
     * Format specifier for printing output counts. Do not change.
     */
    public static final String formatSpecifier = "%8d %8d %8d %s%n";

    public Wc(String[] args) {
        super(args);
    }

    public static void main(String[] args) {
        ShellCommand.start(Wc.class, args);
    }

    @Override
    protected void runCommand() throws IOException {
        if (cmdArgs.length == 0) {
            long[] counts = countInput(System.in);
            System.out.printf(formatSpecifier, counts[0], counts[1], counts[2], "");
            return;
        }

        long totalLines = 0;
        long totalWords = 0;
        long totalBytes = 0;

        for (String filename : cmdArgs) {
            Path path = Path.of(filename);

            try (InputStream input = getFileInput(path)) {
                long[] counts = countInput(input);

                System.out.printf(formatSpecifier, counts[0], counts[1], counts[2], filename);

                totalLines += counts[0];
                totalWords += counts[1];
                totalBytes += counts[2];
            } catch (IllegalArgumentException e) {
                System.err.println(e.getMessage());

                if (Files.isDirectory(path)) {
                    System.out.printf(formatSpecifier, 0, 0, 0, filename);
                }
            }
        }

        if (cmdArgs.length > 1) {
            System.out.printf(formatSpecifier, totalLines, totalWords, totalBytes, "total");
        }

    }

    private long[] countInput(InputStream input) throws IOException {
        long lines = 0;
        long words = 0;
        long bytes = 0;

        String line;

        while ((line = readLineRaw(input)) != null) {
            lines++; 

            String trimmed = line.trim();

            if (!trimmed.isEmpty()) {
                words += trimmed.split("\\s+").length;
            }

            bytes += line.getBytes(StandardCharsets.UTF_8).length;
        }

        return new long[]{lines, words, bytes};
    }

}
