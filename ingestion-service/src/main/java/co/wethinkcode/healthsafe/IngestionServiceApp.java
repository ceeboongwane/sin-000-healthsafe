package co.wethinkcode.healthsafe;

import io.javalin.Javalin;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

public class IngestionServiceApp {

    private static List<Map<String, Object>> cleanedWards = new ArrayList<>();
    private static final Set<String> PLACEHOLDERS = Set.of("n/a","tbd","-","nan","null","","unknown","na");
    private static final Map<String,Integer> WORDS = Map.of(
            "one",1,"two",2,"three",3,"four",4,"five",5,"six",6,
            "seven",7,"eight",8,"nine",9,"ten",10
    );

    public static void main(String[] args) {

        cleanedWards = loadAndClean();

        Javalin app = Javalin.create().start(7030);

        app.get("/health", ctx -> ctx.result("OK"));

        // TODO: read and clean src/main/resources/wards-outdated.csv (wards, wings, specialist departments data —
        // trim whitespace, fix casing, normalize dates/booleans) and expose the
        // cleaned records here for the other services to consume.
        // This is what ward-service (7031) will call
        app.get("/wards", ctx -> ctx.json(cleanedWards));

        System.out.println("IngestionService on 7030 - " + cleanedWards.size() + " wards cleaned");
    }

    private static List<Map<String, Object>> loadAndClean() {
        Map<String, Map<String, Object>> deduped = new LinkedHashMap<>();
        try {
            // Works both from IDE and from jar
            InputStream is = IngestionServiceApp.class.getClassLoader().getResourceAsStream("wards-outdated.csv");
            if (is == null) {
                is = Files.newInputStream(Paths.get("src/main/resources/wards-outdated.csv"));
            }
            List<String> lines = new BufferedReader(new InputStreamReader(is)).lines().toList();

            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.isBlank()) continue;
                String[] r = line.split(",", -1); // keep empty like W-08,,Oncology,4
                if (r.length < 4) continue;

                // --- CLEAN EACH FIELD ---
                String id = cleanId(r[0]); // W-01 vs w-02 vs W-03<space>
                String wing = cleanWing(r[1]); // East Wing , east wing, South  Wing
                String dept = cleanDept(r[2]); // Cardiology, cardiology, paediatrics vs Pediatrics
                Integer beds = cleanBeds(r[3]); // N/A, -1, five, TBD, full, -2, unknown, 2023

                if (id.equals("UNKNOWN")) continue;

                Map<String, Object> ward = new LinkedHashMap<>();
                ward.put("ward_id", id);
                ward.put("wing", wing);
                ward.put("department", dept);
                ward.put("beds_available", beds);
                ward.put("active", beds != null && beds > 0);

                // [ ] Detects duplicate: W-05 vs w-05
                if (deduped.containsKey(id)) {
                    Map<String, Object> existing = deduped.get(id);
                    Integer existingBeds = (Integer) existing.get("beds_available");
                    // keep the most complete record
                    if (existingBeds == null && beds != null) {
                        deduped.put(id, ward);
                    }
                } else {
                    deduped.put(id, ward);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<>(deduped.values());
    }

    // [ ] Handles inconsistent casing + [ ] Trims padding / collapses double spaces
    static String cleanId(String raw) {
        if (isPlaceholder(raw)) return "UNKNOWN";
        return raw.trim().toUpperCase().replaceAll("\\s+", "");
    }

    static String cleanWing(String raw) {
        if (isPlaceholder(raw)) return null;
        String s = raw.trim().replaceAll("\\s+", " "); // "South  Wing" -> "South Wing"
        return Arrays.stream(s.toLowerCase().split(" "))
                .map(w -> w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(Collectors.joining(" "));
    }

    static String cleanDept(String raw) {
        if (isPlaceholder(raw)) return null;
        String s = raw.trim().replaceAll("\\s+", " ").toLowerCase();
        return switch (s) {
            case "cardiology" -> "Cardiology";
            case "paediatrics", "pediatrics" -> "Paediatrics"; // normalize US/UK spelling
            case "oncology" -> "Oncology";
            case "radiology" -> "Radiology";
            case "icu" -> "ICU";
            case "maternity" -> "Maternity";
            default -> s.isEmpty() ? null : Character.toUpperCase(s.charAt(0)) + s.substring(1);
        };
    }

    // [ ] Normalizes missing/placeholder + [ ] Handles invalid/non-numeric without crashing
    static Integer cleanBeds(String raw) {
        if (isPlaceholder(raw)) return null;
        String s = raw.trim().toLowerCase();
        if (WORDS.containsKey(s)) return WORDS.get(s); // five -> 5
        if (s.equals("full")) return 0; // full -> 0 beds available
        try {
            int v = Integer.parseInt(s);
            if (v < 0 || v > 100) return null; // -1, -2, 2023 -> invalid
            return v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static boolean isPlaceholder(String raw) {
        if (raw == null) return true;
        return PLACEHOLDERS.contains(raw.trim().toLowerCase());
    }
}
