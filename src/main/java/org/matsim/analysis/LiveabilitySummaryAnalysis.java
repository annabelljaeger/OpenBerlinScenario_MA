package org.matsim.analysis;

import org.matsim.application.MATSimAppCommand;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class LiveabilitySummaryAnalysis implements MATSimAppCommand {

		public static void main(String[] args) {
		// Der Pfad zur Ausgabe-CSV-Datei
		String fileName = "summaryTiles.csv";

		// Inhalt der CSV-Datei
		String[] rows = {
			"OverallRanking,65%,",
			"LossTime,21%,BaseCase",
			"Emissions,54%,PolicyA"
		};

		// CSV-Datei erstellen und Daten schreiben
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
			for (String row : rows) {
				writer.write(row);
				writer.newLine(); // Zeilenumbruch
			}
			System.out.println("CSV-Datei wurde erfolgreich erstellt: " + fileName);
		} catch (IOException e) {
			System.err.println("Fehler beim Erstellen der CSV-Datei: " + e.getMessage());
		}
	}

	@Override
	public Integer call() throws Exception {
		return 0;
	}
}
