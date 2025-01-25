package org.matsim.analysis;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.application.ApplicationUtils;
import org.matsim.application.CommandSpec;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.InputOptions;
import org.matsim.application.options.OutputOptions;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.router.DijkstraFactory;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.geometry.CoordUtils;
import picocli.CommandLine;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

import static org.matsim.dashboard.RunLiveabilityDashboard.*;

@CommandLine.Command(
	name = "lossTime-analysis",
	description = "Loss Time Analysis",
	mixinStandardHelpOptions = true,
	showDefaultValues = true
)

@CommandSpec(
	requireRunDirectory = true,
	group="liveability",
	requires = {
		"berlin-v6.3.output_legs.csv.gz"
	},
	produces = {
		"output_legsLossTime_new.csv",
		"summary_modeSpecificLegsLossTime.csv",
		"lossTime_stats_perAgent.csv",
		"lossTime_RankingValue.csv"
	}
)

public class AgentBasedLossTimeAnalysis implements MATSimAppCommand {

	private final double limitRelativeLossTime = 0.15;
	//OFFEN: PRÜFEN WIE OFT DER DER WERT ÜBERSCHRITTEN WIRD UND DIE GESAMTLOSSTIME DABEI KLEINER ALS 2/3 MINUTEN IST - WENN DAS VIELE SIND
	//MUSS EIN ZWEITER FAKTOR (MINIMALE ABSOLUTE LOSS TIME) ZUR EXKLUSION ERGÄNZT WERDEN

	@CommandLine.Mixin
	private final InputOptions input = InputOptions.ofCommand(AgentBasedLossTimeAnalysis.class);
	@CommandLine.Mixin
	private final OutputOptions output = OutputOptions.ofCommand(AgentBasedLossTimeAnalysis.class);

	// constants for paths
	private final Path inputLegsCsvFile = getValidOutputDirectory().resolve("berlin-v6.3.output_legs.csv.gz") ;
	private final Path agentLiveabilityInfoPath = ApplicationUtils.matchInput("agentLiveabilityInfo.csv", getValidLiveabilityOutputDirectory());

	private final Path outputSummaryPath = getValidOutputDirectory().resolve("summary_modeSpecificLegsLossTime.csv");
	private final Path outputCSVPath = getValidOutputDirectory().resolve("output_legsLossTime_new.csv");
//	private final Path outputRankingAgentStatsPath = getValidOutputDirectory().resolve("analysis/analysis/lossTime_stats_perAgent.csv");
	private final Path outputRankingAgentStatsPath = getValidLiveabilityOutputDirectory().resolve("lossTime_stats_perAgent.csv");

	private final Path outputRankingValuePath = getValidOutputDirectory().resolve("lossTime_RankingValue.csv");

	public static void main(String[] args) {
		new AgentBasedLossTimeAnalysis().execute();
	}

