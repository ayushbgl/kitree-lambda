package in.co.kitree.services;

public class SemanticVersion {

    // A function that takes two semantic versions as strings and returns -1 if the first one is smaller, 0 if they are equal, and 1 if the first one is larger
    public static int compareSemanticVersions(String v1, String v2) {
        // Split the versions by dots
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");

        // Loop through the parts and compare them
        for (int i = 0; i < Math.min(parts1.length, parts2.length); i++) {
            // Parse the parts as integers
            int num1 = Integer.parseInt(parts1[i]);
            int num2 = Integer.parseInt(parts2[i]);

            // If the first part is smaller, return -1
            if (num1 < num2) {
                return -1;
            }

            // If the first part is larger, return 1
            if (num1 > num2) {
                return 1;
            }

            // If the parts are equal, continue to the next part
        }

        // If one version has more parts than the other, check if they are all zeros
        if (parts1.length > parts2.length) {
            for (int i = parts2.length; i < parts1.length; i++) {
                // If any part is not zero, return 1
                if (Integer.parseInt(parts1[i]) != 0) {
                    return 1;
                }
            }
        } else if (parts2.length > parts1.length) {
            for (int i = parts1.length; i < parts2.length; i++) {
                // If any part is not zero, return -1
                if (Integer.parseInt(parts2[i]) != 0) {
                    return -1;
                }
            }
        }

        // If all parts are equal or zeros, return 0
        return 0;
    }
}
