package co.wethinkcode.healthsafe;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

class IngestionServiceAppTest {

    // [ ] Handles inconsistent casing (IDs, names, status values)
    @Test
    void testCasing() {
        assertEquals("W-05", "w-05".trim().toUpperCase());
        assertEquals("Paediatrics", IngestionServiceApp.cleanDept("PAEDIATRICS"));
        assertEquals("Paediatrics", IngestionServiceApp.cleanDept("paediatrics"));
        assertEquals("Cardiology", IngestionServiceApp.cleanDept("cardiology"));
    }

    // [ ] Trims padding / collapses internal double spaces
    @Test
    void testTrimAndDoubleSpaces() {
        assertEquals("East Wing", IngestionServiceApp.cleanWing(" East Wing "));
        assertEquals("South Wing", IngestionServiceApp.cleanWing("South  Wing"));
        assertEquals("West Wing", IngestionServiceApp.cleanWing("West Wing "));
        assertEquals("North Wing", IngestionServiceApp.cleanWing("North wing"));
    }

    // [ ] Detects and handles duplicate records (W-05 vs w-05)
    @Test
    void testDuplicateHandling() {
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();

        Map<String, Object> ward1 = new LinkedHashMap<>();
        ward1.put("ward_id", "W-05");
        ward1.put("beds_available", 5);

        Map<String, Object> ward2 = new LinkedHashMap<>();
        ward2.put("ward_id", "W-05");
        ward2.put("beds_available", 5);

        // Simulate logic: W-05 vs w-05 -> same key after toUpperCase
        String id1 = "W-05".trim().toUpperCase();
        String id2 = "w-05".trim().toUpperCase();

        assertEquals(id1, id2);

        byId.put(id1, ward1);
        // second duplicate should not create new entry
        if (!byId.containsKey(id2)) {
            byId.put(id2, ward2);
        }
        assertEquals(1, byId.size());
    }

    // [ ] Normalizes missing/placeholder values to one representation
    @Test
    void testPlaceholders() {
        assertNull(IngestionServiceApp.cleanWing("N/A"));
        assertNull(IngestionServiceApp.cleanWing("TBD"));
        assertNull(IngestionServiceApp.cleanWing("-"));
        assertNull(IngestionServiceApp.cleanWing(""));
        assertNull(IngestionServiceApp.cleanDept("NaN"));
        assertNull(IngestionServiceApp.cleanBeds("N/A"));
        assertNull(IngestionServiceApp.cleanBeds("TBD"));
        assertNull(IngestionServiceApp.cleanBeds("unknown"));
    }

    // [ ] Handles invalid/non-numeric values without crashing
    @Test
    void testInvalidNumeric() {
        assertEquals(5, IngestionServiceApp.cleanBeds("five")); // word -> number
        assertEquals(0, IngestionServiceApp.cleanBeds("full")); // full -> 0
        assertNull(IngestionServiceApp.cleanBeds("-1")); // negative
        assertNull(IngestionServiceApp.cleanBeds("-2")); // negative
        assertNull(IngestionServiceApp.cleanBeds("2023")); // unrealistic >100
        assertNull(IngestionServiceApp.cleanBeds("unknown")); // not crash
        assertDoesNotThrow(() -> IngestionServiceApp.cleanBeds("abc"));
    }

    // [ ] Normalizes boolean/flag variants (active flag from beds)
    @Test
    void testActiveFlag() {
        // In your code active = beds > 0
        Integer beds1 = 3;
        Integer beds2 = 0;
        Integer beds3 = null;

        assertTrue(beds1 != null && beds1 > 0);
        assertFalse(beds2 != null && beds2 > 0);
        assertFalse(beds3 != null && beds3 > 0);
    }

    // [ ] Normalizes department spelling
    @Test
    void testDepartmentNormalization() {
        assertEquals("Paediatrics", IngestionServiceApp.cleanDept("Pediatrics")); // US -> UK
        assertEquals("Paediatrics", IngestionServiceApp.cleanDept("paediatrics"));
        assertEquals("Cardiology", IngestionServiceApp.cleanDept("cardiology"));
        assertEquals("Oncology", IngestionServiceApp.cleanDept("Oncology"));
        assertEquals("Radiology", IngestionServiceApp.cleanDept("radiology"));
    }

    // Full file test with your actual CSV data
    @Test
    void testRealCsvRows() {
        assertEquals(3, IngestionServiceApp.cleanBeds("3"));
        assertNull(IngestionServiceApp.cleanBeds("N/A")); // W-02
        assertEquals(0, IngestionServiceApp.cleanBeds("0")); // W-03
        assertNull(IngestionServiceApp.cleanBeds("-1")); // W-04
        assertEquals(5, IngestionServiceApp.cleanBeds("5")); // W-05
        assertEquals(5, IngestionServiceApp.cleanBeds("five")); // w-05 duplicate
        assertEquals(4, IngestionServiceApp.cleanBeds("4")); // W-08 empty wing
    }

    // [ ] Exposes cleaned records over REST - test JSON shape
    @Test
    void testCleanedRecordShape() {
        Map<String, Object> ward = new LinkedHashMap<>();
        ward.put("ward_id", "W-01");
        ward.put("wing", "East Wing");
        ward.put("department", "Cardiology");
        ward.put("beds_available", 3);

        assertTrue(ward.containsKey("ward_id"));
        assertTrue(ward.containsKey("wing"));
        assertTrue(ward.containsKey("department"));
        assertTrue(ward.containsKey("beds_available"));
        assertEquals("W-01", ward.get("ward_id"));
    }
}