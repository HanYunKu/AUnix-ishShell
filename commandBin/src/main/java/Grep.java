import java.io.InputStream;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.io.IOException;

/**
 * {@code grep <pattern> [<file>...]}: prints every line, from the given files or from standard
 * input, that matches the {@link java.util.regex.Pattern regular expression} {@code pattern}.
 *
 * <p>With more than one file, each matching line is prefixed with {@code "<filename>:"}.
 */
public class Grep extends ShellCommand {

    public Grep(String[] args) {
        super(args);
    }

    public static void main(String[] args) {
        ShellCommand.start(Grep.class, args);
    }

    @Override
    protected void runCommand() throws IOException{
        if (cmdArgs.length == 0) {
            System.err.println("Usage: grep <pattern> [<file>...]");
            return;
        }

        Pattern pattern = Pattern.compile(cmdArgs[0]);

        if (cmdArgs.length == 1) {
            String line;

            while ((line = readLineRaw(System.in)) != null) {
                if (pattern.matcher(line).find()) {
                    System.out.print(line);
                }
            }

            return;
        }

        boolean multipleFiles = cmdArgs.length > 2;

        for (int i = 1; i < cmdArgs.length; i++) {
            String filename = cmdArgs[i];

            try (InputStream input = getFileInput(Path.of(filename))) {
                String line;

                while ((line = readLineRaw(input)) != null) {
                    if (pattern.matcher(line).find()) {
                        if (multipleFiles) {
                            System.out.print(filename + ":");
                        }

                        System.out.print(line);
                    }
                }
            } catch (IllegalArgumentException e) {
                System.err.println(e.getMessage());
            }
        }
    }


}
