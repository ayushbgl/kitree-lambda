package in.co.kitree.services;

import org.junit.jupiter.api.Test;

import static in.co.kitree.services.SemanticVersion.compareSemanticVersions;
import static org.junit.jupiter.api.Assertions.*;

public class SemanticVersionUnitTest {

    // A helper method to compare two versions and assert the expected result
    private void assertVersionComparison(String v1, String v2, int expected) {
        int actual = compareSemanticVersions(v1, v2);
        assertEquals(expected, actual);
    }

    // Test that equal versions return 0
    @Test
    public void testEqualVersions() {
        assertVersionComparison("1.0.0", "1.0.0", 0);
        assertVersionComparison("2.3.4", "2.3.4", 0);
        assertVersionComparison("3.12.0", "3.12.0", 0);
    }

    // Test that smaller versions return -1
    @Test
    public void testSmallerVersions() {
        assertVersionComparison("1.0.0", "1.0.1", -1);
        assertVersionComparison("2.3.4", "2.4.0", -1);
        assertVersionComparison("3.12.0", "4.0.0", -1);
    }

    // Test that larger versions return 1
    @Test
    public void testLargerVersions() {
        assertVersionComparison("1.0.1", "1.0.0", 1);
        assertVersionComparison("2.4.0", "2.3.4", 1);
        assertVersionComparison("4.0.0", "3.12.0", 1);
    }

    // Test that versions with different number of parts are handled correctly
    @Test
    public void testDifferentParts() {
        assertVersionComparison("1", "1", 0);
        assertVersionComparison("1", "1.0", 0);
        assertVersionComparison("1", "1.0.0", 0);
        assertVersionComparison("1", "2.0", -1);
        assertVersionComparison("2", "1.0", 1);
    }
}
