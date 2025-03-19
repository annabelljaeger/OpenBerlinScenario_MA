package org.matsim.dashboard;

import org.matsim.analysis.AgentBasedTrafficQualityAnalysis;
import org.matsim.analysis.AgentLiveabilityInfoCollection;
import org.matsim.simwrapper.Dashboard;
import org.matsim.simwrapper.Header;
import org.matsim.simwrapper.Layout;
import org.matsim.simwrapper.viz.*;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.io.csv.CsvReadOptions;
import tech.tablesaw.plotly.components.Axis;
import tech.tablesaw.plotly.traces.BarTrace;
import tech.tablesaw.plotly.traces.HistogramTrace;
import tech.tablesaw.plotly.traces.ScatterTrace;

import java.util.*;

import static org.matsim.dashboard.RunLiveabilityDashboard.getValidLiveabilityOutputDirectory;

public class AgentBasedTrafficQualityDashboard implements Dashboard {

	public double priority(){return -1;}

	public void configure(Header header, Layout layout) {

		header.title = "Traffic Quality";
		header.description = "A key indicator of the quality of the transport system is its efficiency. The time spent in traffic" +
			"and especially in congested traffic have a major influence on the health and behaviour of those involved." +
			"Therefore the absolute travel time spent travelling as well as the loss time during those trips is analysed" +
			"and displayed in the following dashboard.";

		// map as xyt-Map displaying the worst deviation from either of the two indicator deviation values as the dimension index value
		layout.row("Traffic Quality Overall Index Value Map")
			.el(XYTime.class, (viz, data) -> {
				viz.title = "Traffic Quality Overall Index Value Map";
				viz.description = "Here you can see the agents according to their traffic quality index values depicted on their home location";
				viz.height = 15.0;
				viz.radius = 15.0;
				viz.file = data.compute(AgentBasedTrafficQualityAnalysis.class, "travelTime_XYT_agentBasedTrafficQuality.xyt.csv");

				String[] colors = {"#008000", "#6eaa5e", "#93bf85", "#f0a08a", "#d86043", "#c93c20", "#af230c", "#9b88d3", "#7863c4", "#4f3fb4", "#001ca4", "#191350"};
				viz.setBreakpoints(colors, -0.5, -0.25, 0.0, 0.25, 0.5, 0.75, 1.0, 2.0, 4.0, 8.0, 16.0);
			});

		layout.row("overall index result traffic quality")
			.el(Tile.class, (viz, data) -> {

				viz.title = "Traffic Quality Index Value";
				viz.description = "Traffic quality in terms of liveability tends to be measured in the time spent in traffic. " +
					"People are prone to health risks, stress and aggressive behaviour when spending more than 30 minutes driving a car" +
					"or 60 minutes on public transport or ride trips. Also time lost in traffic of more than 20 % of the total travel time" +
					"can have these effects and is therefore identified with these traffic quality indicator.";

				viz.dataset = data.compute(AgentBasedTrafficQualityAnalysis.class, "travelTime_tiles_overall.csv");
				viz.height = 0.1;
			});

		layout.row("Scatterplot 2 Indikatoren")
			.el(Plotly.class, (viz, data) -> {

				viz.title = "Scatter Plot Longest Trip over Loss Time";
				viz.description = "Agent based analysis of longest trip compared to loss time";

				Plotly.DataSet ds = viz.addDataset(data.compute(AgentLiveabilityInfoCollection.class, "overall_stats_agentLiveabilityInfo.csv"));

				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.barMode(tech.tablesaw.plotly.components.Layout.BarMode.GROUP)
					.xAxis(Axis.builder().title("indexValue_longestTrip").build())
					.yAxis(Axis.builder().title("indexValue_lossTime").build())
					.build();

				viz.addTrace(ScatterTrace.builder(Plotly.INPUT, Plotly.INPUT).build(), ds.mapping()
						.x("indexValue_maxTravelTimePerTrip")
						.y("indexValue_relativeLossTime")
					//		.name("indexValue_relativeLossTime", ColorScheme.RdYlBu)

					//	viz.addTrace((Trace) new Line.LineBuilder()),

				);
			});

		// indicator specific dashboard values

		layout.row("Stats per indicator - Tiles")
			.el(Tile.class, (viz, data) -> {
				viz.title = "Longest Trip Indicator Details";
				viz.description = "Displays how many agents only have trips of less than 30 minutes per car and less than 60 minutes" +
					"pt or ride per day.";
				//+
				//"Also shows the average and mean distance that agents from the study area live away from green spaces.";
				viz.dataset = data.compute(AgentBasedTrafficQualityAnalysis.class, "travelTime_tiles_longestTrip.csv");
			})

			.el(Tile.class, (viz, data) -> {
				viz.title = "Loss Time Indicator Details";
				viz.description = "Displays how many agents spend a maximum of 20 % longer in traffic than they would in an empty network." +
					"Therefore all trips during the day are aggregated and compared to the travel time these trips would have, if they ware taken" +
					"in an empty network." +
					"Also shows the sum of all loss times in the scenario as well as the average and median loss time for agents from the study area.";
				viz.dataset = data.compute(AgentBasedTrafficQualityAnalysis.class, "travelTime_tiles_lossTime.csv");
			});

		layout.row("Stats per indicator - Diagram")
			.el(Plotly.class, (viz, data) -> {
				viz.title = "Longest Trip - Distribution of deviations";
				viz.description = "Displays the distribution of deviation for the longest trips. Zero represents the limit of 30 minutes for car" +
					"or 60 minutes for pt and ride trips. Values below 0 mean the longest trip is shorter than the mentioned durations, values over" +
					"zero mean theres an excess of the limit, with 1 meaning 100% over the limit, e.g. 60 min car trip or 120 pt trip.";

				// Add the dataset
				Plotly.DataSet dataset = viz.addDataset(data.compute(AgentBasedTrafficQualityAnalysis.class, "travelTime_stats_perAgent.csv"));

				viz.addTrace(HistogramTrace.builder(Plotly.INPUT).name("TQLongestTripDeviationFromLimit").nBinsX(200).build(),
					dataset.mapping() // Mapping verwenden, um Spalten aus dem Dataset zuzuordnen
						.x("TQLongestTripDeviationFromLimit"));

				// Define the layout for the plot
				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.xAxis(Axis.builder().title("Deviation").build())
					.yAxis(Axis.builder().title("Number of Agents").build())
					.build();
			})

			.el(Plotly.class, (viz, data) -> {
				viz.title = "Loss Time - Distribution of deviations";
				viz.description = "Displays the distribution of deviation for the loss time. Zero represents the aim of 20 % time loss comparing the actual travel" +
					"duration of the whole day with the free speed travel time of all those legs. Values below zero mean less than 20 % loss time, values above mean" +
					"more longer travel times compared to free speed times.";

				// Add the dataset
				Plotly.DataSet dataset = viz.addDataset(data.compute(AgentBasedTrafficQualityAnalysis.class, "travelTime_stats_perAgent.csv"));

				viz.addTrace(HistogramTrace.builder(Plotly.INPUT).name("TQLossTimeDeviationFromLimit").nBinsX(200).build(),
					dataset.mapping() // Mapping verwenden, um Spalten aus dem Dataset zuzuordnen
						.x("TQLossTimeDeviationFromLimit"));

				// Define the layout for the plot
				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.xAxis(Axis.builder().title("Deviation").build())
					.yAxis(Axis.builder().title("Number of Agents").build())
					.build();
			});


		layout.row("Indicator specific Index Value Maps")
			.el(XYTime.class, (viz, data) -> {
				viz.title = "Longest Trips Index Value Map";
				viz.description = "Here you can see the agents according to their traffic quality index values depicted on their home location";
				viz.height = 15.0;
				viz.radius = 15.0;
				viz.file = data.compute(AgentBasedTrafficQualityAnalysis.class, "travelTime_XYT_agentBasedLongestTrip.xyt.csv");

				String[] colors = {"#008000", "#6eaa5e", "#93bf85", "#f0a08a", "#d86043", "#c93c20", "#af230c", "#9b88d3", "#7863c4", "#4f3fb4", "#001ca4", "#191350"};
				viz.setBreakpoints(colors, -0.5, -0.25, 0.0, 0.25, 0.5, 0.75, 1.0, 2.0, 4.0, 8.0, 16.0);
			})

			.el(XYTime.class, (viz, data) -> {
				viz.title = "Loss Time Index Value Map";
				viz.description = "Here you can see the agents according to their traffic quality index values depicted on their home location";
				viz.height = 15.0;
				viz.radius = 15.0;
				viz.file = data.compute(AgentBasedTrafficQualityAnalysis.class, "travelTime_XYT_agentBasedLossTime.xyt.csv");

				String[] colors = {"#008000", "#6eaa5e", "#93bf85", "#f0a08a", "#d86043", "#c93c20", "#af230c", "#9b88d3", "#7863c4", "#4f3fb4", "#001ca4", "#191350"};
				viz.setBreakpoints(colors, -0.5, -0.25, 0.0, 0.25, 0.5, 0.75, 1.0, 2.0, 4.0, 8.0, 16.0);
			});

// AB HIER FEHLT FÜR JEDES LOSS TIME DIAGRAMM NOCH EIN LONGEST TRIP DIAGRAMM!

		layout.row("BarChart Modes")
			.el(Plotly.class, (viz, data)->{

			})

			.el(Plotly.class, (viz, data) -> {

				viz.title = "Mode Specific Loss Time";
				viz.description = "sum by mode";

				Plotly.DataSet ds = viz.addDataset(data.compute(AgentBasedTrafficQualityAnalysis.class, "travelTime_stats_LegsLossTimePerMode.csv"));
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

			});

		layout.row("ModesUsed (BarTrace) & Scatter Plot")
			.el(Plotly.class, (viz, data) -> {
				viz.title = "Histogram longest car trips";
				viz.description = "Histogram longest car trips";

				Plotly.DataSet dataset = viz.addDataset(data.compute(AgentBasedTrafficQualityAnalysis.class, "travelTime_histogram_longestCarTravel.csv"));

				// Define the layout for the plot
				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.barMode(tech.tablesaw.plotly.components.Layout.BarMode.GROUP)
					.xAxis(Axis.builder().title("Trip duration [min]").build())
					.yAxis(Axis.builder().title("Number of Trips").build())
					.build();

				viz.addTrace(HistogramTrace.builder(Plotly.INPUT).name("travTimePerAgent").build(),
					dataset.mapping() // Mapping verwenden, um Spalten aus dem Dataset zuzuordnen
						.x("travTimePerAgent"));
			})

			.el(Plotly.class, (viz, data) -> {
				viz.title = "Number of used modes";
				viz.description = "Number of the used mode";

				Plotly.DataSet dataset = viz.addDataset(data.compute(AgentBasedTrafficQualityAnalysis.class, "travelTime_bar_numberOfModes.csv"));

				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.xAxis(Axis.builder().title("Modes").build())
					.yAxis(Axis.builder().title("Number of modes").build())
					.build();
				viz.addTrace(BarTrace.builder(Plotly.OBJ_INPUT, Plotly.INPUT).orientation(BarTrace.Orientation.VERTICAL).build(), dataset.mapping()
					.x("mode")
					.y("count")
				);

			});

		layout.row("offen & Scatterplot")



			.el(Plotly.class, (viz, data) -> {
				viz.title = "Histogram longest ride trips";
				viz.description = "Histogram longest ride trips";

				Plotly.DataSet dataset = viz.addDataset(data.compute(AgentBasedTrafficQualityAnalysis.class, "travelTime_histogram_longestRideTravel.csv"));

				// Define the layout for the plot
				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.xAxis(Axis.builder().title("Trip duration [min]").build())
					.yAxis(Axis.builder().title("Number of Trips").build())
					.build();

				viz.addTrace(HistogramTrace.builder(Plotly.INPUT).name("travTimePerAgent").build(),
					dataset.mapping() // Mapping verwenden, um Spalten aus dem Dataset zuzuordnen
						.x("travTimePerAgent"));


			})




			.el(Plotly.class, (viz, data) -> {

				viz.title = "Scatter Plot Travel Time over Loss Time";
				viz.description = "Agent based analysis of travel time compared to loss time";

				Plotly.DataSet ds = viz.addDataset(data.compute(AgentBasedTrafficQualityAnalysis.class, "travelTime_stats_legsLossTime.csv"));

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


		layout.row("offen & prüfen ob doppelt")
			.el(Plotly.class, (viz, data) -> {
				viz.title = "Histogram longest Pt trips";
				viz.description = "Histogram longest Pt trips";

				Plotly.DataSet dataset = viz.addDataset(data.compute(AgentBasedTrafficQualityAnalysis.class, "travelTime_histogram_longestPtTravel.csv"));

				// Define the layout for the plot
				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.xAxis(Axis.builder().title("Trip duration [min]").build())
					.yAxis(Axis.builder().title("Number of Trips").build())
					.build();

				viz.addTrace(HistogramTrace.builder(Plotly.INPUT).name("travTimePerAgent").build(),
					dataset.mapping() // Mapping verwenden, um Spalten aus dem Dataset zuzuordnen
						.x("travTimePerAgent"));
			})

			.el(Plotly.class, (viz, data) -> {

				viz.title = "Modes Used";
				viz.description = "teleported modes result in 0 seconds loss time, ergo all solely bike and walk users are defined as true in their loss time dependent liveability ranking - here shown is the number of persons usimg each combination of modes";

				Plotly.DataSet ds = viz.addDataset(data.compute(AgentBasedTrafficQualityAnalysis.class, "travelTime_stats_perAgent.csv"))
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
			});

		layout.row("offen & Histogramm zeitliche Verteilung LossTime")
			.el(Plotly.class, (viz, data) -> {
				viz.title = "Histogram Departure Time longest trips";
				viz.description = "Histogram Departure Time longest trips";

				Plotly.DataSet dataset = viz.addDataset(data.compute(AgentBasedTrafficQualityAnalysis.class, "travelTime_histogram_longestTripDep.csv"));

				// Define the layout for the plot
				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.xAxis(Axis.builder().title("Trip departure [hh:mm:ss]").build())
					.yAxis(Axis.builder().title("Number of Departures").build())
					.build();

				viz.addTrace(BarTrace.builder(Plotly.OBJ_INPUT, Plotly.INPUT).orientation(BarTrace.Orientation.VERTICAL).build(), dataset.mapping()
					.x("timeInterval")
					.y("numberOfDepartures"));


			})

			.el(Plotly.class, (viz, data) -> {
				viz.title = "Loss Time per Quarter Hour";
				viz.description = "Sum of loss times (in seconds) for each 15-minute interval of the day";

				// Add the legs dataset
				Plotly.DataSet dataset = viz.addDataset(data.compute(AgentBasedTrafficQualityAnalysis.class, "travelTime_histogram_lossTimeDep.csv"));

				// Define the layout for the plot
				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.xAxis(Axis.builder().title("Time Interval").build())
					.yAxis(Axis.builder().title("Total Loss Time (s)").build())
					.build();

				viz.addTrace(BarTrace.builder(Plotly.OBJ_INPUT, Plotly.INPUT) // Verwenden von INPUT
						.name("Loss Time") // Name des Traces
						.build(),
					dataset.mapping() // Mapping verwenden, um Spalten aus dem Dataset zuzuordnen
						.x("timeInterval") // Spaltenname für die X-Achse (Zeitintervalle)
						.y("LossSeconds"));// Spaltenname für die Y-Achse (Verlustzeiten)

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

	}

}
