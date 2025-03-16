package org.matsim.dashboard;

import org.matsim.analysis.AgentBasedAccessibilityAnalysis;
import org.matsim.simwrapper.Dashboard;
import org.matsim.simwrapper.Header;
import org.matsim.simwrapper.Layout;
import org.matsim.simwrapper.viz.Tile;

public class AgentBasedPtQualityDashboard implements Dashboard {

	public double priority() {return -2;}

	public void configure(Header header, Layout layout) {

		header.title = "PT Quality and Accessibility";
		header.description = "1. PT Quality (Reisezeitverhältnis ÖV/ MIV and 2. Accessibility per Agent According to the grid";

		layout.row("overall ranking results accessibillity")
			.el(Tile.class, (viz, data) -> {

				viz.title = "PT Quality and Accessibility Ranking Value";
				viz.description = "...";

				viz.dataset = data.compute(AgentBasedAccessibilityAnalysis.class, "ptAccessibility_RankingValue.csv");
				viz.height = 0.1;
			});

	}

}
