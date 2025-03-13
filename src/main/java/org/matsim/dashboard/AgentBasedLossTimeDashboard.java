package org.matsim.dashboard;

import org.matsim.analysis.AgentBasedGreenSpaceAnalysis;
import org.matsim.analysis.AgentBasedLossTimeAnalysis;
import org.matsim.simwrapper.Dashboard;
import org.matsim.simwrapper.Header;
import org.matsim.simwrapper.Layout;
import org.matsim.simwrapper.viz.*;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.LongColumn;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.*;
import tech.tablesaw.api.Table;
import tech.tablesaw.io.csv.CsvReadOptions;
import tech.tablesaw.plotly.components.Axis;
import tech.tablesaw.plotly.components.Line;
import tech.tablesaw.plotly.traces.BarTrace;
import tech.tablesaw.plotly.traces.HistogramTrace;
import tech.tablesaw.plotly.traces.ScatterTrace;
import tech.tablesaw.plotly.traces.Trace;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import static org.matsim.dashboard.RunLiveabilityDashboard.getValidLiveabilityOutputDirectory;
import static org.matsim.dashboard.RunLiveabilityDashboard.getValidOutputDirectory;

public class AgentBasedLossTimeDashboard implements Dashboard {

	public double priority(){return -2;}

	public void configure(Header header, Layout layout) {

		header.title = "Loss Time";
		header.description = "Detailed Loss Time Analysis";

		layout.row("LossTime Ranking Map")
			.el(XYTime.class, (viz, data) -> {
				viz.title = "LossTime ranking results map";
				viz.description = "Here you can see the agents according to their loss time ranking depicted on their home location";
				viz.height = 10.0;
				viz.file = data.compute(AgentBasedLossTimeAnalysis.class, "XYTAgentBasedLossTimeMap.xyt.csv");

				//BREAKPOINTS MÜSSEN NOCH DEFINIERT WERDEN; RADIUS AUCH; COLOR RAMP AUCH; CENTER AUCH
			});

		layout.row("overall ranking result loss time")
			.el(Tile.class, (viz, data) -> {

				viz.title = "Loss Time Ranking Value";
				viz.description = "percentage of agents with less than 15 % loss time";

				viz.dataset = data.compute(AgentBasedLossTimeAnalysis.class, "lossTime_RankingValue.csv");
				viz.height = 0.1;

			});

		layout.row("BarChart Modes")
			.el(Plotly.class, (viz, data) -> {

				viz.title = "Mode Specific Loss Time";
				viz.description = "sum by mode";

				Plotly.DataSet ds = viz.addDataset(data.compute(AgentBasedLossTimeAnalysis.class, "summary_modeSpecificLegsLossTime.csv"));
				//.aggregate(List.of("mode"), "cumulative_loss_time", Plotly.AggrFunc.SUM);

				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.barMode(tech.tablesaw.plotly.components.Layout.BarMode.GROUP)
					.xAxis(Axis.builder().title("Mode").build())
					.yAxis(Axis.builder().title("Loss Time").build())
					.build();

				viz.addTrace(BarTrace.builder(Plotly.OBJ_INPUT, Plotly.INPUT).orientation(BarTrace.Orientation.VERTICAL).build(), ds.mapping()
					.x("mode")
					.y("cumulative_loss_time")
					.name("cumulative_loss_time", ColorScheme.RdYlBu)

				);
//			})
//			.el(Bar.class, (viz, data) -> {
//				viz.title = "Loss Time per mode v2";
//				viz.description = "in seconds";
//
//				viz.stacked = false;
//				viz.dataset = data.compute(AgentBasedLossTimeAnalysis.class, "summary_modeSpecificLegsLossTime.csv");
//				viz.x = "mode";
//				viz.xAxisName = "Mode";
//				viz.yAxisName = "Loss Time [s]";
//				viz.columns = List.of("cumulative_loss_time", "failed_routings");
			});

		layout.row("ModesUsed (BarTrace) & Scatter Plot")

			.el(Plotly.class, (viz, data) -> {
				viz.title = "Number of used modes";
				viz.description = "Number of the used mode";

				// Dataset hinzufügen
				//Plotly.DataSet dataset = viz.addDataset(data.compute(AgentBasedLossTimeAnalysis.class, "output_legsLossTime_new.csv"));


				//ACHTUNG: AUFRUF KANN HIER NICHT KLAPPEN WEIL DATEI ZU DEM ZEITPUNKT NOCH NICHT VORHANDEN!! BERECHNUNG IN ANALYSKLASSE AUSLAGERN!!

				// Dateipfad aus dem Dataset abrufen
			//	String csvFilePath = "C:\\Users\\annab\\MatSim for MA\\Output_Cluster\\OBS_Base\\output_OBS_Base\\berlin-v6.3-10pct\\analysis\\analysis\\lossTime_stats_perAgent.csv";
				String csvFilePath = String.valueOf(getValidLiveabilityOutputDirectory().resolve("lossTime_stats_perAgent.csv"));

				// Layout definieren
				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.xAxis(Axis.builder().title("Modes").build())
					.yAxis(Axis.builder().title("Number of modes").build())
					.build();

				String columnName = "modesUsed";

				try {
					// CSV-Datei mit Tablesaw laden
					Table table = Table.read().csv(CsvReadOptions.builder(csvFilePath).separator(';').build());

					// Überprüfen, ob die Spalte existiert
					if (!table.columnNames().contains(columnName)) {
						throw new IllegalArgumentException("Spalte '" + columnName + "' existiert nicht in der CSV-Datei.");
					}

					// Spalte extrahieren und Häufigkeiten berechnen
					StringColumn categories = table.stringColumn(columnName);
					Map<String, Integer> frequencyMap = new LinkedHashMap<>();
					for (String value : categories) {
						if (value != null && !value.isEmpty()) { // Sicherstellen, dass der Wert gültig ist
							frequencyMap.put(value, frequencyMap.getOrDefault(value, 0) + 1); // Frequenz erhöhen
						}
					}

					// Frequenzen in Tablesaw-Spalten umwandeln
					StringColumn uniqueValues = StringColumn.create("Unique Values", frequencyMap.keySet());
					DoubleColumn frequencies = DoubleColumn.create("Frequencies",
						frequencyMap.values().stream().mapToDouble(Integer::doubleValue).toArray());

					// Bar-Trace hinzufügen
					viz.addTrace(BarTrace.builder(uniqueValues, frequencies)
						.name("Frequencies") // Name des Traces in der Legende
						.build());

				} catch (IllegalArgumentException e) {
					System.err.println("Fehler in der Tabellenstruktur: " + e.getMessage());
				} catch (Exception e) {
					e.printStackTrace(); // Für unerwartete Fehler
				}
			})

			.el(Plotly.class, (viz, data) -> {

				viz.title = "Scatter Plot Travel Time over Loss Time";
				viz.description = "Agent based analysis of travel time compared to loss time";

				Plotly.DataSet ds = viz.addDataset(data.compute(AgentBasedLossTimeAnalysis.class, "output_legsLossTime_new.csv"));

				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.barMode(tech.tablesaw.plotly.components.Layout.BarMode.GROUP)
					.xAxis(Axis.builder().title("Travel Time").build())
					.yAxis(Axis.builder().title("Loss Time").build())
					.build();

				viz.addTrace(ScatterTrace.builder(Plotly.INPUT, Plotly.INPUT).build(), ds.mapping()
					.x("trav_time")
					.y("loss_time")
					.name("loss_time", ColorScheme.RdYlBu)

			//	viz.addTrace((Trace) new Line.LineBuilder()),

				);
			});

		layout.row("offene Tasks")
			.el(TextBlock.class, (viz, data) -> {
				viz.title = "Open Tasks";
				viz.description = "diese Diagramme würde ich gerne noch erstellen - dauert aber gerade zu lang";
				viz.content = "\t1. Loss Times über den Tag verteilt (Bar)\n" +
					"\t2. Top Ausreißer räumlich auf Karte darstellen (zeitlich)?\n"+
					"\t7. Zusätzliche Infos-Tabelle\n" +
					"\t\ta. Wie viele Routen werden gar nicht richtig gefunden und somit geskippt (LossTime 0)?\n" +
					"\t\tb. Wie viele Agenten nutzen nur teleportierte Modi und erhalten somit automatisch den True-Wert?\n" +
					"\t\tc. Wie viele Agenten haben über X%/xMin (z.B. 100%/30 min) Verlustzeit - evtl. 2 Angaben, einmal > 100% und einmal mehr als 1 Std.\n";

			});




		layout.row("nicht mehr relevante Versuche")

			.el(Plotly.class, (viz, data) -> {

				viz.title = "Modes Used";
				viz.description = "teleported modes result in 0 seconds loss time, ergo all solely bike and walk users are defined as true in their loss time dependent liveability ranking - here shown is the number of persons usimg each combination of modes";

				Plotly.DataSet ds = viz.addDataset(data.compute(AgentBasedLossTimeAnalysis.class, "lossTime_stats_perAgent.csv"))
					.aggregate(List.of("modeUsed"), "Person", Plotly.AggrFunc.SUM);

				// Layout und Achsen anpassen
				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.xAxis(Axis.builder().title("Modes").build())
					.yAxis(Axis.builder().title("Number of Persons").build())
					.build();

				viz.addTrace(BarTrace.builder(Plotly.OBJ_INPUT, Plotly.INPUT).orientation(BarTrace.Orientation.VERTICAL).build(), ds.mapping()
						.x("modesUsed")
						.y("Person")
					//				.name("mode", ColorScheme.RdYlBu)
				);
			})

			.el(Plotly.class, (viz, data) -> {
				viz.title = "Lost Time per Quarter Hour";
				viz.description = "Sum of lost times (in seconds) for each 15-minute interval of the day";

				// Add the legs dataset
				Plotly.DataSet dataset = viz.addDataset(data.compute(AgentBasedLossTimeAnalysis.class, "output_legsLossTime_new.csv"));

				// Define the layout for the plot
				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.xAxis(Axis.builder().title("Time Interval").build())
					.yAxis(Axis.builder().title("Total Loss Time (s)").build())
					.build();

				//		viz.addTrace((Trace) HistogramTrace.builder(Plotly.INPUT));
				//viz.addTrace(HistogramTrace.builder(Plotly.OBJ_INPUT, Plotly.OBJ_INPUT)

				viz.addTrace(BarTrace.builder(Plotly.OBJ_INPUT, Plotly.INPUT) // Verwenden von INPUT
						.name("Loss Time") // Name des Traces
						.build(),
					dataset.mapping() // Mapping verwenden, um Spalten aus dem Dataset zuzuordnen
						.x("dep_time") // Spaltenname für die X-Achse (Zeitintervalle)
						.y("loss_time"));// Spaltenname für die Y-Achse (Verlustzeiten)

			});

		//HIER NOCH UNFERTIGER VERSUCH DIE AUSREIßER UND SONSTIGE FEHLER AUSZUBLENDEN - SCATTERPLOT DETAILARBEIT NOCH NÖTIG
		layout.row("neuer Ansatz Scatterplot")
		.el(Plotly.class, (viz, data) -> {

			viz.title = "Scatter Plot Travel Time over Loss Time";
			viz.description = "Agent based analysis of travel time compared to loss time";

//			// Bereinige die Daten vor dem Hinzufügen zum Dataset
//			List<Map<String, Object>> cleanedData = new ArrayList<>();
//			try (BufferedReader br = new BufferedReader(new FileReader(String.valueOf(getValidOutputDirectory().resolve("analysis\\analysis\\output_legsLossTime_new.csv"))))) {
//				String line;
//				while ((line = br.readLine()) != null) {
//					String[] values = line.split(";");
//					double travTime = Double.parseDouble(values[3]);  // Annahme: Travel Time ist in der ersten Spalte
//					double lossTime = Double.parseDouble(values[5]);  // Annahme: Loss Time ist in der zweiten Spalte
//
//					// Nullwerte für Loss Time und Ausreißer filtern
//					if (lossTime != 0 && travTime <= 1000 && lossTime <= 500) {
//						Map<String, Object> row = new HashMap<>();
//						row.put("trav_time", travTime);
//						row.put("loss_time", lossTime);
//						cleanedData.add(row);
//					}
//				}
////			} catch (IOException e) {
////				e.printStackTrace();
////			}
////
////			// Konvertiere die bereinigten Daten in ein Dataset
////			Plotly.DataSet ds = viz.addDataset(cleanedData.toString());
//
//			// Layout konfigurieren
//			viz.layout = tech.tablesaw.plotly.components.Layout.builder()
//				.barMode(tech.tablesaw.plotly.components.Layout.BarMode.GROUP)
//				.xAxis(Axis.builder().title("Travel Time").build())
//				.yAxis(Axis.builder().title("Loss Time").build())
//				.build();
//
//			// Scatter Trace für die bereinigten Daten
//			viz.addTrace(ScatterTrace.builder(Plotly.INPUT, Plotly.INPUT).build(), ds.mapping()
//				.x("trav_time")
//				.y("loss_time")
//				.name("Loss Time vs. Travel Time", ColorScheme.RdYlBu)
//			);
//
//			// Füge die Linie Travel Time = 2 * Loss Time hinzu
//			viz.addTrace(ScatterTrace.builder(Plotly.INPUT, Plotly.INPUT).build(), ds.mapping()
//				.x("trav_time")
//				.y("trav_time")//.transform(v -> v / 2)  // Die Linie, bei der Travel Time = 2 * Loss Time
//				.name("Travel Time = 2 * Loss Time")
//			//	.line(tech.tablesaw.plotly.components.Line.builder().color("red").width(2).build())  // Linie in Rot
//			);
		});

	}

}
