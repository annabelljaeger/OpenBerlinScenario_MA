package org.matsim.dashboard;

import org.matsim.analysis.AgentBasedAccidentsAnalysis;
import org.matsim.analysis.AgentBasedGreenSpaceAnalysis;
import org.matsim.simwrapper.Dashboard;
import org.matsim.simwrapper.Header;
import org.matsim.simwrapper.Layout;
import org.matsim.simwrapper.viz.TextBlock;
import org.matsim.simwrapper.viz.Tile;

public class AgentBasedSafetyDashboard implements Dashboard {

	public double priority(){return -1;}

	@Override
	public void configure(Header header, Layout layout) {

		header.title = "Safety";
		header.description = "1. Accidents - cost/risk of accidents per agent";

		layout.row("overall ranking result safety")
			.el(Tile.class, (viz, data) -> {

				viz.title = "Safety Ranking Value";
				viz.description = "for now: Accidents cost/risk via accidents contrib broken down to agents according to their used links.";

				viz.dataset = data.compute(AgentBasedAccidentsAnalysis.class, "accidents_RankingValue.csv");
				viz.height = 0.1;

			});

		layout.row("offene Tasks")
			.el(TextBlock.class, (viz, data) -> {
				viz.title = "Open Tasks";
				viz.description = "diese Diagramme/Infos würde ich gerne noch erstellen - dauert aber gerade zu lang";
				viz.content = "\t1. ??\n" +
					"\t2. ??\n"+
					"\t3. Zusätzliche Infos-Tabelle\n" +
					"\t\ta. ???\n" +
					"\t\tb. ??\n" +
					"\t\tc. ??\n";
			});

	}
}
