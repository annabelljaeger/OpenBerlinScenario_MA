package org.matsim.dashboard;

import org.matsim.analysis.AgentBasedTrafficQualityAnalysis;
import org.matsim.analysis.AgentLiveabilityInfoCollection;
import org.matsim.simwrapper.Dashboard;
import org.matsim.simwrapper.Header;
import org.matsim.simwrapper.Layout;
import org.matsim.simwrapper.viz.*;
import tech.tablesaw.plotly.components.Axis;
import tech.tablesaw.plotly.components.Marker;
import tech.tablesaw.plotly.traces.BarTrace;
import tech.tablesaw.plotly.traces.HistogramTrace;
import tech.tablesaw.plotly.traces.ScatterTrace;
import static java.lang.Double.NaN;

public class AgentBasedTrafficQualityDashboard implements Dashboard {

	public double priority(){return -1;}

	public void configure(Header header, Layout layout) {

		header.title = "Traffic Quality";
		header.description = "A key indicator of the quality of the transport system is its efficiency. The time spent in traffic " +
			"and especially in congested traffic have a major influence on the health and behaviour of those involved. Therefore the " +
			"absolute travel time spent travelling as well as the loss time during those trips is analysed and displayed in the following dashboard.";


	// *********************************** Traffic Quality - Overall Visualization elements ************************************

		// map as xyt-Map displaying the worst deviation from either of the two indicator deviation values as the dimension index value
		layout.row("Traffic Quality Index Value Map")
			.el(XYTime.class, (viz, data) -> {
				viz.title = "Traffic Quality Index Value Map";
				viz.description = "The agent's traffic quality index value represents the deviation from a limit that the agent is below on all traffic quality indicators. " +
					"It is therefore the maximum indicator index value of longest trip or loss time per agent and is displayed on the agent's home location.";

				viz.file = data.compute(AgentBasedTrafficQualityAnalysis.class, "travelTime_XYT_agentBasedTrafficQuality.xyt.csv");
				viz.height = 15.0;
				viz.radius = 15.0;
				String[] colors = {"#008000", "#6eaa5e", "#93bf85", "#f0a08a", "#d86043", "#c93c20", "#af230c", "#9b88d3", "#7863c4", "#4f3fb4", "#001ca4", "#191350","#0d0a28", "#363636"};
				viz.setBreakpoints(colors, -0.5, -0.25, 0.0, 0.25, 0.5, 0.75, 1.0, 2.0, 4.0, 8.0, 16.0, 128.0, NaN);
			});

		layout.row("overall index result traffic quality")
			.el(Tile.class, (viz, data) -> {
				viz.title = "Traffic Quality Index Value";
				viz.description = "Traffic quality in terms of liveability tends to be measured in the time spent in traffic. " +
					"People are prone to health risks, stress and aggressive behaviour when spending more than 30 minutes driving a car " +
					"or 60 minutes on public transport or ride trips. Also time lost in traffic of more than 20 % of the total travel time " +
					"can have these effects and is therefore identified with these traffic quality indicators.";

				viz.dataset = data.compute(AgentBasedTrafficQualityAnalysis.class, "travelTime_tiles_overall.csv");
				viz.height = 0.1;
			});

		layout.row("Scatterplot comparing the two indicators")
			.el(Plotly.class, (viz, data) -> {
				viz.title = "Scatter Plot Loss Time over Longest Trip";
				viz.description = "Comparison of the two indicator index values (deviation from the limit) per agent. Negative values mean that " +
					"the limit has been undershot, positive values that it has been exceeded.";

				Plotly.DataSet ds = viz.addDataset(data.compute(AgentLiveabilityInfoCollection.class, "overall_stats_agentLiveabilityInfo.csv"));
				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.barMode(tech.tablesaw.plotly.components.Layout.BarMode.GROUP)
					.xAxis(Axis.builder().title("Longest Trip Index Value").build())
					.yAxis(Axis.builder().title("Loss Time Index Value").build())
					.build();
				viz.addTrace(ScatterTrace.builder(Plotly.INPUT, Plotly.INPUT).build(), ds.mapping()
						.x("indexValue_maxTravelTimePerTrip")
						.y("indexValue_relativeLossTime")
				);
			});


	// *********************************** Traffic Quality - Indicator Specific Dashboard Values ************************************

		layout.row("Stats per indicator - Tiles")
			.el(Tile.class, (viz, data) -> {
				viz.title = "Longest Trip Indicator Details";
				viz.description = "Shows how many agents only have trips of less than 30 minutes per car and less than 60 minutes" +
					"pt or ride per day. The trips with the worst trip duration to limit ratio make up the considered deviation value, " +
					"of which the median per mode is displayed.";
				viz.dataset = data.compute(AgentBasedTrafficQualityAnalysis.class, "travelTime_tiles_longestTrip.csv");
			})

			.el(Tile.class, (viz, data) -> {
				viz.title = "Loss Time Indicator Details";
				viz.description = "Shows how many agents spend a maximum of 20 % longer in traffic than they would in an empty network. Therefore all trips " +
					"during the day are aggregated and compared to the travel time these trips would have, if they ware taken in an empty network. " +
					"Also the sum of all loss times in the scenario (sample size adjusted) as well as the average and median loss time for agents from the study area are displayed.";
				viz.dataset = data.compute(AgentBasedTrafficQualityAnalysis.class, "travelTime_tiles_lossTime.csv");
			});

		layout.row("Stats per indicator - Diagram")
			.el(Plotly.class, (viz, data) -> {
				viz.title = "Longest Trip - Distribution of deviations";
				viz.description = "Shows the distribution of deviation for the longest trips. Zero represents the limit of 30 minutes for car trips " +
					"or 60 minutes for pt and ride trips. Values below 0 mean that the longest trip is shorter than the mentioned durations, values above " +
					"zero mean that the limit has been exceeded, with 1 meaning 100% over the limit, e.g. 60 min car trip or 120 min pt trip.";

				Plotly.DataSet dataset = viz.addDataset(data.compute(AgentBasedTrafficQualityAnalysis.class, "travelTime_stats_perAgent.csv"));
				viz.addTrace(HistogramTrace.builder(Plotly.INPUT).name("TQLongestTripDeviationFromLimit").nBinsX(200).build(),
					dataset.mapping()
						.x("TQLongestTripDeviationFromLimit"));
				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.xAxis(Axis.builder().title("Deviation").build())
					.yAxis(Axis.builder().title("Number of Agents").build())
					.build();
			})

			.el(Plotly.class, (viz, data) -> {
				viz.title = "Loss Time - Distribution of deviations";
				viz.description = "Shows the distribution of deviation for the loss time. Zero represents the limit of 20 % time loss comparing the actual travel " +
					"duration of the whole day with the free speed travel time of all those legs. Values below zero mean less than 20 % loss time, values above mean " +
					"longer travel times compared to free speed times.";

				Plotly.DataSet dataset = viz.addDataset(data.compute(AgentBasedTrafficQualityAnalysis.class, "travelTime_stats_perAgent.csv"));
				viz.addTrace(HistogramTrace.builder(Plotly.INPUT).name("TQLossTimeDeviationFromLimit").nBinsX(200).build(),
					dataset.mapping()
						.x("TQLossTimeDeviationFromLimit"));
				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.xAxis(Axis.builder().title("Deviation").build())
					.yAxis(Axis.builder().title("Number of Agents").build())
					.build();
			});

		layout.row("Indicator specific Index Value Maps")
			.el(XYTime.class, (viz, data) -> {
				viz.title = "Longest Trip Index Value Map";
				viz.description = "The map shows each agent's deviation value from the 30 min for car and 60 min for pt and ride limits displayed at their home location.";

				viz.file = data.compute(AgentBasedTrafficQualityAnalysis.class, "travelTime_XYT_agentBasedLongestTrip.xyt.csv");
				viz.height = 15.0;
				viz.radius = 15.0;
				String[] colors = {"#008000", "#6eaa5e", "#93bf85", "#f0a08a", "#d86043", "#c93c20", "#af230c", "#9b88d3", "#7863c4", "#4f3fb4", "#001ca4", "#191350","#0d0a28", "#363636"};
				viz.setBreakpoints(colors, -0.5, -0.25, 0.0, 0.25, 0.5, 0.75, 1.0, 2.0, 4.0, 8.0, 16.0, 128.0, NaN);
			})

			.el(XYTime.class, (viz, data) -> {
				viz.title = "Loss Time Index Value Map";
				viz.description = "The map shows each agent's deviation value from the 20 % loss time limit displayed at their home location.";

				viz.file = data.compute(AgentBasedTrafficQualityAnalysis.class, "travelTime_XYT_agentBasedLossTime.xyt.csv");
				viz.height = 15.0;
				viz.radius = 15.0;
				String[] colors = {"#008000", "#6eaa5e", "#93bf85", "#f0a08a", "#d86043", "#c93c20", "#af230c", "#9b88d3", "#7863c4", "#4f3fb4", "#001ca4", "#191350","#0d0a28", "#363636"};
				viz.setBreakpoints(colors, -0.5, -0.25, 0.0, 0.25, 0.5, 0.75, 1.0, 2.0, 4.0, 8.0, 16.0, 128.0, NaN);
			});

		layout.row("Detail Diagrams 1/4")
			.el(Plotly.class, (viz, data) -> {
				viz.title = "Histogram Longest Car Trips";
				viz.description = "Shows the duration of the trips of those agents whose worst longest trip to limit deviation was found to be for a car trip.";

				Plotly.DataSet dataset = viz.addDataset(data.compute(AgentBasedTrafficQualityAnalysis.class, "travelTime_histogram_longestCarTravel.csv"));
				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.barMode(tech.tablesaw.plotly.components.Layout.BarMode.GROUP)
					.xAxis(Axis.builder().title("Trip duration [min]").build())
					.yAxis(Axis.builder().title("Number of Trips").build())
					.build();
				viz.addTrace(HistogramTrace.builder(Plotly.INPUT).name("travTimePerAgent").build(),
					dataset.mapping()
						.x("travTimePerAgent"));
			})

			.el(Plotly.class, (viz, data) -> {
				viz.title = "Mode Specific Loss Time";
				viz.description = "Shows the sample size adjusted sum of all loss times per mode.";

				Plotly.DataSet ds = viz.addDataset(data.compute(AgentBasedTrafficQualityAnalysis.class, "travelTime_stats_LegsLossTimePerMode.csv"));
				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.xAxis(Axis.builder().title("Mode").build())
					.yAxis(Axis.builder().title("Loss Time [min]").build())
					.build();
				viz.addTrace(BarTrace.builder(Plotly.OBJ_INPUT, Plotly.INPUT).orientation(BarTrace.Orientation.VERTICAL).build(), ds.mapping()
					.x("mode")
					.y("cumulative_loss_time")
				);
			});

		layout.row("Detail Diagrams 2/4")
			.el(Plotly.class, (viz, data) -> {
				viz.title = "Histogram Longest Pt Trips";
				viz.description = "Shows the duration of the trips of those agents whose worst longest trip to limit deviation was found to be for a pt trip.";

				Plotly.DataSet dataset = viz.addDataset(data.compute(AgentBasedTrafficQualityAnalysis.class, "travelTime_histogram_longestPtTravel.csv"));
				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.xAxis(Axis.builder().title("Trip duration [min]").build())
					.yAxis(Axis.builder().title("Number of Trips").build())
					.build();
				viz.addTrace(HistogramTrace.builder(Plotly.INPUT).name("travTimePerAgent").build(),
					dataset.mapping()
						.x("travTimePerAgent"));
			})

			.el(Plotly.class, (viz, data) -> {
				viz.title = "Number Of Used Modes";
				viz.description = "Number of legs travelled by each mode.";

				Plotly.DataSet dataset = viz.addDataset(data.compute(AgentBasedTrafficQualityAnalysis.class, "travelTime_bar_numberOfModes.csv"));
				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.xAxis(Axis.builder().title("Modes").build())
					.yAxis(Axis.builder().title("Number of modes").build())
					.build();
				viz.addTrace(BarTrace.builder(Plotly.OBJ_INPUT, Plotly.INPUT).orientation(BarTrace.Orientation.VERTICAL)
					.build(), dataset.mapping()
					.x("mode")
					.y("count")
				);
			});

		layout.row("Detail Diagrams 3/4")
			.el(Plotly.class, (viz, data) -> {
				viz.title = "Histogram Longest Ride trips";
				viz.description = "Shows the duration of the trips of those agents whose worst longest trip to limit deviation was found to be for a ride trip.";

				Plotly.DataSet dataset = viz.addDataset(data.compute(AgentBasedTrafficQualityAnalysis.class, "travelTime_histogram_longestRideTravel.csv"));
				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.xAxis(Axis.builder().title("Trip duration [min]").build())
					.yAxis(Axis.builder().title("Number of Trips").build())
					.build();
				viz.addTrace(HistogramTrace.builder(Plotly.INPUT).name("travTimePerAgent").build(),
					dataset.mapping()
						.x("travTimePerAgent"));
			})

			.el(Plotly.class, (viz, data) -> {
				viz.title = "Scatter Plot Loss Time over Travel Time";
				viz.description = "Agent based analysis of travel time compared to loss time";

				Plotly.DataSet ds = viz.addDataset(data.compute(AgentBasedTrafficQualityAnalysis.class, "travelTime_stats_legsLossTime.csv"));

				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.barMode(tech.tablesaw.plotly.components.Layout.BarMode.GROUP)
					.xAxis(Axis.builder().title("Travel Time [min]").build())
					.yAxis(Axis.builder().title("Loss Time [min]").build())
					.build();
				viz.addTrace(ScatterTrace.builder(Plotly.INPUT, Plotly.INPUT).build(), ds.mapping()
						.x("trav_time")
						.y("loss_time")
				);
			});

		layout.row("Detail Diagrams 4/4")
			.el(Plotly.class, (viz, data) -> {
				viz.title = "Histogram Departure Time Of Longest Trips";
				viz.description = "Histogram Departure Time longest trips";

				Plotly.DataSet dataset = viz.addDataset(data.compute(AgentBasedTrafficQualityAnalysis.class, "travelTime_histogram_longestTripDep.csv"));
				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.xAxis(Axis.builder().title("Trip departure [hh:mm:ss]").build())
					.yAxis(Axis.builder().title("Number of Departures").build())
					.build();
				viz.addTrace(BarTrace.builder(Plotly.OBJ_INPUT, Plotly.INPUT).orientation(BarTrace.Orientation.VERTICAL).build(), dataset.mapping()
					.x("timeInterval")
					.y("numberOfDepartures"));
			})

			.el(Plotly.class, (viz, data) -> {
				viz.title = "Histogramm Occurrences Of Loss Time";
				viz.description = "Sum of loss times (in seconds) for each 15-minute interval of the day";

				Plotly.DataSet dataset = viz.addDataset(data.compute(AgentBasedTrafficQualityAnalysis.class, "travelTime_histogram_lossTimeDep.csv"));
				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.xAxis(Axis.builder().title("Time Interval").build())
					.yAxis(Axis.builder().title("Total Loss Time [min]").build())
					.build();

				viz.addTrace(BarTrace.builder(Plotly.OBJ_INPUT, Plotly.INPUT) // Verwenden von INPUT
						.name("Loss Time") // Name des Traces
						.build(),
					dataset.mapping() // Mapping verwenden, um Spalten aus dem Dataset zuzuordnen
						.x("timeInterval") // Spaltenname für die X-Achse (Zeitintervalle)
						.y("LossMinutes"));// Spaltenname für die Y-Achse (Verlustzeiten)
			});
	}
}
