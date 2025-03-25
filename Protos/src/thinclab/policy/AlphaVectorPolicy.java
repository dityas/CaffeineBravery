/*
 *	THINC Lab at UGA | Cyber Deception Group
 *
 *	Author: Aditya Shinde
 * 
 *	email: shinde.aditya386@gmail.com
 */
package thinclab.policy;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import thinclab.DDOP;
import thinclab.legacy.DD;
import thinclab.legacy.DDleaf;
import thinclab.models.PBVISolvablePOMDPBasedModel;
import thinclab.utils.Jsonable;
import thinclab.utils.LispExpressible;
import thinclab.utils.Tuple;

/*
 * @author adityas
 *
 */
public class AlphaVectorPolicy extends ArrayList<AlphaVector> implements 
Policy<DD>, Jsonable, LispExpressible, Serializable {

    private static final Logger LOGGER = 
        LogManager.getFormatterLogger(AlphaVectorPolicy.class);

    public final List<Integer> stateIndices;

    public AlphaVectorPolicy(List<Integer> stateIndices) {
        super();
        this.stateIndices = stateIndices;
    }

    public AlphaVectorPolicy(Collection<AlphaVector> alphaVectors,
            List<Integer> stateIndices) {
        super(alphaVectors);
        this.stateIndices = stateIndices;
    }

    public List<DD> getAsDDList() {

        return stream()
            .map(v -> v.getVector())
            .collect(Collectors.toList());
    }

    public int getBestVectorIndex(DD b) {

        float maxVal = Float.NEGATIVE_INFINITY;
        int bestIndex = -1;

        for (int i = 0; i < this.size(); i++) {

            float val = DDOP.dotProduct(b, 
                    this.get(i).getVector(), stateIndices);

            if (val > maxVal) {

                maxVal = val;
                bestIndex = i;
            }
        }

        if (bestIndex < 0) {
            LOGGER.error("Error while getting best action at %s", b);
            throw new RuntimeException(
                    String.format("Could not find best vec for %s", b));
        }

        return bestIndex;
    }

    public static AlphaVectorPolicy getLowerBound(PBVISolvablePOMDPBasedModel m) {

        var policy = new AlphaVectorPolicy(m.i_S());

        var Rmin = Float.POSITIVE_INFINITY;
        var worstAction = -1;

        for (int a = 0; a < m.A().size(); a++) {

            var Ra = DDOP.minAll(m.R().get(a));

            if (Ra < Rmin) {
                Rmin = Ra;
                worstAction = a;
            }
        }

        LOGGER.info("Lower bound is R(S, %s)=%s",
                m.A().get(worstAction), Rmin);
        var vector = DDleaf.getDD((1.0f/(1.0f - m.discount)) * Rmin);
        policy.add(new AlphaVector(worstAction, vector, vector.getVal()));
        return policy;
    }

    public List<Float> getValuesAtWitnessPoints() {
        return stream().map(v -> v.getVal()).collect(Collectors.toList());
    }

    @Override
    public int getBestActionIndex(DD belief) {
        /*
         * Dot all vectors with given belief and return action Id of max
         */

        float bestVal = Float.NEGATIVE_INFINITY;
        AlphaVector best = null;

        for (var vec: this) {

            float val = DDOP.dotProduct(belief, 
                    vec.getVector(), stateIndices);

            if (val > bestVal) {
                bestVal = val;
                best = vec;
            }

        }

        return best.getActId();
    }

    @Override
    public String toString() {
        return stream().map(v -> v.toString())
            .collect(Collectors.toList()).toString();
    }

    @Override
    public JsonElement toJson() {

        var json = new JsonArray();

        return json;
    }

    @Override
    public Object toLisp() {

        var policyList = new ArrayList<Object>(size());
        return policyList;
    }

    public static AlphaVectorPolicy fromJson(JsonElement json) {

        if (json instanceof JsonArray ja) {

            var aVecs = new ArrayList<Tuple<Integer, DD>>();

            ja.forEach(j -> {

                var _jo = j.getAsJsonObject();
                var actId = _jo.get("actId").getAsInt();
                var alpha = DD.fromJson(_jo.get("alpha"));

                if (alpha.isPresent())
                    aVecs.add(Tuple.of(actId, alpha.get()));

                else
                    LOGGER.error("Error while loading %s", _jo);
            });

            return null;
        }

        return null;
    }

    public Collection<String> getActions(PBVISolvablePOMDPBasedModel m) {

        var solnSet = stream()
            .map(a -> m.A().get(a.getActId()))
            .collect(Collectors.toSet());

        return solnSet;
    }
}
