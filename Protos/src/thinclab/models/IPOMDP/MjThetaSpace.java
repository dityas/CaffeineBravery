/*
 *	THINC Lab at UGA | Cyber Deception Group
 *
 *	Author: Aditya Shinde
 * 
 *	email: shinde.aditya386@gmail.com
 */
package thinclab.models.IPOMDP;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import thinclab.legacy.DD;
import thinclab.legacy.Global;
import thinclab.models.PBVISolvablePOMDPBasedModel;
import thinclab.models.datastructures.PolicyGraph;
import thinclab.models.datastructures.PolicyNode;
import thinclab.policy.AlphaVectorPolicy;
import thinclab.solver.SymbolicPerseusSolver;
import thinclab.utils.Tuple;
import thinclab.utils.Tuple3;
import thinclab.utils.Utils;

/*
 * @author adityas
 *
 */
public class MjThetaSpace implements Frame<PolicyNode>, Serializable {

    final public int frame;
    final public PolicyGraph G;
    final public PBVISolvablePOMDPBasedModel m;
    final public SymbolicPerseusSolver<PBVISolvablePOMDPBasedModel> s;
    final public AlphaVectorPolicy Vn;

    final private static Logger LOGGER = 
        LogManager.getFormatterLogger(MjThetaSpace.class);

    public MjThetaSpace(List<DD> b_j, int frame, 
            PBVISolvablePOMDPBasedModel m) {

        this.frame = frame;
        this.m = m;

        // switch to biases here
        if (Global.MODEL_BIASED && this.m.getName().startsWith("att")) {
            LOGGER.info("Modeling %s as a biased agent", this.m.getName());
            m.setBiased();
        }

        this.s = new SymbolicPerseusSolver<>(this.m);

        var b_js = new ArrayList<DD>();

        if (m instanceof IPOMDP)
            b_js.addAll(b_j.stream()
                    .map(d -> ((IPOMDP) m).getECDDFromMjDD(d))
                    .collect(Collectors.toList()));

        else
            b_js.addAll(b_j);

        var policy = getPolicy(b_js, m);
        if (policy != null) {
            LOGGER.info("[*] Loaded policy for %s", m.getName());
            Vn = policy;
        }

        else Vn = solvePolicy(b_js, m);

        this.G = PolicyGraph.makePolicyGraph(b_js, m, Vn);

        LOGGER.debug(
                "Graph has %s nodes and %s node sources", 
                G.nodeMap.size(), G.adjMap.size());
        LOGGER.info(
                "MjTheta space for frame %s initialized with %s EQ classes",
                frame, G.adjMap.size());

        Utils.serializePolicyGraph(G, m.getName());
    }

    public AlphaVectorPolicy solvePolicy(List<DD> b_j,
            PBVISolvablePOMDPBasedModel m) {

        var modelPath = Global.RESULTS_DIR.toAbsolutePath().toString();
        var policyFile = String.format("%s/%s.policy", modelPath, m.getName());
        var policy = s.solve(b_j, 100, 10);

        try {
            var oos = new ObjectOutputStream(new FileOutputStream(policyFile));
            oos.writeObject(policy);
            oos.close();
            LOGGER.info("[*] Policy for %s serialized in %s",
                    m.getName(), policyFile);
        } catch (Exception e) {
            LOGGER.error("[!] Could not store in %s", policyFile);
            LOGGER.error("[!] %s", e.getMessage());
        }

        return policy;
    }

    public AlphaVectorPolicy getPolicy(List<DD> b_j,
            PBVISolvablePOMDPBasedModel m) {

        var modelPath = Global.RESULTS_DIR.toAbsolutePath().toString();
        var policyFile = String.format("%s/%s.policy", modelPath, m.getName());

        try {
            if (Files.exists(Path.of(policyFile))) {
                var ois = new ObjectInputStream(new FileInputStream(policyFile));
                var policy = (AlphaVectorPolicy) ois.readObject();
                ois.close();

                return policy;
            }

            else return null;
        } catch (Exception e) {
            LOGGER.error("[!] Could not open %s", policyFile);
            LOGGER.error("[!] %s", e.getMessage());

            return null;
        }
    }

    @Override
    public List<MjRepr<PolicyNode>> allModels() {

        return G.adjMap.keySet().stream()
            .map(n -> new MjRepr<>(frame, G.nodeMap.get(n)))
            .collect(Collectors.toList());
    }

    @Override
    public List<MjRepr<PolicyNode>> bMj() {

        return null;
    }

    @Override
    public List<Tuple3<MjRepr<PolicyNode>, List<Integer>, MjRepr<PolicyNode>>> getTriples() {

//        for (var k: G.nodeMap.keySet())
//            LOGGER.debug("%s: %s", k, G.nodeMap.get(k));
//
//        for (var k: G.adjMap.keySet()) {
//            for (var e: G.adjMap.get(k).entrySet()) 
//                LOGGER.debug("%s: %s: %s", k, e.getKey(), e.getValue());
//        }

        var triples = G.adjMap.entrySet().stream()
            .flatMap(e -> G.edgeMap.entrySet().stream().map(f ->
                        {

                            // for each edge, make a list of indices of child vals
                            var edge = new ArrayList<Integer>(
                                    f.getKey()._1().size() + 1);
                            edge.add(f.getKey()._0() + 1);
                            edge.addAll(f.getKey()._1());

                            var mj_p = e.getValue().get(f.getValue());

                            // if it is a leaf node, loop it back
                            if (mj_p == null)
                                return Tuple.of(
                                        new MjRepr<>(frame, 
                                            G.nodeMap.get(e.getKey())), 
                                        (List<Integer>) edge,
                                        new MjRepr<>(frame, 
                                            G.nodeMap.get(e.getKey())));

                            else
                                return Tuple.of(
                                        new MjRepr<>(frame, 
                                            G.nodeMap.get(e.getKey())), 
                                        (List<Integer>) edge,
                                        new MjRepr<>(frame, 
                                            G.nodeMap.get(mj_p)));

                        })).collect(Collectors.toList());

        return triples;
    }
}
