/*
 *	THINC Lab at UGA | Cyber Deception Group
 *
 *	Author: Aditya Shinde
 * 
 *	email: shinde.aditya386@gmail.com
 */
package thinclab.policy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import thinclab.DDOP;
import thinclab.legacy.DD;

/*
 * @author adityas
 *
 */
public class BoltzmannExplorationPolicy extends AlphaVectorPolicy {

    private static final Logger LOGGER = 
        LogManager.getFormatterLogger(BoltzmannExplorationPolicy.class);

    private final float conf;
    private final int A;

    public BoltzmannExplorationPolicy(List<Integer> stateIndices, float conf,
            int A) {
        super(stateIndices);
        this.conf = conf;
        this.A = A;
    }

    public BoltzmannExplorationPolicy(Collection<AlphaVector> alphaVectors,
            List<Integer> stateIndices, float conf, int A) {
        super(alphaVectors, stateIndices);
        this.conf = conf;
        this.A = A;
    }

    @Override
    public int getBestActionIndex(DD belief) {

        var Q = new float[A];
        for (int q = 0; q < Q.length; q++)
            Q[q] = Float.NEGATIVE_INFINITY;

        for (var v: this) {

            var val = DDOP.dotProduct(belief, v.getVector(), stateIndices);
            val = (float) Math.exp(val * conf);
            int a = v.getActId();

            if (Q[a] < val)
                Q[a] = val;
        }

        float minQ = Float.POSITIVE_INFINITY;
        for (int q = 0; q < Q.length; q++) {
            if (Q[q] < minQ && Q[q] != Float.NEGATIVE_INFINITY)
                minQ = Q[q];
        }

        for (int q = 0; q < Q.length; q++) {
            if (Q[q] == Float.NEGATIVE_INFINITY && minQ != Float.POSITIVE_INFINITY)
                Q[q] = minQ;
        }

        ArrayList<Float> Qfn = new ArrayList<Float>();
        for (int q = 0; q < Q.length; q++)
            Qfn.add(Q[q]);

        var best = DDOP.sample(Qfn);
        LOGGER.debug("Sampled %s from %s", best, Qfn);

        return best;
    }

}
