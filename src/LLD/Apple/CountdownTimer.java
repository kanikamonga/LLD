import java.io.*;

/**
 * CountdownTimer - Simple Countdown Timer Implementation
 *
 * Problem: Given a file containing a time value in H:M:S format,
 * implement a countdown timer from that value to 00:00:00.
 * The file may contain:
 * - Only S (Seconds): "45"
 * - M:S format: "2:30"
 * - H:M:S format: "1:30:45"
 *
 * Time Complexity: O(n) - iterates from input to 0 (n = total seconds)
 * Space Complexity: O(1)
 */
public class CountdownTimer {
    private long totalSeconds;

    /**
     * Constructor that reads time from file
     * @param filePath Path to file containing time value
     * @throws IOException If file cannot be read
     * @throws IllegalArgumentException If format is invalid
     */
    public CountdownTimer(String filePath) throws IOException {
        this.totalSeconds = parseTimeFromFile(filePath);
    }

    /**
     * Parse time from file and convert to seconds
     * Supports formats:
     * - "5" → 5 seconds
     * - "2:30" → 2 minutes 30 seconds
     * - "1:30:45" → 1 hour, 2 minutes, 30 seconds
     *
     * @param filePath Path to the file
     * @return Total time in seconds
     * @throws IOException If file cannot be read
     * @throws IllegalArgumentException If format is invalid
     */
    private long parseTimeFromFile(String filePath) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(filePath));
        String line = reader.readLine();
        reader.close();

        if (line == null || line.trim().isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        return parseTimeString(line.trim());
    }

    /**
     * Parse time string in various formats
     * @param timeStr Time string to parse
     * @return Total seconds
     */
    public static long parseTimeString(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            throw new IllegalArgumentException("Time string cannot be null or empty");
        }

        String[] parts = timeStr.split(":");
        
        if (parts.length > 3) {
            throw new IllegalArgumentException("Invalid time format. Use H:M:S or variations");
        }

        long hours = 0, minutes = 0, seconds = 0;

        try {
            switch (parts.length) {
                case 1:
                    // Single value - interpret as seconds
                    seconds = Long.parseLong(parts[0].trim());
                    break;

                case 2:
                    // Two values - interpret as minutes:seconds
                    minutes = Long.parseLong(parts[0].trim());
                    seconds = Long.parseLong(parts[1].trim());
                    break;

                case 3:
                    // Three values - H:M:S format
                    hours = Long.parseLong(parts[0].trim());
                    minutes = Long.parseLong(parts[1].trim());
                    seconds = Long.parseLong(parts[2].trim());
                    break;
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid time format. Values must be numbers: " + e.getMessage());
        }

        // Validate ranges (only for M:S and H:M:S formats, not for single value)
        if (parts.length > 1) {
            if (seconds < 0 || seconds >= 60) {
                throw new IllegalArgumentException("Seconds must be between 0 and 59");
            }
            if (minutes < 0 || minutes >= 60) {
                throw new IllegalArgumentException("Minutes must be between 0 and 59");
            }
        } else {
            // Single value - must be non-negative
            if (seconds < 0) {
                throw new IllegalArgumentException("Time cannot be negative");
            }
        }
        if (hours < 0) {
            throw new IllegalArgumentException("Hours cannot be negative");
        }

        // Convert to total seconds
        return hours * 3600 + minutes * 60 + seconds;
    }

    /**
     * Format seconds as HH:MM:SS
     */
    private static String formatTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    /**
     * Start the countdown timer
     * Displays timer every second and stops at 00:00:00
     */
    public void start() {
        long remainingSeconds = totalSeconds;

        // Display initial time
        System.out.println("🔔 Countdown Timer Starting...");
        System.out.println("Total time: " + formatTime(totalSeconds));
        System.out.println("─".repeat(40));

        while (remainingSeconds >= 0) {
            System.out.print("\r" + formatTime(remainingSeconds) + " remaining");

            if (remainingSeconds == 0) {
                System.out.println("\n" + "🔔 ".repeat(5) + "TIME'S UP! 🔔 ".repeat(5));
                break;
            }

            remainingSeconds--;

            try {
                Thread.sleep(1000); // 1 second
            } catch (InterruptedException e) {
                System.out.println("\nTimer interrupted");
                break;
            }
        }
    }

    /**
     * Get the total seconds
     */
    public long getTotalSeconds() {
        return totalSeconds;
    }

    /**
     * Get formatted time string
     */
    public String getFormattedTime() {
        return formatTime(totalSeconds);
    }

    /**
     * Main method with examples
     */
    public static void main(String[] args) throws IOException {
        System.out.println("╔" + "═".repeat(50) + "╗");
        System.out.println("║" + " ".repeat(10) + "COUNTDOWN TIMER IMPLEMENTATION" + " ".repeat(11) + "║");
        System.out.println("╚" + "═".repeat(50) + "╝");
        System.out.println();

        // Example 1: String parsing
        System.out.println("─".repeat(50));
        System.out.println("Example 1: Time String Parsing");
        System.out.println("─".repeat(50));
        demonstrateTimeStringParsing();

        // Example 2: Timer from file
        demonstrateTimerFromFile("example_timer_10s.txt");
        demonstrateTimerFromFile("example_timer_2m30s.txt");

        System.out.println("\n✅ All demonstrations complete!");
    }

    /**
     * Demonstrate time string parsing
     */
    private static void demonstrateTimeStringParsing() {
        String[][] testCases = {
            {"10", "10 seconds"},
            {"45", "45 seconds"},
            {"2:30", "2 minutes 30 seconds"},
            {"5:45", "5 minutes 45 seconds"},
            {"1:30:45", "1 hour 30 minutes 45 seconds"},
        };

        System.out.println("\nTime String Parsing Results:");
        System.out.println(String.format("%-20s %-25s %-15s", "Input", "Description", "Formatted"));
        System.out.println("─".repeat(60));

        for (String[] testCase : testCases) {
            try {
                long seconds = parseTimeString(testCase[0]);
                String formatted = formatTime(seconds);
                System.out.printf("%-20s %-25s %s%n", 
                    testCase[0], testCase[1], formatted);
            } catch (IllegalArgumentException e) {
                System.out.printf("%-20s %-25s ERROR: %s%n", 
                    testCase[0], testCase[1], e.getMessage());
            }
        }
    }

    /**
     * Demonstrate timer from file
     */
    private static void demonstrateTimerFromFile(String filename) {
        System.out.println("\n" + "─".repeat(50));
        System.out.println("Timer from File: " + filename);
        System.out.println("─".repeat(50));

        try {
            CountdownTimer timer = new CountdownTimer(filename);
            System.out.println("📄 File: " + filename);
            System.out.println("⏱ Time read: " + timer.getFormattedTime());
            System.out.println("\nStarting countdown...\n");

            // Run the countdown
            timer.start();
        } catch (FileNotFoundException e) {
            System.out.println("Note: File '" + filename + "' not found");
            System.out.println("Example file content: 10 or 2:30 or 1:30:45");
        } catch (IOException e) {
            System.out.println("Error reading file: " + e.getMessage());
        }
    }
}
