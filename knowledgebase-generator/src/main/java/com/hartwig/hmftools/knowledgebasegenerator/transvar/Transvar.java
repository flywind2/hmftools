package com.hartwig.hmftools.knowledgebasegenerator.transvar;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.variant.hotspot.ImmutableVariantHotspotImpl;
import com.hartwig.hmftools.common.variant.hotspot.VariantHotspot;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class Transvar {

    private static final Logger LOGGER = LogManager.getLogger(Transvar.class);

    private static final int TRANSVAR_TIMEOUT_SEC = 10;

    @NotNull
    private final String refFastaPath;
    @NotNull
    private final RefVersion refVersion;

    public Transvar(@NotNull String refFastaPath, @NotNull RefVersion refVersion) {
        this.refFastaPath = refFastaPath;
        this.refVersion = refVersion;
    }

    @NotNull
    public List<VariantHotspot> extractHotspotsFromProteinAnnotation(@NotNull String gene, @NotNull String transcript,
            @NotNull String proteinAnnotation) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder("transvar",
                "panno",
                "--reference",
                refFastaPath,
                "--refversion",
                refVersion.refVersionString(),
                "--noheader",
                "--ensembl",
                "-i",
                gene + ":p." + proteinAnnotation);

        // Below is (somehow) necessary to run in intellij. Otherwise it can not find proper locale.
        processBuilder.environment().put("LC_CTYPE", "UTF-8");

        // Not sure if below is needed to capture all outputs (esp std err).
//        processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT).redirectError(ProcessBuilder.Redirect.INHERIT);

        Process process = processBuilder.start();
        if (!process.waitFor(TRANSVAR_TIMEOUT_SEC, TimeUnit.SECONDS)) {
            throw new RuntimeException(String.format("Timeout. [%s] took more than [%s %s] to execute",
                    command(processBuilder),
                    TRANSVAR_TIMEOUT_SEC,
                    TimeUnit.SECONDS));
        }

        if (process.exitValue() != 0) {
            throw new RuntimeException(String.format("[%s] failed with non-zero exit code [%s]",
                    command(processBuilder),
                    process.exitValue()));
        }

        List<String> stderr = captureStderr(process);
        if (!stderr.isEmpty()) {
            LOGGER.warn("Non-empty stderr when running '{}'!", command(processBuilder));
            for (String errLine : stderr) {
                LOGGER.warn(" {}", errLine);
            }
        }

        List<String> stdout = captureStdout(process);
        List<VariantHotspot> hotspots = Lists.newArrayList();
        for (String outLine : stdout) {
            hotspots.add(transvarToHotpot(outLine));
        }

        return hotspots;
    }

    @NotNull
    private static VariantHotspot transvarToHotpot(@NotNull String transvarLine) {
        LOGGER.info("Converting '{}' to Hotspot", transvarLine);
        // TODO (implement!)
        return ImmutableVariantHotspotImpl.builder().chromosome("7").position(0).ref("C").alt("T").build();
    }

    @NotNull
    private static List<String> captureStdout(@NotNull Process process) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        List<String> stdout = toStringList(reader);

        reader.close();

        return stdout;
    }

    @NotNull
    private static List<String> captureStderr(@NotNull Process process) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

        List<String> stderr = toStringList(reader);

        reader.close();

        return stderr;
    }

    @NotNull
    private static List<String> toStringList(@NotNull BufferedReader reader) throws IOException {
        List<String> output = Lists.newArrayList();
        String line;
        while ((line = reader.readLine()) != null) {
            output.add(line);
        }
        return output;
    }

    @NotNull
    private static String command(@NotNull ProcessBuilder processBuilder) {
        return processBuilder.command().stream().collect(Collectors.joining(" "));
    }
}