package org.matsim.analysis;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class OverpassQuery {
	public static void main(String[] args) {
		String overpassUrl = "https://overpass-api.de/api/interpreter";
		String query = """
            [out:json];
            node ["leisure"="park"](50.6,7.0,50.8,7.3);
            out body;
        """;

		try {
			// Verbindung zur Overpass-API herstellen
			URL url = new URL(overpassUrl);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("POST");
			connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			connection.setDoOutput(true);

			// Query senden
			try (OutputStream os = connection.getOutputStream()) {
				os.write(query.getBytes());
				os.flush();
			}

			// Antwort lesen
			int responseCode = connection.getResponseCode();
			if (responseCode == HttpURLConnection.HTTP_OK) {
				try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
					String line;
					while ((line = br.readLine()) != null) {
						System.out.println(line);
					}
				}
			} else {
				System.out.println("Fehler: HTTP-Code " + responseCode);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