	@Override
	public Integer call() throws Exception {

        //load network & execute NetworkCleaner
		Network network = NetworkUtils.readNetwork(String.valueOf(ApplicationUtils.matchInput("network.xml.gz", getValidOutputDirectory())));

//		String networkFile = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v6.3/input/berlin-v6.3-network-with-pt.xml.gz"; //Netzwerk-Dateipfad
//		Network network = loadNetwork(networkFile);

        NetworkCleaner cleaner = new NetworkCleaner();
        cleaner.run(network);



        //find input-File (legs.csv) and define Output-paths
        //	Path inputLegsCSVPath = Path.of(input.getPath("berlin-v6.3.output_legs.csv.gz"));

	//	Path inputLegsCsvFile = Path.of("C:/Users/annab/MatSim for MA/Output_Cluster/OBS_Base/output_OBS_Base/output_legs.csv/berlin-v6.3.output_legs.csv");
////        String inputLegsCsvFile = ApplicationUtils.matchInput("legs.csv", input.getRunDirectory()).toAbsolutePath().toString();
//        Path outputSummaryPath = output.getPath("summary_modeSpecificLegsLossTime.csv");
//        Path outputCSVPath = output.getPath("output_legsLossTime_new.csv");
//        Path outputRankingAgentStatsPath = output.getPath("lossTime_stats_perAgent.csv");
//		Path outputRankingValuePath = output.getPath("lossTime_RankingValue.csv");

		AgentLiveabilityInfoCollection agentLiveabilityInfoCollection = new AgentLiveabilityInfoCollection();

//		CSVReader csvReader = new CSVReader(new FileReader(inputLegsCsvFile));
//
//		List<String[]> rows = csvReader.readAll();
//		String[] headers = rows.get(0);
//
//		Map<String,Integer> columnIndexMap = new HashMap<>();
//		for (int i = 0; i < headers.length; i++){
//			columnIndexMap.put(headers[i],i);
//		}
//
//		for (int i = 1; i < rows.size(); i++){
//			String[] row = rows.get(i);
//			String waitTime = row[columnIndexMap.get("waitTime")];
//		}

		//read Input-legs.csv file and write new output files for loss time analysis

		try (InputStream fileStream = new FileInputStream(inputLegsCsvFile.toFile());
			 InputStream gzipStream = new GZIPInputStream(fileStream);
			 Reader reader = new InputStreamReader(gzipStream);
		//	 BufferedReader brInputLegs = new BufferedReader(new FileReader(String.valueOf(inputLegsCsvFile)));
			 BufferedWriter bwLegsLossTime = new BufferedWriter(Files.newBufferedWriter(outputCSVPath))) {
			CSVParser legsParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader().withDelimiter(';'));
			// CSVWriter reisezeitVergleichsWriter = new CSVWriter(new FileWriter(outputReisezeitvergleichPath.toFile()));
			//CSVWriter ptQualityStatsWriter = new CSVWriter(new FileWriter(outputRankingValuePath.toFile()))) {

			//	try (BufferedReader brInputLegs = new BufferedReader(new FileReader(String.valueOf(inputLegsCsvFile)));

			// writing new csv with added legs loss time information
//			 BufferedWriter bwLegsLossTime = new BufferedWriter(Files.newBufferedWriter(outputCSVPath))) {

			// read and skip header-line
			//	String line = brInputLegs.readLine();

			//defining maps for further csv-Writer tasks
			Map<String, Long> cumulativeLossTime = new HashMap<>();
			Map<String, Long> failedRoutingOccurances = new HashMap<>();
			Map<String, Double> sumTravTimePerAgent = new HashMap<>();
			Map<String, Double> sumLossTimePerAgent = new HashMap<>();
			Map<String, Double> percentageLossTimePerAgent = new HashMap<>();
			Map<String, Set<String>> modePerPerson = new HashMap<>();
			Map<String, Double> limitValueMap = new HashMap<>();
			Map<String, Double> relativeLossTimeRankingValue = new HashMap<>();

			// defining column-headers (; as separator) for the new legsLossTime_csv
			bwLegsLossTime.write("person;trip_id;mode;trav_time;fs_trav_time;loss_time;percent_lossTime;trav_time_hms;fs_trav_time_hms;loss_time_hms;dep_time;start_x;start_y;start_node_found;start_link;end_x;end_y;end_node_found;end_link\n");

			//Berechnung der LossTime nur für agenten in Berlin
			Set<String> relevantPersons = new HashSet<>();
			try (CSVParser inputPersonParser = new CSVParser(new FileReader(String.valueOf(agentLiveabilityInfoPath)), CSVFormat.DEFAULT.withFirstRecordAsHeader().withDelimiter(','))) {
				for (CSVRecord personRecord : inputPersonParser) {
					relevantPersons.add(personRecord.get("person"));
				}
			}

			int limit = 800;
			int count = 0;

			for (CSVRecord legRecord : legsParser) {
				//			legsParser.stream().limit(500).forEach(leg -> {


				if (count >= limit) {
					break;
				}

				// collection of values from existing legs.csv to take over to the new legsLossTime.csv
				double startX = Double.parseDouble(legRecord.get("start_x"));
				double startY = Double.parseDouble(legRecord.get("start_y"));
				String startLink = legRecord.get("start_link");
				double endX = Double.parseDouble(legRecord.get("end_x"));
				double endY = Double.parseDouble(legRecord.get("end_y"));
				String endLink = legRecord.get("end_link");

				String person = legRecord.get("person");
				String tripId = legRecord.get("trip_id");
				String mode = legRecord.get("mode");
				String depTime = legRecord.get("dep_time");

				String travTimeString = legRecord.get("trav_time");

				if (relevantPersons.contains(person)) {

					// transforming travTime (hh:mm:ss) from the legs.csv into the string format (PTnHnMnS) and into the duration format (hh:mm:ss) afterwards [formattedTravTime as a result]
					if (travTimeString.matches("\\d{2}:\\d{2}:\\d{2}")) {
						String[] timeParts = travTimeString.split(":");
						travTimeString = "PT" + timeParts[0] + "H" + timeParts[1] + "M" + timeParts[2] + "S";
					} else {
						System.out.println("Ungültiges Zeitformat: " + travTimeString);
						//		continue;
					}
					Duration travTime = Duration.parse(travTimeString);
					long hours = travTime.toHours();
					long minutes = travTime.toMinutes() % 60;
					long seconds = travTime.getSeconds() % 60;
					String formattedTravTime = String.format("%02d:%02d:%02d", hours, minutes, seconds);

					// transform travel time into seconds
					long travTimeInSeconds = travTime.getSeconds();

					// prep data for free speed travel time calculation
					Coord startPoint = new Coord(startX, startY);
					Coord endPoint = new Coord(endX, endY);

					Node startNodeFound = NetworkUtils.getNearestNode(network, startPoint);
					Node endNodeFound = NetworkUtils.getNearestNode(network, endPoint);

					// free speed travel time is calculated by calling the calculateFreeSpeedTravelTime method for every start-end-point-combination
					long freeSpeedTravelTimeInSeconds = (long) calculateFreeSpeedTravelTime(network, startPoint, endPoint, mode, travTimeInSeconds);

					//transforming the resulting free speed travel time into the duration format as well es the String hh:mm:ss format
					Duration fsTravTimeHMS = Duration.ofSeconds(freeSpeedTravelTimeInSeconds);
					long hours_fs = fsTravTimeHMS.toHours();
					long minutes_fs = fsTravTimeHMS.toMinutes() % 60;
					long seconds_fs = fsTravTimeHMS.getSeconds() % 60;
					String formattedFreeSpeedTravTime = String.format("%02d:%02d:%02d", hours_fs, minutes_fs, seconds_fs);

					// defining loss time as the difference of travel time minus freeSpeedTravelTime (place holders are intercepted by the if-clause)
					// data type is Long as no double is required since seconds is the smallest unit used in matsim
					Long lossTimeInSeconds;
					if (freeSpeedTravelTimeInSeconds != -1 && freeSpeedTravelTimeInSeconds != -2) {
						lossTimeInSeconds = travTimeInSeconds - freeSpeedTravelTimeInSeconds;
						if (lossTimeInSeconds < 0) {
							lossTimeInSeconds = 0L;
						} // avoiding negative loss times
					} else {
						lossTimeInSeconds = 0L;
						failedRoutingOccurances.put(mode, failedRoutingOccurances.getOrDefault(mode, 0L) + 1);
					}

					// transforming the seconds of the loss time calculation to the duration format and the String (hh:mm:ss) afterwards
					Duration lostTimeHMS = Duration.ofSeconds(lossTimeInSeconds);
					long hours_lt = lostTimeHMS.toHours();
					long minutes_lt = lostTimeHMS.toMinutes() % 60;
					long seconds_lt = lostTimeHMS.getSeconds() % 60;
					String formattedLossTime = String.format("%02d:%02d:%02d", hours_lt, minutes_lt, seconds_lt);

					// calculating the percentage of loss time compared to the free speed (minimum possible) travel time for positive loss time values
					double percentLossTime = 0.0;
					if (freeSpeedTravelTimeInSeconds != 0 && freeSpeedTravelTimeInSeconds != -1 && freeSpeedTravelTimeInSeconds != -2) {
						percentLossTime = (double) lossTimeInSeconds / freeSpeedTravelTimeInSeconds;
					} else {
						System.out.println("Warnung: Division by Zero for trip " + tripId + " avoided.");
					}

					// calculate sum of travel time and of loss time per agent. overall loss time sum and mode per person info
					sumLossTimePerAgent.put(person, sumLossTimePerAgent.getOrDefault(person, 0.0) + lossTimeInSeconds);
					sumTravTimePerAgent.put(person, sumTravTimePerAgent.getOrDefault((Object) person, 0.0) + travTimeInSeconds);
					modePerPerson.computeIfAbsent(person, k -> new HashSet<>()).add(mode);
					percentageLossTimePerAgent.put(person, percentLossTime);

					limitValueMap.put(person, limitRelativeLossTime);

					relativeLossTimeRankingValue.put(person, (percentLossTime-limitRelativeLossTime)/limitRelativeLossTime);


					if (lossTimeInSeconds != 0L) {
						cumulativeLossTime.put(mode, cumulativeLossTime.getOrDefault(mode, 0L) + lossTimeInSeconds);
					} else {
						System.out.println("Warnung: Loss Time for trip" + tripId + " is zero.");
					}

					// writing the desired columns in the new legsLossTime output csv file
					try {
						bwLegsLossTime.write(String.format("%s;%s;%s;%s;%d;%d;%f;%s;%s;%s;%s;%f;%f;%s;%s;%f;%f;%s;%s\n",
							person, tripId, mode, travTimeInSeconds, freeSpeedTravelTimeInSeconds, lossTimeInSeconds,
							percentLossTime, formattedTravTime, formattedFreeSpeedTravTime, formattedLossTime, depTime,
							startX, startY, startNodeFound.getId(), startLink, endX, endY, endNodeFound.getId(), endLink));
					} catch (IOException e) {
						throw new RuntimeException(e);
					}

					//System.out.println("Neue LossTime für Person "+person);
					count++;
				}
		//		});
			}

					//HIER WEITER MIT CODE-ÜBERARBEITUNG UND KOMMENTIERUNG


				try (BufferedWriter summaryBw = new BufferedWriter(Files.newBufferedWriter(outputSummaryPath))) {
					summaryBw.write("mode;cumulative_loss_time;failed_routings\n");

					for (String mode : cumulativeLossTime.keySet()) {
						long cumulativeLoss = cumulativeLossTime.getOrDefault(mode, 0L);
						long failedCount = failedRoutingOccurances.getOrDefault(mode, 0L);
						summaryBw.write(String.format("%s;%d;%d\n", mode, cumulativeLoss, failedCount));
					}
				}

				//	try (BufferedReader br = new BufferedReader(new FileReader(String.valueOf(inputLegsCsvFile)));

				//Tabelle mit Summen pro Person - aus diesen können die Agentenbasierte True/False Werte gebildet werden und aus deren Summe der Ranking-Prozentsatz
				try (BufferedWriter agentBasedBw = new BufferedWriter(Files.newBufferedWriter(outputRankingAgentStatsPath))) {
					agentBasedBw.write("Person;lossTimePerAgent;travTimePerAgent;percentageLossTime;rankingStatus;modesUsed\n");


					for (String person : sumLossTimePerAgent.keySet()) {
						double lossTimePerAgent = sumLossTimePerAgent.getOrDefault((Object) person, 0.0);
						double travTimePerAgent = sumTravTimePerAgent.getOrDefault((Object) person, 0.0);
						double percentageLossTime = lossTimePerAgent / travTimePerAgent;
						//	String modeUsed = modePerPerson.getOrDefault((Object) person, "");
						String modeUsed = String.join(",", modePerPerson.getOrDefault(person, Set.of()));
						boolean rankingStatus = false;
						if (percentageLossTime < limitRelativeLossTime) {
							rankingStatus = true;
						}
						agentBasedBw.write(String.format("%s;%f;%f;%f;%s;%s \n", person, lossTimePerAgent, travTimePerAgent, percentageLossTime, rankingStatus, modeUsed));
					}

					agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(sumLossTimePerAgent, "Loss Time");
					agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(sumTravTimePerAgent, "Travel Time");
					agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(percentageLossTimePerAgent, "percentageLossTime");
					agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(limitValueMap, "limit_relativeLossTime");
					agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(relativeLossTimeRankingValue, "rankingValue_relativeLossTime");

				}

				// Summiere die Spalte loss_time aus der Datei output_legsLossTime_new.csv
				try (BufferedReader reader2 = Files.newBufferedReader(outputCSVPath)) {
					long totalLossTimeInSeconds = 0;

					// Überspringe die Header-Zeile
					reader2.readLine();
						String line = reader2.readLine();


					// Iteriere über alle Zeilen der CSV
					while ((line = reader2.readLine()) != null) {
						String[] values = line.split(";");
						String lossTimeValue = values[5]; // Spalte "loss_time"
						if (!lossTimeValue.isEmpty()) {
							totalLossTimeInSeconds += Long.parseLong(lossTimeValue); // Aufsummieren
						}
					}

					// Konvertiere die Gesamtsumme in das Format HH:mm:ss
					Duration totalLossTime = Duration.ofSeconds(totalLossTimeInSeconds);
					long hours = totalLossTime.toHours();
					long minutes = totalLossTime.toMinutes() % 60;
					long seconds = totalLossTime.getSeconds() % 60;
					String formattedTotalLossTime = String.format("%02d:%02d:%02d", hours, minutes, seconds);

					try (BufferedReader agentBasedReader = Files.newBufferedReader(outputRankingAgentStatsPath)) {
						String entry;
						int totalEntries = 0;
						int trueEntries = 0;

						// Überspringen der Header-Zeile
						agentBasedReader.readLine();

						// Iteration über alle Zeilen
						while ((entry = agentBasedReader.readLine()) != null) {
							String[] values = entry.split(";");
							// Prüfen, ob die Spalte "rankingStatus" auf True gesetzt ist
							if (values.length > 4 && "true".equalsIgnoreCase(values[4].trim())) {
								trueEntries++;
							}
							totalEntries++;
						}

						// Anteil der True-Einträge berechnen
						double rankingLossTime = (totalEntries > 0) ? ((double) trueEntries / totalEntries) * 100 : 0.0;
						String formattedRankingLossTime = String.format(Locale.US, "%.2f%%", rankingLossTime);

						// Schreibe das Ergebnis zusammen mit rankingLossTime in die Datei lossTime_RankingValue.csv
						try (BufferedWriter writer = Files.newBufferedWriter(outputRankingValuePath)) {
							//	writer.write("Dimension;Value\n"); // Header
							writer.write(String.format("LossTimeRanking;%s\n", formattedRankingLossTime)); // Ranking
							writer.write(String.format("LossTimeSum(h:m:s);%s\n", formattedTotalLossTime)); // Summe der Verlustzeit
						}

						System.out.println("LossTimeSum (HH:mm:ss): " + formattedTotalLossTime);
						System.out.println("LossTimeRanking: " + formattedRankingLossTime);

//						agentLiveabilityInfo.extendSummaryTilesCsvWithAttribute(formattedRankingLossTime, "LossTime", "https://github.com/simwrapper/simwrapper/blob/master/public/images/tile-icons/route.svg");
						agentLiveabilityInfoCollection.extendSummaryTilesCsvWithAttribute(formattedRankingLossTime, "LossTime");

						agentLiveabilityInfoCollection.extendIndicatorValuesCsvWithAttribute("Loss Time", "relative Loss Time", String.valueOf(totalLossTime), "15 %", String.valueOf(rankingLossTime), 1);


					} catch (IOException e) {
						e.printStackTrace();
					}

					return 0;

				}

		}
	}

