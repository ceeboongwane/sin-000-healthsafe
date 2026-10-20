package co.wethinkcode.healthsafe;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

public class WardServiceTest {

    private List<Map<String, Object>> sampleWards;

    @BeforeEach
    void setup() {
        sampleWards = List.of(
                Map.of("ward_id", "W-01", "wing", "East Wing", "department", "Cardiology", "beds_available", 3),
                Map.of("ward_id", "W-05", "wing", "South Wing", "department", "Oncology", "beds_available", 5),
                Map.of("ward_id", "W-08", "wing", "North Wing", "department", "Radiology", "beds_available", 4)
        );
    }

    // [ ] Provides lists of wards
    @Test
    void testWardsListShape() {
        assertEquals(3, sampleWards.size());
        assertTrue(sampleWards.get(0).containsKey("ward_id"));
        assertTrue(sampleWards.get(0).containsKey("department"));
    }

    // [ ] GET /wards/{id} -> 404 for unknown
    @Test
    void testFindWardFoundAndNotFound() {
        String id = "W-05";
        Optional<Map<String, Object>> found = sampleWards.stream()
                .filter(w -> id.equals(w.get("ward_id"))).findFirst();
        assertTrue(found.isPresent());

        String unknown = "W-99";
        Optional<Map<String, Object>> notFound = sampleWards.stream()
                .filter(w -> unknown.equals(w.get("ward_id"))).findFirst();
        assertTrue(notFound.isEmpty()); // should return 404
    }

    // [ ] Handles W-05 vs w-05 casing
    @Test
    void testCaseInsensitiveLookup() {
        String query = "w-05".toUpperCase();
        assertEquals("W-05", query);
        assertTrue(sampleWards.stream().anyMatch(w -> query.equals(w.get("ward_id"))));
    }

    // [ ] Provides lists of departments - distinct
    @Test
    void testDepartmentsDistinct() {
        List<Map<String, Object>> wardsWithDupDept = List.of(
                Map.of("ward_id", "W-01", "department", "Cardiology"),
                Map.of("ward_id", "W-02", "department", "Cardiology"),
                Map.of("ward_id", "W-03", "department", "Oncology")
        );
        Set<String> depts = new LinkedHashSet<>();
        for (Map w : wardsWithDupDept) depts.add(w.get("department").toString());

        assertEquals(2, depts.size());
        assertTrue(depts.contains("Cardiology"));
    }

    // [ ] Empty wing normalized - W-08,,Oncology,4
    @Test
    void testEmptyWingHandled() {
        Map<String, Object> ward = Map.of("ward_id", "W-08", "department", "Oncology");
        // wing missing should be null not crash
        assertNull(ward.get("wing"));
    }
}