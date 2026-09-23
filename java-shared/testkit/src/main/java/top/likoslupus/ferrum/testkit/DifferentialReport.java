package top.likoslupus.ferrum.testkit;

/**
 * Summary of a differential comparison between a reference implementation and a candidate.
 *
 * @param subject         a short description of what was compared
 * @param comparedSamples the number of compared samples
 * @param mismatches      the number of samples that differed
 */
public record DifferentialReport(
        String subject,
        int comparedSamples,
        int mismatches
) {

    public DifferentialReport {
        if (comparedSamples < 0) {
            throw new IllegalArgumentException("comparedSamples must be >= 0");
        }
        if (mismatches < 0) {
            throw new IllegalArgumentException("mismatches must be >= 0");
        }
    }

    public boolean matches() {
        return mismatches == 0;
    }

}
