package osfx.kubert;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

public class ImageChecker {

    private static final String DOCKER_HUB_URL = "https://registry.hub.docker.com/v2/repositories";
    private final String dockerHubUsername;
    private final String dockerHubToken;

    public ImageChecker(String dockerHubUsername, String dockerHubToken) {
        this.dockerHubUsername = dockerHubUsername;
        this.dockerHubToken = dockerHubToken;
    }

    public String getLatestImage(String currentImage) {
        String[] parts = currentImage.split(":");
        String baseImage = parts[0];
        String currentTag = parts.length > 1 ? parts[1] : null;

        if (currentTag == null) return currentImage;

        String repository = baseImage.contains("/") ? baseImage : "library/" + baseImage; // handle official images

        try {
            List<String> availableTags = fetchAvailableTags(repository);
            Optional<String> latestTag = findLatestTag(currentTag, availableTags);

            return latestTag.map(tag -> baseImage + ":" + tag).orElse(currentImage);
        } catch (Exception e) {
            System.err.println("Failed to fetch tags: " + e.getMessage());
            return currentImage;
        }
    }


    private List<String> fetchAvailableTags(String repository) throws Exception {
        URL url = new URL(DOCKER_HUB_URL + "/" + repository + "/tags?page_size=100");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");

        // Add basic authentication
        String auth = dockerHubUsername + ":" + dockerHubToken;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        con.setRequestProperty("Authorization", "Basic " + encodedAuth);

        int responseCode = con.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new RuntimeException("Failed to fetch tags from Docker Hub: " + responseCode);
        }
        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String response = in.lines().collect(Collectors.joining());
        in.close();
        return parseTags(response);
    }

    private List<String> parseTags(String jsonResponse) {
        return Arrays.stream(jsonResponse.split("\"name\":\""))
                .skip(1)
                .map(tag -> tag.split("\"")[0])
                .filter(this::isVersionTag)
                .sorted(this::compareVersions)
                .collect(Collectors.toList());
    }

    private java.util.Optional<String> findLatestTag(String currentTag, List<String> availableTags) {
        return availableTags.stream()
                .filter(tag -> compareVersions(tag, currentTag) > 0)
                .max(this::compareVersions);
    }

    private boolean isVersionTag(String tag) {
        return Pattern.matches("\\d+\\.\\d+", tag); // Matches format like "03.33"
    }

    private int compareVersions(String tag1, String tag2) {
        String[] tag1Parts = tag1.split("\\.");
        String[] tag2Parts = tag2.split("\\.");
        int majorComparison = Integer.compare(Integer.parseInt(tag1Parts[0]), Integer.parseInt(tag2Parts[0]));
        return majorComparison != 0 ? majorComparison : Integer.compare(Integer.parseInt(tag1Parts[1]), Integer.parseInt(tag2Parts[1]));
    }
}

