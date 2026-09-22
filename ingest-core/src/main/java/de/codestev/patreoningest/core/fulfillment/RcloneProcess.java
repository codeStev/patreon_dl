package de.codestev.patreoningest.core.fulfillment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

// Shared subprocess-running helper for both rclone adapters. Reads
// stdout/stderr concurrently on virtual threads while waiting for exit -
// reading them sequentially after waitFor() risks a classic deadlock if
// rclone fills a pipe buffer before exiting (real risk here: `lsjson`
// output on a folder with many files can be large).
final class RcloneProcess {

    record Result(int exitCode, String stdout, String stderr) {
    }

    private RcloneProcess() {
    }

    static Result run(List<String> command, Duration timeout) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).start();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> stdoutFuture = executor.submit(() -> readAll(process.getInputStream()));
            Future<String> stderrFuture = executor.submit(() -> readAll(process.getErrorStream()));

            boolean finished = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("Command timed out after " + timeout + ": " + command);
            }

            return new Result(process.exitValue(), stdoutFuture.get(), stderrFuture.get());
        } catch (ExecutionException e) {
            throw new IOException("Failed reading process output for: " + command, e.getCause());
        }
    }

    private static String readAll(InputStream in) throws IOException {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
}
