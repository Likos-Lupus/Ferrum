package top.likoslupus.ferrum.testkit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DifferentialReportTest {

    @Test
    void matchesWhenNoMismatches() {
        assertTrue(new DifferentialReport("noise", 128, 0).matches());
        assertFalse(new DifferentialReport("noise", 128, 1).matches());
    }

    @Test
    void rejectsNegativeCounts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DifferentialReport("noise", -1, 0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new DifferentialReport("noise", 0, -1)
        );
    }

}
