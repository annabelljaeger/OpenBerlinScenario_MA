package org.matsim.run.scoring;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scoring.ScoringFunction;
import org.matsim.core.scoring.SumScoringFunction;

import java.util.List;

public class PseudoRandomTripScoring implements SumScoringFunction.TripScoring {

	private static final Logger log = LogManager.getLogger(PseudoRandomTripScoring.class);

	private final Id<Person> id;
	private final PseudoRandomScorer rng;

	private final DoubleList scores = new DoubleArrayList();
	private double score;

	public PseudoRandomTripScoring(Id<Person> id, PseudoRandomScorer rng) {
		this.id = id;
		this.rng = rng;
	}

	@Override
	public void finish() {
	}

	@Override
	public double getScore() {
		return score;
	}

	@Override
	public void handleTrip(TripStructureUtils.Trip trip) {

		List<Leg> legs = trip.getLegsOnly();
		if (legs.isEmpty()) {
			log.warn("Trip {} for person {} has not legs and can not be scored", trip, id);
			return;
		}

		double tripScore = rng.scoreTrip(id, legs.getFirst().getRoutingMode(), trip);
		scores.add(tripScore);
		score += tripScore;
	}

	@Override
	public void explainScore(StringBuilder out) {
		out.append("trips_util=").append(score);
		for (int i = 0; i < scores.size(); i++) {
			double s = scores.getDouble(i);
			out.append(ScoringFunction.SCORE_DELIMITER).append("trip_").append(i).append("=").append(s);
		}
	}
}
