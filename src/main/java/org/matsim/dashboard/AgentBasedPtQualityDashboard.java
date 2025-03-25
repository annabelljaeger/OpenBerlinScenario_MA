package org.matsim.dashboard;

import org.matsim.analysis.AgentBasedPtQualityAnalysis;
import org.matsim.analysis.AgentLiveabilityInfoCollection;
import org.matsim.simwrapper.Dashboard;
import org.matsim.simwrapper.Header;
import org.matsim.simwrapper.Layout;
import org.matsim.simwrapper.viz.Plotly;
import org.matsim.simwrapper.viz.Tile;
import org.matsim.simwrapper.viz.XYTime;
import tech.tablesaw.plotly.components.Axis;
import tech.tablesaw.plotly.traces.HistogramTrace;
import tech.tablesaw.plotly.traces.ScatterTrace;

import static java.lang.Double.NaN;

public class AgentBasedPtQualityDashboard implements Dashboard {

	public double priority() {return -2;}

	public void configure(Header header, Layout layout) {

		header.title = "PT Quality";
		header.description = "The quality of the public transport system is vital to society. Reliable, comfortable and affordable transport systems " +
			"enable everyone to participate in society. Regular, intermodal public transport is the key to socially and environmentally sustainable regions.";


		// *********************************** PT Quality - Overall Visualization elements ************************************

		layout.row("PT Quality Overall Index Value Map")
			.el(XYTime.class, (viz, data) -> {
				viz.title = "PT Quality Index Value Map";
				viz.description = "The pt quality is represented by showing the deviation from a limit that the agent is below on all pt quality indicators. " +
					"It is therefore the maximum indicator index value of pt to car travel time ratio or maximum walk distance to a pt stop per agent, " +
					"which is displayed on the agent's home location";

				viz.file = data.compute(AgentBasedPtQualityAnalysis.class, "ptQuality_XYT_agentBasedPtQuality.csv");
				viz.height = 15.0;
				viz.radius = 15.0;
				String[] colors = {"#008000", "#6eaa5e", "#93bf85", "#f0a08a", "#d86043", "#c93c20", "#af230c", "#9b88d3", "#7863c4", "#4f3fb4", "#001ca4", "#191350","#0d0a28", "#363636"};
				viz.setBreakpoints(colors, -0.5, -0.25, 0.0, 0.25, 0.5, 0.75, 1.0, 2.0, 4.0, 8.0, 16.0, 128.0, NaN);
			});

		layout.row("overall index result pt quality")
			.el(Tile.class, (viz, data) -> {
				viz.title = "Public Transport Quality Index Value";
				viz.description = "The quality of pt in terms of liveability can be measured by its spatial and temporal accessibility. To be able to participate freely in society and " +
					"to have a reliable source of transportat, pt trips should not take more than twice as long as car journeys. Therefore, the ratio of car to pt travel time is a set of 2. " +
					"Furthermore the distance to walk to or from a pt stop should not exceed 500 m.";

				viz.dataset = data.compute(AgentBasedPtQualityAnalysis.class, "ptQuality_Tiles_PtQuality.csv");
				viz.height = 0.1;
			});

		layout.row("Scatterplot comparing the two indicators")
			.el(Plotly.class, (viz, data) -> {
				viz.title = "Scatter Plot Walk Distance To Pt Stop Over Pt To Car Travel Time Ratio";
				viz.description = "Comparison of the two indicator index values (deviation from the limit) per agent. Negative values mean that " +
					"the limit has been undershot, positive values that it has been exceeded.";

				Plotly.DataSet ds = viz.addDataset(data.compute(AgentLiveabilityInfoCollection.class, "overall_stats_agentLiveabilityInfo.csv"));
				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.barMode(tech.tablesaw.plotly.components.Layout.BarMode.GROUP)
					.xAxis(Axis.builder().title("Pt To Car Ratio Index Value").build())
					.yAxis(Axis.builder().title("Walk Distance to Pt Index Value").build())
					.build();
				viz.addTrace(ScatterTrace.builder(Plotly.INPUT, Plotly.INPUT).build(), ds.mapping()
					.x("indexValue_maxPtToCarRatio")
					.y("indexValue_maxWalkToPtDistance")
				);
			});


		// *********************************** Pt Quality - Indicator Specific Dashboard Values ************************************

		layout.row("Stats per indicator - Tiles")
			.el(Tile.class, (viz, data) -> {
				viz.title = "Pt to Car travel time ratio Indicator Details";
				viz.description = "Shows how many agents could make all their daily trips by public transport, with with each trip taking at most twice as long as a car. " +
					"The trips with the worst pt to car travel time ratio make up the considered deviation value, of which the mean and median are displayed.";
				viz.dataset = data.compute(AgentBasedPtQualityAnalysis.class, "ptQuality_Tiles_PtToCarRatio.csv");
			})

			.el(Tile.class, (viz, data) -> {
				viz.title = "Walk to Pt Indicator Details";
				viz.description = "Shows how many agents have to walk 500 m or less to or from a pt stop, if making all their daily trips by public transport. " +
					"The deviation from the leg with the maximum detected walk distance from the limit makes up the considered deviation value, of which the mean and median are displayed.";
				viz.dataset = data.compute(AgentBasedPtQualityAnalysis.class, "ptQuality_Tiles_MaxWalkToPt.csv");
			});

		layout.row("Stats per indicator - Diagram")
			.el(Plotly.class, (viz, data) -> {
				viz.title = "PT To Car Travel Time Ratio - Distribution of deviations";
				viz.description = "Shows the distribution of deviation for the pt to car travel time comparison. Zero represents the limit of double pt trip duration compared to car (limit = 2). " +
					"Values below 0 mean that the ratio is lower than 2, values above " +
					"zero mean that the limit has been exceeded, with 1 meaning 100% over the limit (pt trip is four times as long as the car trip, e.g. 40 min pt travel time compared to 10 min car travel time).";

				Plotly.DataSet dataset = viz.addDataset(data.compute(AgentBasedPtQualityAnalysis.class, "ptQuality_stats_perAgent.csv"));
				viz.addTrace(HistogramTrace.builder(Plotly.INPUT).name("indexValue_PtToCarTravelTimeRatioPerAgent").nBinsX(200).build(),
					dataset.mapping()
						.x("indexValue_PtToCarTravelTimeRatioPerAgent"));
				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.xAxis(Axis.builder().title("Deviation").build())
					.yAxis(Axis.builder().title("Number of Agents").build())
					.build();
			})

			.el(Plotly.class, (viz, data) -> {
				viz.title = "Maximum Walking Distance To PT Stop - Distribution of deviations";
				viz.description = "Shows the distribution of deviation for the walk distance to and from pt stops. Zero represents the limit of 500 m walk distance. " +
					"Each leg is therefore viewed separately and the longest of all distances chosen. Deviation values below zero mean less than 500 m max. walk distance, values above mean " +
					"longer walk distances to or from pt.";

				Plotly.DataSet dataset = viz.addDataset(data.compute(AgentBasedPtQualityAnalysis.class, "ptQuality_stats_perAgent.csv"));
				viz.addTrace(HistogramTrace.builder(Plotly.INPUT).name("indexValue_MaxWalkToPtPerAgent").nBinsX(200).build(),
					dataset.mapping()
						.x("indexValue_MaxWalkToPtPerAgent"));
				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.xAxis(Axis.builder().title("Deviation").build())
					.yAxis(Axis.builder().title("Number of Agents").build())
					.build();
			});

		layout.row("Indicator Index Value Map")
			.el(XYTime.class, (viz, data) -> {
				viz.title = "Pt To Car Travel Time Ratio Index Value Map";
				viz.description = "The map shows each agent's deviation value from the limit of 2 for pt to car travel time ratio displayed at their home location.";

				viz.file = data.compute(AgentBasedPtQualityAnalysis.class, "ptQuality_XYT_PtToCarRatioPerAgent.csv");
				viz.height = 15.0;
				viz.radius = 15.0;
				String[] colors = {"#008000", "#6eaa5e", "#93bf85", "#f0a08a", "#d86043", "#c93c20", "#af230c", "#9b88d3", "#7863c4", "#4f3fb4", "#001ca4", "#191350","#0d0a28", "#363636"};
				viz.setBreakpoints(colors, -0.5, -0.25, 0.0, 0.25, 0.5, 0.75, 1.0, 2.0, 4.0, 8.0, 16.0, 128.0, NaN);
			})

			.el(XYTime.class, (viz, data) -> {
				viz.title = "Walk to Pt Index Value Map";
				viz.description = "The map shows each agent's deviation value from the 500 m walk distance limit displayed at their home location";

				viz.file = data.compute(AgentBasedPtQualityAnalysis.class, "ptQuality_XYT_maxWalkToPTPerAgent.csv");
				viz.height = 15.0;
				viz.radius = 15.0;
				String[] colors = {"#008000", "#6eaa5e", "#93bf85", "#f0a08a", "#d86043", "#c93c20", "#af230c", "#9b88d3", "#7863c4", "#4f3fb4", "#001ca4", "#191350","#0d0a28", "#363636"};
				viz.setBreakpoints(colors, -0.5, -0.25, 0.0, 0.25, 0.5, 0.75, 1.0, 2.0, 4.0, 8.0, 16.0, 128.0, NaN);
			});

		layout.row("EcoMobility to Car Ratio Tiles")
			.el(Tile.class, (viz, data) -> {
				viz.title = "EcoMobility To Car Travel Time Ratio Deviation";
				viz.description = "This additional analysis extends the pt situation by walk and bike trips, representing environmentally friendly modes of transportation. " +
					"Compared to the pt-car travel time ratio, this indicator results in bike trips, that are faster than pt being included in the travel time comparison. " +
					"This reflects that long pt trips with short euclidean distances might be more quickly on foot or by bike, which is reflected here. This additional analysis could be a first " +
					"indicator for a possible extension of this index in the context of walkability and bikeability. The values below show, how many agents would have a travel time comparison value " +
					"of 2 or less if the fastest of pt, walk or bike are chosen, compared to car.";
				viz.dataset = data.compute(AgentBasedPtQualityAnalysis.class, "ptQuality_Tiles_EcoMobilityRatio.csv");
				viz.height = 0.1;
			});

		layout.row("EcoMobility to Car Index Value Map")
			.el(XYTime.class, (viz, data) -> {
				viz.title = "EcoMobility to Car travel time ratio Index Value Map";
				viz.description = "The map shows the deviation of trips made by environmentally friendly modes compared to car trips in terms of their travel time, with a limit of 2.";

				viz.file = data.compute(AgentBasedPtQualityAnalysis.class, "ptQuality_XYT_EcoMobilityToCarRatioPerAgent.csv");
				viz.height = 15.0;
				viz.radius = 15.0;
				String[] colors = {"#008000", "#6eaa5e", "#93bf85", "#f0a08a", "#d86043", "#c93c20", "#af230c", "#9b88d3", "#7863c4", "#4f3fb4", "#001ca4", "#191350","#0d0a28", "#363636"};
				viz.setBreakpoints(colors, -0.5, -0.25, 0.0, 0.25, 0.5, 0.75, 1.0, 2.0, 4.0, 8.0, 16.0, 128.0, NaN);
			});
	}
}
