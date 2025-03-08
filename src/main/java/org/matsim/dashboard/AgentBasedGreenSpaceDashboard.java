package org.matsim.dashboard;

import org.jaitools.numeric.Histogram;
import org.matsim.analysis.*;
import org.matsim.application.ApplicationUtils;
import org.matsim.application.prepare.network.CreateGeoJsonNetwork;
import org.matsim.simwrapper.Dashboard;
import org.matsim.simwrapper.Header;
import org.matsim.simwrapper.Layout;
import org.matsim.simwrapper.viz.*;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.plotly.components.Axis;
import tech.tablesaw.plotly.traces.BarTrace;
import tech.tablesaw.plotly.traces.HistogramTrace;
import tech.tablesaw.plotly.traces.Trace;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.matsim.dashboard.RunLiveabilityDashboard.*;
import static tech.tablesaw.plotly.components.Axis.CategoryOrder.ARRAY;
import static tech.tablesaw.plotly.components.Axis.CategoryOrder.CATEGORY_ASCENDING;

// This is the Class creating the Dashboard for the green space related analyses.
// 1. Map of all agents with their deviation values from the set limit
// 2. Tiles with details on overall fulfillment and indicator specific achievement values
// 3. Distribution of the deviations for each indicator (distance and utilitzation) with zero being the limit, positive values exceeding the limit and negative ones for falling below
// 4. additional information on the green spaces to see how big they are
public class AgentBasedGreenSpaceDashboard implements Dashboard {

	public double priority(){return -1;}

	@Override
	public void configure(Header header, Layout layout) {

		header.title = "Green Space";
		header.description = "A key indicator of spatial justice is the availability and accessibility of " +
			"relaxing green spaces for relaxation and stress relief. Therefore the walking distance to the nearest " +
			"green space as well as the amount of green space in this nearest area per Person is analysed.";

		// map as xyt-Map - better: SHP for more interactive use of the data
		layout.row("GreenSpace Ranking Map")
			.el(XYTime.class, (viz, data) -> {
				viz.title = "GreenSpace ranking results map";
				viz.description = "Here you can see the agents according to their green space ranking depicted on their home location";
				viz.height = 10.0;
				viz.buckets = 6;
				viz.radius = 15.0;
				//viz.setColorRamp(new double[]{30.0, 40.0, 50.0, 60.0, 70.0}, new String[]{"#1175b3", "#95c7df", "#dfdb95", "#dfb095", "#f4a986", "#cc0c27"});
				viz.setBreakpoints(-0.5, 0.0, 0.5, 1.0, 1.5);
				viz.colorRamp = "viridis";
				viz.file = data.compute(AgentBasedGreenSpaceAnalysis.class, "XYTAgentBasedGreenSpaceMap.xyt.csv");

				//BREAKPOINTS MÜSSEN NOCH DEFINIERT WERDEN; RADIUS AUCH; COLOR RAMP AUCH; CENTER AUCH
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
				viz.description = "Displays the distribution of deviation for the green space distance. Zero represents the aim of 500 m and " +
					"values below zero show all values that fall below, meaning a better value for citizens. Agents that exceed the 500m euclidean " +
					"distance to a green space are represented in the values above zero, with 1 meaning 100 % over the limit (1000m euclidean distance).";

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
				viz.description = "Displays the distribution of deviation for the green space utilization. Zero represents the aim of 6 m²/person and " +
				"values below zero show all values that fall below, meaning a better value for citizens, as the return value is used (how many people per m² of green space)." +
				"Agents that exceed the 6 m²/person for their nearest green space are represented in the values above zero, with 1 meaning 100 % over the limit (3 m²/person).";

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

		layout.row("Green Space info - size groups")
			.el(Plotly.class, (viz, data) -> {
				viz.title = "Green Space Area Distribution";
				viz.description = "Distribution of green spaces by size categories";

				// Add the dataset
				Plotly.DataSet dataset = viz.addDataset(data.compute(AgentBasedGreenSpaceAnalysis.class, "greenSpace_utilization.csv"));

				viz.addTrace(HistogramTrace.builder(Plotly.INPUT).name("areaCategory").build(),
					dataset.mapping() // Mapping verwenden, um Spalten aus dem Dataset zuzuordnen
						.x("areaCategory"));

				Axis.CategoryOrder categoryOrder = Axis.CategoryOrder.ARRAY;
				// Define the layout for the plot
				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.xAxis(Axis.builder().title("Green Space Size").categoryOrder(CATEGORY_ASCENDING).build())
					.yAxis(Axis.builder().title("Number of Green Spaces").build())
					.build();

			});

		// geofile based map - issue: visualization cannot be adapted here
		layout.row("Green Space Deviations Geofile")
			.el(MapPlot.class, (viz, data) -> {
				viz.title = "GreenSpace Index Results Map";
				viz.height = 10.0;

				viz.setShape("greenSpace_perAgentGeofile.gpkg", data.compute(AgentBasedGreenSpaceAnalysis.class, "greenSpace_perAgentGeofile.gpkg"));

				viz.display.fill.dataset = "greenSpaceOverallIndexValue";
				viz.display.fill.setColorRamp(ColorScheme.RdYlBu, 6, false);

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
	}
}
