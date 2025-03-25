package org.matsim.analysis;

import com.opencsv.CSVWriter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.application.ApplicationUtils;
import org.matsim.application.CommandSpec;
import org.matsim.application.Dependency;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.InputOptions;
import org.matsim.application.options.OutputOptions;
import picocli.CommandLine;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

import static org.matsim.dashboard.RunLiveabilityDashboard.getValidLiveabilityOutputDirectory;

@CommandLine.Command(
	name = "Liveability Summary Analysis",
	description = "Provides the overall liveability-index value and stats",
	mixinStandardHelpOptions = true,
	showDefaultValues = true
)

@CommandSpec(
	requireRunDirectory = true,
	group="liveability",
	dependsOn = {
		@Dependency(value = AgentBasedTrafficQualityAnalysis.class, files =  "travelTime_stats_perAgent.csv"),
		@Dependency(value= AgentBasedPtQualityAnalysis.class, files = "ptQuality_stats_perAgent.csv"),
		@Dependency(value = AgentBasedGreenSpaceAnalysis.class, files =  "greenSpace_stats_perAgent.csv"),
	},
	produces = {
		"overall_tiles_ranking.csv",
		"overall_XYT_AgentRankingForSummary.xyt.csv",
		"overall_tiles_highestLowestIndicator.csv"
	}
)

// overall liveability ranking analysis class to calculate the overall ranking and generating the indicator overview table for the Overall Dashboard page
public class LiveabilitySummaryAnalysis implements MATSimAppCommand {

	@CommandLine.Mixin
	private final InputOptions input = InputOptions.ofCommand(LiveabilitySummaryAnalysis.class);
	@CommandLine.Mixin
	private final OutputOptions output = OutputOptions.ofCommand(LiveabilitySummaryAnalysis.class);

	// input path
	private final Path agentLivabilityInfoPath = ApplicationUtils.matchInput("overall_stats_agentLiveabilityInfo.csv", getValidLiveabilityOutputDirectory());

	//output Path
	private final Path outputOverallRankingPath = getValidLiveabilityOutputDirectory().resolve("overall_tiles_ranking.csv");
	private final Path XYTMapOutputPath = getValidLiveabilityOutputDirectory().resolve("overall_XYT_AgentRankingForSummary.xyt.csv");
	private final Path overallHighestLowestIndicatorPath = getValidLiveabilityOutputDirectory().resolve("overall_tiles_highestLowestIndicator.csv");

	private static final Logger log = LogManager.getLogger(LiveabilitySummaryAnalysis.class);


	public static void main(String[] args) {
		new LiveabilitySummaryAnalysis().execute(args);
	}

