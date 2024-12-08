package org.matsim.dashboard;

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
import tech.tablesaw.plotly.traces.BarTrace;
import tech.tablesaw.plotly.traces.HistogramTrace;
import tech.tablesaw.plotly.traces.ScatterTrace;
import tech.tablesaw.plotly.traces.Trace;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AgentBasedLossTimeDashboard implements Dashboard {

	public double priority(){return -1;}

	public void configure(Header header, Layout layout) {

		header.title = "Loss Time";
		header.description = "Detailed Loss Time Analysis";

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
			})
			.el(Bar.class, (viz, data) -> {
					viz.title = "Loss Time per mode v2";
					viz.description = "in seconds";

					viz.stacked = false;
					viz.dataset = data.compute(AgentBasedLossTimeAnalysis.class, "summary_modeSpecificLegsLossTime.csv");
					viz.x = "mode";
					viz.xAxisName = "Mode";
					viz.yAxisName = "Loss Time [s]";
					viz.columns = List.of("cumulative_loss_time", "failed_routings");
		});
/*
		layout.row("BarChart ModesUsed")
			.el(Bar.class, (viz, data) -> {
				viz.title = "Modes Used";
				viz.description = "teleported modes result in 0 seconds loss time, ergo all solely bike and walk users are defined as true in their loss time dependent liveability ranking - here shown is the number of persons usimg each combination of modes";

				viz.dataset = data.compute(AgentBasedLossTimeAnalysis.class, "lossTime_stats_perAgent.csv")
					.groupBy(List.of("modesUsed"))
					.aggregate("person", Plotly.AggrFunc.COUNT);
				viz.x = "modesUsed";
				viz.y = "person";
				viz.xAxisName = "Mode";
				viz.yAxisName = "Number of Persons";
				viz.stacked = false;
			});
*/
		layout.row("BarChart ModesUsed")
			.el(Plotly.class, (viz, data) -> {

				viz.title = "Modes Used";
				viz.description = "teleported modes result in 0 seconds loss time, ergo all solely bike and walk users are defined as true in their loss time dependent liveability ranking - here shown is the number of persons usimg each combination of modes";

				Plotly.DataSet ds = viz.addDataset(data.compute(AgentBasedLossTimeAnalysis.class, "lossTime_stats_perAgent.csv"))
					.aggregate(List.of("modeUsed"),"Person", Plotly.AggrFunc.SUM);

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
			});


		layout.row("histogramm")
			.el(Bar.class, (viz, data) -> {
				viz.title = "Histogram";
				viz.description = "loss time over time";

				viz.stacked = false;
				viz.dataset = data.compute(AgentBasedLossTimeAnalysis.class, "output_legsLossTime_new.csv");
				viz.x = "dep_time";
			})
			.el(Plotly.class, (viz, data) -> {

				viz.title = "Histogramm Loss Time";
				viz.description = "loss time over time";

				Plotly.DataSet ds = viz.addDataset(data.compute(AgentBasedLossTimeAnalysis.class, "output_legsLossTime_new.csv"))
					.aggregate(List.of("mode"), "dep_time", Plotly.AggrFunc.SUM);

				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.barMode(tech.tablesaw.plotly.components.Layout.BarMode.GROUP)
					.xAxis(Axis.builder().title("Mode").build())
					.yAxis(Axis.builder().title("Loss Time").build())
					.build();

				viz.addTrace(BarTrace.builder(Plotly.OBJ_INPUT, Plotly.INPUT).orientation(BarTrace.Orientation.VERTICAL).build(), ds.mapping()
					.x("mode")
					.y("dep_time")
					.name("lost_time", ColorScheme.RdYlBu)
				);
			})

//NEUER ANSATZ HISTOGRAMM
			.el(Plotly.class, (viz, data) -> {
				viz.title = "Number of used modes";
				viz.description = "Number of the used mode";

				// Dataset hinzufügen
				//Plotly.DataSet dataset = viz.addDataset(data.compute(AgentBasedLossTimeAnalysis.class, "output_legsLossTime_new.csv"));

				// Dateipfad aus dem Dataset abrufen
				String csvFilePath = "C:\\Users\\annab\\MatSim for MA\\Output_Cluster\\OBS_Base\\output_OBS_Base\\berlin-v6.3-10pct\\analysis\\analysis\\output_legsLossTime_new-Kopie.csv";
				//String csvFilePath = "C:\\Users\\holle\\IdeaProjects\\OpenBerlinScenario_MA\\output\\berlin-v6.3-10pct\\analysis\\analysis\\output_legsLossTime_new.csv";

				// Layout definieren
				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.xAxis(Axis.builder().title("Modes").build())
					.yAxis(Axis.builder().title("Number of modes").build())
					.build();

				String columnName = "mode";

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
						.y("loss_time") );// Spaltenname für die Y-Achse (Verlustzeiten)

			});






	}
}

