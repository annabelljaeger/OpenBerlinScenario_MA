package org.matsim.run.scoring;

import com.google.inject.Inject;
import org.apache.commons.math3.exception.OutOfRangeException;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.core.source64.XoRoShiRo128PlusPlus;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;

/**
 * Computes pseudo random scores, which are frozen for certain choice situations, thus representing the unobserved heterogeneity.
 */
public final class PseudoRandomScorer {

	/**
	 * Number of random numbers to throw away before starting to use it.
	 */
	private static final int WARMUP_ITERATIONS = 100;

	private final PseudoRandomTripScore tripScore;
	private final long seed;
	private final double scale;

	@Inject
	public PseudoRandomScorer(PseudoRandomTripScore tripScore, Config config) {
		this.tripScore = tripScore;
		this.seed = config.global().getRandomSeed();
		this.scale = ConfigUtils.addOrGetModule(config, AdvancedScoringConfigGroup.class).pseudoRamdomScale;
	}

	/**
	 * Calculates the pseudo random score of a trip.
	 */
	public double scoreTrip(Id<Person> personId, String routingMode, String prevActivityType) {

		if (tripScore == null || scale == 0)
			return 0;

		long tripSeed = tripScore.getSeed(personId, routingMode, prevActivityType);

		// Need to create a new instance because reusing them will also create a lot of intermediate arrays
		XoRoShiRo128PlusPlus rng = new XoRoShiRo128PlusPlus(seed, tripSeed);
		for (int i = 0; i < WARMUP_ITERATIONS; i++) {
			rng.nextLong();
		}

		return sampleGumbel(rng, 0, scale);
	}


	/**
	 * Sample from a Gumbel distribution.
	 *
	 * @param mu   location parameter
	 * @param beta scale parameter (must be positive)
	 */
	private double sampleGumbel(UniformRandomProvider rng, double mu, double beta) {

		double v = rng.nextDouble();
		if (v < 0.0 || v > 1.0) {
			throw new OutOfRangeException(v, 0.0, 1.0);
		} else if (v == 0) {
			return Double.NEGATIVE_INFINITY;
		} else if (v == 1) {
			return Double.POSITIVE_INFINITY;
		}

		return mu - FastMath.log(-FastMath.log(v)) * beta;
	}

}
