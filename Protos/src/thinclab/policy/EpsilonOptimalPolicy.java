/*
 *	THINC Lab at UGA | Cyber Deception Group
 *
 *	Author: Aditya Shinde
 * 
 *	email: shinde.aditya386@gmail.com
 */
package thinclab.policy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import thinclab.DDOP;
import thinclab.legacy.DD;
import thinclab.legacy.Global;

/*
 * @author adityas
 *
 */
public class EpsilonOptimalPolicy extends AlphaVectorPolicy {

    private static final Logger LOGGER = 
        LogManager.getFormatterLogger(EpsilonOptimalPolicy.class);

    private final float conf;
    private final int A;

    public EpsilonOptimalPolicy(List<Integer> stateIndices, float conf,
            int A) {
        super(stateIndices);
        this.conf = conf;
        this.A = A;
    }

    public EpsilonOptimalPolicy(Collection<AlphaVector> alphaVectors,
            List<Integer> stateIndices, float conf, int A) {
        super(alphaVectors, stateIndices);
        this.conf = conf;
        this.A = A;
    }

    @Override
    public int getBestActionIndex(DD belief) {

        if (Global.random.nextFloat() > conf)
            return super.getBestActionIndex(belief);

        var Q = new float[A];
        for (int q = 0; q < Q.length; q++)
            Q[q] = 0.0f;

        float min_U = Float.POSITIVE_INFINITY;
        for (var v: this) {
            var val = DDOP.dotProduct(belief, v.getVector(), stateIndices);
            int a = v.getActId();

            if (Q[a] < val)
                Q[a] = val;

            if (val < min_U)
                min_U = val;
        }

        // shift
        for (int q = 0; q < Q.length; q++) {
            Q[q] = Q[q] - min_U;
        }

        float sum = 0f;
        for (int q = 0; q < Q.length; q++)
            sum += Q[q];

        ArrayList<Float> Qfn = new ArrayList<Float>();
        for (int q = 0; q < Q.length; q++)
            Qfn.add(Q[q] / sum);

        LOGGER.debug("Sampling from %s", Qfn);

        var best = DDOP.sample(Qfn);
        return best;
    }

}
