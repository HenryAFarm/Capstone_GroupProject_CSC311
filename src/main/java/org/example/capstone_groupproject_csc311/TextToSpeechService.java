package org.example.capstone_groupproject_csc311;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

public class TextToSpeechService {
    public void speak(String text) {
        if (text == null || text.isBlank()) {
            return;
        }

        String commandText = text.trim();
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(buildCommand(osName, commandText));
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            process.getInputStream().transferTo(OutputStream.nullOutputStream());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("The speech program exited with status " + exitCode + ".");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Text-to-speech was interrupted.", e);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Free system text-to-speech failed. Check that PowerShell/SAPI, say, or espeak is available.",
                    e);
        }
    }

    static String[] buildCommand(String osName, String text) {
        String normalizedOsName = osName.toLowerCase(Locale.ROOT);
        if (normalizedOsName.contains("mac") || normalizedOsName.contains("darwin")) {
            return new String[]{"say", text};
        }

        if (normalizedOsName.contains("win")) {
            String encodedText = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
            return new String[]{
                "powershell",
                "-NoProfile",
                "-Command",
                "$s = New-Object -ComObject SAPI.SpVoice; "
                        + "$s.Volume = 100; $s.Rate = 0; "
                        + "$text = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('"
                        + encodedText + "')); [void]$s.Speak($text)"
            };
        }

        return new String[]{"espeak", text};
    }
}
