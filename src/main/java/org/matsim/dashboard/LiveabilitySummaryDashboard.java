package org.matsim.dashboard;

import org.matsim.analysis.AgentLiveabilityInfoCollection;
import org.matsim.analysis.LiveabilitySummaryAnalysis;
import org.matsim.simwrapper.Dashboard;
import org.matsim.simwrapper.Header;
import org.matsim.simwrapper.Layout;
import org.matsim.simwrapper.viz.*;

public class LiveabilitySummaryDashboard implements Dashboard {

	public double priority() {return 2;}

	@Override
	public void configure(Header header, Layout layout) {

		header.title = "Liveability-Index Summary";
		header.description = "This index checks the compliance of agents according to liveability indicators. The overall index, which is the proportion of agents " +
			"that meet the benchmark values (limits) for all indicators, can be found. This dashboard also includes a summary of the dimension and indicator index values.";

		// map as xyt-Map - better: SHP for more interactive use of the data
		layout.row("Overall Index Value Map")
			.el(XYTime.class, (viz, data) -> {
				viz.title = "Overall Index Value Map";
				viz.description = "The agent's overall index value represents the deviation from a limit that the agent is below on all indicators. It is therefore " +
					"the maximum indicator index value per agent and is displayed on the agent's home location.";
				viz.height = 10.0;
				viz.radius = 15.0;
				viz.file = data.compute(LiveabilitySummaryAnalysis.class, "overall_XYT_AgentRankingForSummary.xyt.csv");

				String[] colors = {"#008000", "#6eaa5e", "#93bf85", "#f0a08a", "#d86043", "#c93c20", "#af230c", "#9b88d3", "#7863c4", "#4f3fb4", "#001ca4", "#191350"};
				viz.setBreakpoints(colors, -0.5, -0.25, 0.0, 0.25, 0.5, 0.75, 1.0, 2.0, 4.0, 8.0, 16.0);
			});

		layout.row("Overall Ranking")
			.el(Tile.class, (viz, data) -> {
				viz.title = "Overall Liveability-Index Value";
				viz.description = "The index value indicates what percentage of the agents in the study area fulfil all the liveability indicators by achieving at least the limit.";
				viz.dataset = data.compute(LiveabilitySummaryAnalysis.class, "overall_tiles_ranking.csv");
				viz.height = 0.1;
			});

		layout.row("ScoringTiles")
			.el(Tile.class, (viz, data) -> {
				viz.title = "Target Dimensions Index Values";
				viz.description = "The index values for each calculated target dimension can be found here. These values indicate what percentage of the agents in the study area, " +
					"for which the calculation was successful, fulfil all the indicator limits for that dimension.";
				viz.dataset = data.compute(AgentLiveabilityInfoCollection.class, "overall_tiles_indexDimensionValues.csv");
				viz.height = 0.1;
			});

		layout.row("WorstIndicator")
			.el(Tile.class, (viz, data) -> {
				viz.title = "Overall Best and Worst Indicators";
				viz.description = "To better understand the influence of each indicator on the overall index, the indicator with the best and worst deviation values for each agent is shown. " +
					"For each agent, the name of the indicator with the highest and lowest of all indicator index values (excluding null values) is counted and the indicator which was the best or worst indicator most often is shown, " +
					"together with the percentage of agents for which this is the highest or lowest indicator index value.";
				viz.dataset = data.compute(LiveabilitySummaryAnalysis.class, "overall_tiles_highestLowestIndicator.csv");
				viz.height = 0.1;
			});

		layout.row("Indicator Overview")
			.el(Table.class, (viz, data) -> {
				viz.title = "Liveability-Index Indicator Overview";
				viz.dataset = data.compute(AgentLiveabilityInfoCollection.class, "overall_stats_indicatorValues.csv");
				viz.height = 4.0;
			});
	}
}
