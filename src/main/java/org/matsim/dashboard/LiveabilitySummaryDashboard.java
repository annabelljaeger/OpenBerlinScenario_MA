package org.matsim.dashboard;

import org.matsim.analysis.AgentBasedGreenSpaceAnalysis;
import org.matsim.analysis.LiveabilitySummaryAnalysis;
import org.matsim.application.ApplicationUtils;
import org.matsim.simwrapper.Dashboard;
import org.matsim.simwrapper.Header;
import org.matsim.simwrapper.Layout;
import org.matsim.simwrapper.viz.*;

import java.nio.file.Path;
import java.util.List;

import org.matsim.simwrapper.*;

import static org.matsim.dashboard.RunLiveabilityDashboard.getValidLiveabilityOutputDirectory;


public class LiveabilitySummaryDashboard implements Dashboard {

	public double priority() {return 2;}

	private static final String RANKING = "ranking";
//	private final Path xytMapInputPath = ApplicationUtils.matchInput("agentRankingForMap.xyt.csv", getValidLiveabilityOutputDirectory());
//	private final List<String> dirs;
//	private final Integer noRuns;
//
//	public LiveabilitySummaryDashboard(List<String> dirs, Integer noRuns) {
//		this.dirs = dirs;
//		this.noRuns = noRuns;
//	}

	@Override
	public void configure(Header header, Layout layout) {

		header.title = "Liveability-Index Summary";
		header.description = "Agents and their fulfillment of liveability indicators";

		// map as xyt-Map - better: SHP for more interactive use of the data
		layout.row("Overall Index Value Map")
			.el(XYTime.class, (viz, data) -> {
				viz.title = "Overall index value map";
				viz.description = "Here you can see the agents according to their overall ranking index depicted on their home location";
				viz.height = 10.0;
				viz.radius = 15.0;
				viz.file = data.compute(LiveabilitySummaryAnalysis.class, "agentRankingForSummaryMap.xyt.csv");

				String[] colors = {"#008000", "#6eaa5e", "#93bf85", "#f0a08a", "#d86043", "#c93c20", "#af230c", "#9b88d3", "#7863c4", "#4f3fb4", "#001ca4", "#191350"};
				viz.setBreakpoints(colors, -0.5, -0.25, 0.0, 0.25, 0.5, 0.75, 1.0, 2.0, 4.0, 8.0, 16.0);
			});


		layout.row("Overall Ranking")
			.el(Tile.class, (viz, data) -> {
				viz.dataset = data.compute(LiveabilitySummaryAnalysis.class, "overallRankingTile.csv");
				viz.height = 0.1;
			});

		layout.row("ScoringTiles")
			.el(Tile.class, (viz, data) -> {
				viz.dataset = data.output("analysis/liveability/summaryTiles.csv");
				viz.height = 0.1; //})
			});

		layout.row("WorstIndicator")
			.el(Tile.class, (viz, data) -> {
				viz.dataset = data.output("analysis/liveability/overallWorstIndexValueTile.csv");
				viz.height = 0.1; //})
			});

		layout.row("Indicator Overview")
			.el(Table.class, (viz, data) -> {
				viz.dataset = data.compute(LiveabilitySummaryAnalysis.class, "overviewIndicatorTable.csv");
				viz.height = 2.0;
			});
	}
}
/*
Library for Simwrapper Dashboard Outputs

	layout.row("second")
		.el(Plotly.class, (viz, data) -> {
			viz.title = "Stuck Agents";
			viz.description = "per Mode";

			Plotly.DataSet ds = viz.addDataset(data.compute(StuckAgentAnalysis.class, "stuck_agents_per_mode.csv"));
			viz.addTrace(PieTrace.builder(Plotly.OBJ_INPUT, Plotly.INPUT).build(), ds.mapping()
				.text("Mode")
				.x("Agents")
			);
		})
		.el(Table.class, (viz, data) -> {
			viz.title = "Stuck Agents";
			viz.description = "per Mode";
			viz.dataset = data.compute(StuckAgentAnalysis.class, "stuck_agents_per_mode.csv");
		});

	layout.row("third")
		.el(Bar.class, (viz, data) -> {
			viz.title = "Stuck Agents";
			viz.description = "per hour";

			viz.stacked = true;
			viz.dataset = data.compute(StuckAgentAnalysis.class, "stuck_agents_per_hour.csv");
			viz.x = "hour";
			viz.xAxisName = "Hour";
			viz.yAxisName = "# Stuck";
			viz.columns = List.of("pt","walk");
		})
		.el(Table.class, (viz, data) -> {
			viz.title = "Stuck Agents";
			viz.description = "per hour";
			viz.dataset = data.compute(StuckAgentAnalysis.class, "stuck_agents_per_hour.csv");
		});

	layout.row("four").el(Table.class, (viz, data) -> {
		viz.title = "Stuck Agents";
		viz.description = "per Link (Top 20)";
		viz.dataset = data.compute(StuckAgentAnalysis.class, "stuck_agents_per_link.csv");
	});
}
}
*/
