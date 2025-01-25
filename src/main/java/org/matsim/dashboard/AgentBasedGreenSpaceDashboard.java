package org.matsim.dashboard;

import org.matsim.analysis.AgentBasedGreenSpaceAnalysis;
import org.matsim.analysis.AgentBasedGreenSpaceAnalysisTest;
import org.matsim.analysis.AgentBasedLossTimeAnalysis;
import org.matsim.simwrapper.Dashboard;
import org.matsim.simwrapper.Header;
import org.matsim.simwrapper.Layout;
import org.matsim.simwrapper.viz.Bar;
import org.matsim.simwrapper.viz.TextBlock;
import org.matsim.simwrapper.viz.Tile;

public class AgentBasedGreenSpaceDashboard implements Dashboard {

	public double priority(){return -3;}

	@Override
	public void configure(Header header, Layout layout) {

		header.title = "Green Space";
		header.description = "A key indicator of spatial justice is the availability and accessibility of " +
			"relaxing green spaces for relaxation and stress relief. Therefore the walking distance to the nearest " +
			"green space as well as the amount of green space in this nearest area per Person is analysed.";

		layout.row("overall ranking result green space")
			.el(Tile.class, (viz, data) -> {

				viz.title = "Green Space Ranking Value";
				viz.description = "According to the 'Deutsche Städtetag' every person should have accessibility to a green space " +
					"of at least 1 ha of size within 500 m footpath of their home location. Furthermore those green spaces should " +
					"offer at least 6 m² per person assigned to it (here: always choice of the nearest green space to the home location).";

				viz.dataset = data.compute(AgentBasedGreenSpaceAnalysis.class, "greenSpace_RankingValue.csv");
				viz.height = 0.1;

			});

		layout.row("offene Tasks")
			.el(TextBlock.class, (viz, data) -> {
				viz.title = "Open Tasks";
				viz.description = "diese Diagramme/Infos würde ich gerne noch erstellen - dauert aber gerade zu lang";
				viz.content = "\t1. Tiles erweitern: Prozentsatz der nah genug Leute und Prozentsatz der utilization ausreichend Leute\n" +
					"\t2. Median statt Mittelwert bei Distanz und Utilization (oder alle Infos in Tabelle und in Tiles nur Median\n";//+
//					"\t7. Zusätzliche Infos-Tabelle\n" +
//					"\t\ta. Wie viele Routen werden gar nicht richtig gefunden und somit geskippt (LossTime 0)?\n" +
//					"\t\tb. Wie viele Agenten nutzen nur teleportierte Modi und erhalten somit automatisch den True-Wert?\n" +
//					"\t\tc. Wie viele Agenten haben über X%/xMin (z.B. 100%/30 min) Verlustzeit - evtl. 2 Angaben, einmal > 100% und einmal mehr als 1 Std.\n";

			});
	}
}