/*
				try (BufferedReader legsLossTimeReader = Files.newBufferedReader(outputCSVPath)) {
					String lines;
					Long sumLossTime = 0L;
					// Überspringen der Header-Zeile
					agentBasedReader.readLine();
					// Iteration über alle Zeilen
					while ((entry = agentBasedReader.readLine()) != null) {
						String[] values = entry.split(";");

						sumLossTime += (Long) values[5];
						// Prüfen, ob die Spalte "rankingStatus" auf True gesetzt ist
						if (values.length > 4 && "true".equalsIgnoreCase(values[4].trim())) {
							trueEntries++;
						}
						totalEntries++;
					}

					// Ergebnis in die Datei schreiben
					try (BufferedWriter lossTimeRankingBw = Files.newBufferedWriter(outputRankingValuePath)) {
						//	lossTimeRankingBw.write("Dimension;RankingPercentage\n");

					String dimension = "LossTimeAgentRanking";
					// Prozentsatz formatieren und schreiben (z. B. 12.34%)
					String formattedRankingLossTime = String.format(Locale.US, "%.2f%%", rankingLossTime);
				//	lossTimeRankingBw.write(String.format("%s;%s\n", dimension, formattedRankingLossTime));
				//	System.out.println("Info: Loss Time Ranking Wert ist "+formattedRankingLossTime);

//						String sum = "LossTimeSum";

//						lossTimeRankingBw.write(String.format("%s;%s\n", sum, sumLossTime));
//						System.out.println("Info: Loss Time Gesamtsumme ist " + sumLossTime);
					}

				} catch (IOException e) {
					e.printStackTrace();
				}
			}
				try (CSVParser parser = new CSVParser(Files.newBufferedReader(outputCSVPath), CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
					Long sumLossTime = 0L;

					for (CSVRecord record : parser) {
						String value = record.get("loss_time");
						sumLossTime += Long.parseLong(value);
					}
					// Wandelt die Sekunden in LocalTime um (nur für positive Werte)
					Duration sumLossTimeHMS = Duration.ofSeconds(sumLossTime);
					long hours_lt = sumLossTimeHMS.toHours();
					long minutes_lt = sumLossTimeHMS.toMinutes() % 60;
					long seconds_lt = sumLossTimeHMS.getSeconds() % 60;
					String formattedSumLossTime = String.format("%02d:%02d:%02d", hours_lt, minutes_lt, seconds_lt);

*/








	// Netzwerk laden
	private static Network loadNetwork(String networkFile) {
		Network network = NetworkUtils.createNetwork();
		new MatsimNetworkReader(network).readFile(networkFile);
		return network;
	}





	// Berechnung der Reisezeit auf der Strecke (FreeSpeed-Modus)
	private static double calculateFreeSpeedTravelTime(Network network, Coord point1, Coord point2, String mode, long InputTravTimeInSeconds) {
		double travelTimeInSeconds = 0;
		double beelineFactor = 1.3; // Faktor für bike und walk

		switch (mode.toLowerCase()) {
			case "bike":
			case "walk":
				double distance = CoordUtils.calcEuclideanDistance(point1, point2);

				// Beeline-Faktor berücksichtigen
				distance *= beelineFactor;

				// Durchschnittsgeschwindigkeit festlegen
				double averageSpeed = mode.equals("bike") ? 3.1388889 : 1.23; // 5 m/s für bike, 1.4 m/s für walk
				travelTimeInSeconds = distance / averageSpeed;
				break;

			case "car":
			case "freight":
			case "truck":
			case "ride":
				Node startNode = NetworkUtils.getNearestNode(network, point1);
				Node endNode = NetworkUtils.getNearestNode(network, point2);

				// TravelTime-Implementierung für Freifahrtgeschwindigkeit
				TravelTime freeSpeedTravelTime = (link, time, person, vehicle) -> {
					if (link.getAllowedModes().contains(mode)) {
						return link.getLength() / link.getFreespeed();
					} else {
						return Double.POSITIVE_INFINITY; // Modus nicht erlaubt
					}
				};

				TravelDisutility travelDisutility = new OnlyTimeDependentTravelDisutility(freeSpeedTravelTime);
				LeastCostPathCalculator router = new DijkstraFactory().createPathCalculator(network, travelDisutility, freeSpeedTravelTime);

				LeastCostPathCalculator.Path path = router.calcLeastCostPath(startNode, endNode, 0, null, null);

				if (path == null || path.links.isEmpty()) {
					System.out.println("Keine Route für Modus " + mode + " gefunden.");
					return -1;
				}

				// Verwende die Gesamtzeit der Route, die bereits berechnet wurde
				travelTimeInSeconds = (long) path.travelTime;
				break;

			case "pt":
				return InputTravTimeInSeconds;

			default:
				System.out.println("Ungültiger Modus: " + mode);
				return -2; // Ungültiger Modus
		}
		return travelTimeInSeconds;
	}

	public static double getLossTimeSum(Path outputDirectory) throws IOException {
		int sum = 0;

	//	Path filePath = outputDirectory.resolve("summary_modeSpecificLegsLossTime.csv");
		if (!Files.exists(outputDirectory)) {
			throw new IOException(("Die Datei summary_modeSpecificLegsLossTime.csv wurde im Ordner nicht gefunden: " + outputDirectory.toAbsolutePath()));
		}
		try (CSVParser parser = new CSVParser(Files.newBufferedReader(outputDirectory),CSVFormat.DEFAULT.withFirstRecordAsHeader())){
			for (CSVRecord record : parser) {
				String value = record.get("cumulative_loss_time");
				sum += (int) Double.parseDouble(value);
			}
		}
	catch (NumberFormatException e) {
		throw new IOException("Fehler beim Parsen der Werte in der Datei: " + e.getMessage(), e);
	}
		return sum;
	}
