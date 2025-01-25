package org.matsim.dashboard;

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
	private final Path xytMapInputPath = ApplicationUtils.matchInput("agentRankingForMap.xyt.csv", getValidLiveabilityOutputDirectory());
//	private final List<String> dirs;
//	private final Integer noRuns;
//
//	public LiveabilitySummaryDashboard(List<String> dirs, Integer noRuns) {
//		this.dirs = dirs;
//		this.noRuns = noRuns;
//	}

	@Override
	public void configure(Header header, Layout layout) {

		header.title = "Liveability Ranking Summary";
		header.description = "Agents and their fulfillment of liveability indicators";

		layout.row("Test Ranking Map")
			.el(XYTime.class, (viz, data) -> {
				viz.title = "Ranking results map";
				viz.description = "Here you can see the agents according to their liveability ranking depicted on their home location";
				viz.height = 10.0;
				viz.file = data.compute(LiveabilitySummaryAnalysis.class, "agentRankingForMap.xyt.csv");

				//BREAKPOINTS MÜSSEN NOCH DEFINIERT WERDEN; RADIUS AUCH; COLOR RAMP AUCH
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

		layout.row("Indicator Overview")
			.el(Table.class, (viz, data) -> {
				viz.dataset = data.compute(LiveabilitySummaryAnalysis.class, "overviewIndicatorTable.csv");
				viz.height = 5.0;
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