	@Override
	public Integer call() throws Exception {

		log.info("Starting LiveabilitySummaryAnalysis...");

		Map<String, Map<String, Double>> indexValuesPerAgent = new HashMap<>();
		Map<String, List<String>> homeCoordinatesPerAgentInStudyArea = new HashMap<>();
		Map<String, Double> worstValuePerAgent = new HashMap<>();
		Map<String, String> worstCategoryPerAgent = new HashMap<>();
		Map<String, Double> bestValuePerAgent = new HashMap<>();
		Map<String, String> bestCategoryPerAgent = new HashMap<>();

		// Counter for all Agents with all Livability Index <= 0;
		long countHighLivabilityAgents = 0;

		try (CSVParser agentLiveabilityInfoParser = new CSVParser(new FileReader(String.valueOf(agentLivabilityInfoPath)), CSVFormat.DEFAULT.withFirstRecordAsHeader().withDelimiter(','));) {

			// Check whether the file is empty
			if (agentLiveabilityInfoParser.getHeaderMap().isEmpty()) {
				throw new IOException("The CSV file is empty or does not contain any valid headers.");
			}

			Set<String> headers = agentLiveabilityInfoParser.getHeaderMap().keySet();
			List<String> valueColumns = new ArrayList<>();

			// Check whether columns with “indexValue_” exist
			for (String header : headers) {
				if (header.startsWith("indexValue_")) {
					valueColumns.add(header);
				}
			}

			if (valueColumns.isEmpty()) {
				throw new IOException("No columns with 'indexValue_' found in the header.");
			}

			for (CSVRecord record : agentLiveabilityInfoParser) {
				String personKey = record.get("person");
				String homeX = record.get("home_x");
				String homeY = record.get("home_y");
				homeCoordinatesPerAgentInStudyArea.put(personKey, Arrays.asList(homeX, homeY));

				Map<String, Double> valuesMap = new HashMap<>();

				for (String column : valueColumns) {
					try {
						// Try to parse the value and add it to the valuesMap
						valuesMap.put(column, Double.valueOf(record.get(column)));
					} catch (NumberFormatException e) {
						continue; // Proceed to the next iteration
					}
				}

				indexValuesPerAgent.put(personKey, valuesMap);
			}
		}

		// identifying maximum and minimum indicator index values per agent
		for (Map.Entry<String, Map<String, Double>> agentEntry : indexValuesPerAgent.entrySet()) {
			String agent = agentEntry.getKey();
			Map<String, Double> values = agentEntry.getValue();

			if (values.isEmpty()) {
				continue;
			}

			String worstCategory = null;
			double worstValue = Double.NEGATIVE_INFINITY;
			String bestCategory = null;
			double bestValue = Double.POSITIVE_INFINITY;

			for (Map.Entry<String, Double> valueEntry : values.entrySet()) {
				String category = valueEntry.getKey();
				double value = valueEntry.getValue();

				if (value > worstValue) {
					worstValue = value;
					worstCategory = category;
				}

				if (value < bestValue) {
					bestValue = value;
					bestCategory = category;
				}
			}

			worstValuePerAgent.put(agent, worstValue);
			worstCategoryPerAgent.put(agent, worstCategory);
			bestValuePerAgent.put(agent, bestValue);
			bestCategoryPerAgent.put(agent, bestCategory);
		}

		AgentLiveabilityInfoCollection.writeXYTDataToCSV(XYTMapOutputPath, worstValuePerAgent, homeCoordinatesPerAgentInStudyArea);

		try(CSVWriter overallRankingWriter = new CSVWriter(new FileWriter(String.valueOf(outputOverallRankingPath)));
			CSVWriter highestLowestIndicatorTileWriter = new CSVWriter(new FileWriter(String.valueOf(overallHighestLowestIndicatorPath)));){

			Map.Entry<String, Double> resultWorst = findMostFrequentCategory(worstCategoryPerAgent);
			Map.Entry<String, Double> resultBest = findMostFrequentCategory(bestCategoryPerAgent);

			String worstIndicatorName = resultWorst.getKey();
			String formattedWorstIndicatorName = worstIndicatorName.substring(worstIndicatorName.indexOf('_') + 1);
			String formattedPercentageWorstIndicator = String.format(Locale.US, "%.2f%%", resultWorst.getValue()*100);
			String bestIndicatorName = resultBest.getKey();
			String formattedBestIndicatorName = bestIndicatorName.substring(bestIndicatorName.indexOf('_') + 1);
			String formattedPercentageBestIndicator = String.format(Locale.US, "%.2f%%", resultBest.getValue()*100);

			countHighLivabilityAgents = worstValuePerAgent.values().stream().filter(value -> value <= 0).count();
			double overallRankingValue = (double) countHighLivabilityAgents / worstValuePerAgent.size();
			String formattedOverallRankingValue = String.format(Locale.US, "%.2f%%", overallRankingValue*100);

			highestLowestIndicatorTileWriter.writeNext(new String[]{"Worst Indicator", formattedWorstIndicatorName});
			highestLowestIndicatorTileWriter.writeNext(new String[]{"Worst Indicator Percentage", formattedPercentageWorstIndicator});
			highestLowestIndicatorTileWriter.writeNext(new String[]{"Best Indicator", formattedBestIndicatorName});
			highestLowestIndicatorTileWriter.writeNext(new String[]{"Best Indicator Percentage", formattedPercentageBestIndicator});
			overallRankingWriter.writeNext(new String[]{"Overall Liveability-Index Value", formattedOverallRankingValue});
		}

		log.info("LiveabilitySummaryAnalysis completed successfully.");

		return 0;
	}

	// method to extract the most frequent category to identify the best and worst indicator in the scenario
	public static Map.Entry<String, Double> findMostFrequentCategory(Map<String, String> CategoryPerAgent) {
		Map<String, Integer> categoryCount = new HashMap<>();
		int totalAgents = CategoryPerAgent.size();

		// Count the frequency of each maxCategory
		for (String category : CategoryPerAgent.values()) {
			categoryCount.put(category, categoryCount.getOrDefault(category, 0) + 1);
		}

		// Determine the most frequent maxCategory
		String mostFrequentCategory = null;
		int maxCount = 0;

		for (Map.Entry<String, Integer> entry : categoryCount.entrySet()) {
			if (entry.getValue() > maxCount) {
				maxCount = entry.getValue();
				mostFrequentCategory = entry.getKey();
			}
		}

		// Calculate the proportion of the most frequent maxCategory
		double percentage = (totalAgents > 0) ? ((double) maxCount / totalAgents) : 0.0;

		// Return as Map.Entry<String, Double>
		return new AbstractMap.SimpleEntry<>(mostFrequentCategory, percentage);
	}
}