/*
	public double getRankingLossTime() throws IOException {
		double ranklossTime = 0.0;

		String entry;
		int totalEntries = 0;
		int trueEntries = 0;

		try (BufferedReader readerRankLT = Files.newBufferedReader(outputRankingValuePath)) {
			// Überspringen der Header-Zeile
			readerRankLT.readLine();
			// Iteration über alle Zeilen
			while ((entry = readerRankLT.readLine()) != null) {
				String[] values = entry.split(";");
				if (values.length > 4 && "true".equalsIgnoreCase(values[4].trim())) {
					trueEntries++;
				}
				totalEntries++;
			}


			double rankLossTime = (totalEntries > 0) ? ((double) trueEntries / totalEntries) * 100 : 0.0;
		}

		return ranklossTime;
	}
*/
	private static Duration parseTime(String timeString) {
		if (timeString != null && timeString.matches("\\d{2}:\\d{2}:\\d{2}")) {
			String[] parts = timeString.split(":");
			return Duration.ofHours(Long.parseLong(parts[0]))
				.plusMinutes(Long.parseLong(parts[1]))
				.plusSeconds(Long.parseLong(parts[2]));
		}
		throw new IllegalArgumentException("Ungültiges Zeitformat: " + timeString);
	}

	private static String formatDuration(Duration duration) {
		long hours = duration.toHours();
		long minutes = duration.toMinutes() % 60;
		long seconds = duration.getSeconds() % 60;
		return String.format("%02d:%02d:%02d", hours, minutes, seconds);
	}

}
