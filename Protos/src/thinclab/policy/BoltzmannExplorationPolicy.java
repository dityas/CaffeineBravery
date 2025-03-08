/*
 *	THINC Lab at UGA | Cyber Deception Group
 *
 *	Author: Aditya Shinde
 * 
 *	email: shinde.aditya386@gmail.com
 */
package thinclab.policy;

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

    public BoltzmannExplorationPolicy(List<Integer> stateIndices, float conf) {
        super(stateIndices);
        this.conf = conf;
    }

    public BoltzmannExplorationPolicy(Collection<AlphaVector> alphaVectors,
            List<Integer> stateIndices, float conf) {
        super(alphaVectors, stateIndices);
        this.conf = conf;
    }

    @Override
    public int getBestActionIndex(DD belief) {

        var vals = this.stream()
            .map(v -> DDOP.dotProduct(belief, v.getVector(), stateIndices) * conf)
            .map(v -> (float) Math.exp(v))
            .collect(Collectors.toList());

        var sum = vals.stream().reduce(0.0f, (a, b) -> a + b);
        var probs = vals.stream().map(v -> v / sum).collect(Collectors.toList());

        var best = DDOP.sample(probs);
        LOGGER.debug("Sampled action with prob %s", probs.get(best));

        if (best == -1)
            throw new RuntimeException("Could not sample an action");

        return this.get(best).getActId();
    }

}
