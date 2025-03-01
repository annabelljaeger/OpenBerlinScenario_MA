package org.matsim.dashboard;

import org.jaitools.numeric.Histogram;
import org.matsim.analysis.*;
import org.matsim.application.ApplicationUtils;
import org.matsim.application.prepare.network.CreateGeoJsonNetwork;
import org.matsim.simwrapper.Dashboard;
import org.matsim.simwrapper.Header;
import org.matsim.simwrapper.Layout;
import org.matsim.simwrapper.viz.*;
import tech.tablesaw.plotly.components.Axis;
import tech.tablesaw.plotly.traces.BarTrace;
import tech.tablesaw.plotly.traces.HistogramTrace;
import tech.tablesaw.plotly.traces.Trace;

import java.awt.*;

import static org.matsim.dashboard.RunLiveabilityDashboard.getValidInputDirectory;
import static org.matsim.dashboard.RunLiveabilityDashboard.getValidOutputDirectory;

public class AgentBasedGreenSpaceDashboard implements Dashboard {

	public double priority(){return -1;}

	@Override
	public void configure(Header header, Layout layout) {

		header.title = "Green Space";
		header.description = "A key indicator of spatial justice is the availability and accessibility of " +
			"relaxing green spaces for relaxation and stress relief. Therefore the walking distance to the nearest " +
			"green space as well as the amount of green space in this nearest area per Person is analysed.";

		//Entwurf: Kartendarstellung als xyt-Map - besser: SHP, daher siehe unten
//		layout.row("GreenSpace Ranking Map")
//			.el(XYTime.class, (viz, data) -> {
//				viz.title = "GreenSpace ranking results map";
//				viz.description = "Here you can see the agents according to their green space ranking depicted on their home location";
//				viz.height = 10.0;
//				viz.buckets = 5;
//				viz.radius = 5.0;
//				viz.colorRamp = "viridis";
//				viz.file = data.compute(AgentBasedGreenSpaceAnalysis.class, "XYTAgentBasedGreenSpaceMap.xyt.csv");
//
//				//BREAKPOINTS MÜSSEN NOCH DEFINIERT WERDEN; RADIUS AUCH; COLOR RAMP AUCH; CENTER AUCH
//			});

		layout.row("Green Space Deviations Shp")
			.el(MapPlot.class, (viz, data) -> {
				viz.title = "GreenSpace Index Results Map";
				viz.height = 10.0;

				viz.setShape(String.valueOf(ApplicationUtils.matchInput("allGreenSpaces_min1ha.shp", getValidOutputDirectory().resolve("analysis"))), "osm_id");
				viz.addDataset("greenSpace_utilization", data.compute(AgentBasedGreenSpaceAnalysis.class, "greenSpace_utilization.csv"));

				viz.display.fill.dataset = "greenSpace_utilization";
				viz.display.fill.join = "osm_id";
				viz.display.fill.setColorRamp(ColorScheme.RdYlBu, 3, false);

			});

		layout.row("Green Spaces Shp")
			.el(MapPlot.class, (viz, data) -> {
				viz.title = "Green Spaces Shp";
				viz.height = 10.0;

				viz.setShape(String.valueOf(ApplicationUtils.matchInput("allGreenSpaces_min1ha.shp", getValidInputDirectory())), "osm_id");
				viz.addDataset("greenSpace_utilization", data.compute(AgentBasedGreenSpaceAnalysis.class, "greenSpace_utilization.csv"));

				viz.display.fill.dataset = "greenSpace_utilization";
				viz.display.fill.join = "osm_id";
				viz.display.fill.setColorRamp(ColorScheme.RdYlBu, 3, false);

			});

		layout.row("overall ranking result green space")
			.el(Tile.class, (viz, data) -> {

				viz.title = "Green Space Ranking Value";
				viz.description = "According to the 'Deutsche Städtetag' every person should have accessibility to a green space " +
					"of at least 1 ha of size within 500 m footpath of their home location. Furthermore those green spaces should " +
					"offer at least 6 m² per person assigned to it (here: always choice of the nearest green space to the home location).";

				viz.dataset = data.compute(AgentBasedGreenSpaceAnalysis.class, "greenSpace_TilesOverall.csv");
				viz.height = 0.1;

			});

		layout.row("Stats per indicator - Tiles")
			.el(Tile.class, (viz, data) -> {
				viz.title = "Distance Indicator Details";
				viz.description = "Displays how many agents have a green space within 500m of their home location. " +
					"Also shows the average and mean distance that agents from the study area live away from green spaces.";
				viz.dataset = data.compute(AgentBasedGreenSpaceAnalysis.class, "greenSpace_TilesDistance.csv");
			})

			.el(Tile.class, (viz, data) -> {
				viz.title = "Utilization Indicator Details";
				viz.description = "Displays how many agents have at least 6m² of space in the green space closest to their home location. " +
					"Also shows the average and mean utilization that agents from the study area face when visiting their nearest green space.";
				viz.dataset = data.compute(AgentBasedGreenSpaceAnalysis.class, "greenSpace_TilesUtilization.csv");

			});

		layout.row("Stats per indicator - Diagram")
			.el(Plotly.class, (viz, data) -> {
				viz.title = "Green Space Distance - Distribution of deviations";
				viz.description = "tbc";

				// Add the dataset
				Plotly.DataSet dataset = viz.addDataset(data.compute(AgentBasedGreenSpaceAnalysis.class, "greenSpace_stats_perAgent.csv"));

				viz.addTrace(HistogramTrace.builder(Plotly.INPUT).name("GSDistanceDeviationFromLimit").nBinsX(200).build(),
					dataset.mapping() // Mapping verwenden, um Spalten aus dem Dataset zuzuordnen
						.x("GSDistanceDeviationFromLimit"));

				// Define the layout for the plot
				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.xAxis(Axis.builder().title("Deviation").build())
					.yAxis(Axis.builder().title("Number of Agents").build())
					.build();
			})

			.el(Plotly.class, (viz, data) -> {
				viz.title = "Green Space Utilization - Distribution of deviations";
				viz.description = "tbc";

				// Add the dataset
				Plotly.DataSet dataset = viz.addDataset(data.compute(AgentBasedGreenSpaceAnalysis.class, "greenSpace_stats_perAgent.csv"));

				viz.addTrace(HistogramTrace.builder(Plotly.INPUT).name("GSUtilizationDeviationFromLimit").nBinsX(200).build(),
					dataset.mapping() // Mapping verwenden, um Spalten aus dem Dataset zuzuordnen
						.x("GSUtilizationDeviationFromLimit"));

				// Define the layout for the plot
				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.xAxis(Axis.builder().title("Deviation").build())
					.yAxis(Axis.builder().title("Number of Agents").build())
					.build();
			});


	}
}
