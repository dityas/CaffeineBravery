/*
 *	THINC Lab at UGA | Cyber Deception Group
 *
 *	Author: Aditya Shinde
 * 
 *	email: shinde.aditya386@gmail.com
 */
package thinclab.models.datastructures;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import thinclab.DDOP;
import thinclab.legacy.DD;
import thinclab.legacy.Global;
import thinclab.models.PBVISolvablePOMDPBasedModel;
import thinclab.models.POMDP;
import thinclab.models.IPOMDP.IPOMDP;
import thinclab.policy.AlphaVectorPolicy;
import thinclab.simulator.Simulator;
import thinclab.solver.SymbolicPerseusSolver;
import thinclab.utils.Jsonable;
import thinclab.utils.Tuple;

/*
 * @author adityas
 *
 */
public class PolicyGraph implements Jsonable, Serializable {

    final public HashMap<Integer, PolicyNode> nodeMap = new HashMap<>();
    final public HashMap<Tuple<Integer, List<Integer>>, Integer> edgeMap;
    final public HashMap<List<Integer>, List<String>> edgeLabelMap;
    public HashMap<Integer, HashMap<Integer, Integer>> adjMap = new HashMap<>();

    private static final Logger LOGGER = 
        LogManager.getFormatterLogger(PolicyGraph.class);

    public PolicyGraph(PBVISolvablePOMDPBasedModel m, AlphaVectorPolicy p) {

        // First create the action-observation space
        edgeMap = new HashMap<>();
        for (int a = 0; a < m.A().size(); a++) {
            for (var o: m.oAll) {
                var edge = Tuple.of(a, o);
                edgeMap.put(edge, edgeMap.size());
            }
        }

        // populate the edge label map with observation labels for printing
        // to dot file later
        edgeLabelMap = new HashMap<>();
        for (var o: m.oAll) {

            var namedObs = new ArrayList<String>();
            for (int i = 0; i < o.size(); i++) {

                var varIndex = m.i_Om.get(i) - 1;
                var name = Global.varNames.get(varIndex);
                var obsName = Global.valNames.get(varIndex).get(o.get(i) - 1);

                namedObs.add(Tuple.of(name, obsName).toString());
            }

            if (!edgeLabelMap.containsKey(o))
                edgeLabelMap.put(o, namedObs);
        }
    }

    @Override
    public String toString() {

        var gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(toJson());
    }

    @Override
    public JsonObject toJson() {

        var gson = new GsonBuilder().setPrettyPrinting().create();
        var json = new JsonObject();

        var nodeJson = new JsonObject();
        var edgeJson = new JsonObject();

        for (var node : adjMap.keySet()) {

            nodeJson.add(node.toString(), nodeMap.get(node).toJson());

            var thisEdgeJson = new JsonObject();
            for (var edge : edgeMap.entrySet()) {

                if (adjMap.get(node).containsKey(edge.getValue())) {

                    var _node = nodeMap.get(adjMap.get(node).get(edge.getValue())).nodeId;

                    thisEdgeJson.add(edgeLabelMap.get(edge.getKey()._1()).toString(),
                            gson.toJsonTree(Integer.toString(_node)));
                }
            }

            // edgeJson.add(Tuple.of(nodeMap.get(node).actName, nodeMap.get(node).alphaId).toString(), thisEdgeJson);
            edgeJson.add(String.format("%s", nodeMap.get(node).nodeId), thisEdgeJson);
        }

        json.add("nodes", gson.toJsonTree(nodeJson));
        json.add("edges", edgeJson);

        return json;
    }

    public PolicyNode getNextNode(int nodeId, int action,
            List<Integer> obs) {

        var edge = edgeMap.get(Tuple.of(action, obs));

        if (adjMap.containsKey(nodeId) 
                && adjMap.get(nodeId).containsKey(edge)) {

            var nextNodeId = adjMap.get(nodeId).get(edge);
            return nodeMap.get(nextNodeId);
        }

        return null;
    }

    public void makeGraphFromSims(List<DD> beliefs,
            PBVISolvablePOMDPBasedModel m,
            AlphaVectorPolicy Vn) {

        for (var startBelief: beliefs) {

            var beliefSet = new ArrayList<DD>();
            beliefSet.add(startBelief);
            for (int h = 0; h < 1000; h++) {

                if (beliefSet.isEmpty())
                    break;

                var b = beliefSet.remove(0);
                var i = Vn.getBestVectorIndex(b);
                var v = Vn.get(i);
                var bestAction = v.getActId();

                if (nodeMap.containsKey(i))
                    continue;

                LOGGER.info("At node %s for action %s with value %s",
                        i, m.A().get(bestAction),
                        DDOP.dotProduct(v.getVector(), b, m.i_S()));

                // Make policy node
                var n = new PolicyNode(i, bestAction, m.A().get(bestAction));
                n.nodeId = i;
                nodeMap.put(i, n);

                // For all o, link with best node
                var likelihoods = m.obsLikelihoods(b, bestAction);
                for (var o: m.oAll) {

                    var edgeIdx = edgeMap.get(Tuple.of(bestAction, o));
                    var prob = DDOP.restrict(likelihoods, m.i_Om_p(), o).getVal();
                    // If o is impossible, loop back to i
                    if (prob < 1e-6)
                        updateAgjMap(i, edgeIdx, i);

                    else { // Link to best vector for updated belief
                        var nextBelief = m.beliefUpdate(b, bestAction, o);
                        int bestNode = Vn.getBestVectorIndex(nextBelief);

                        if (!adjMap.containsKey(i) || !adjMap.get(i).containsKey(edgeIdx))
                            updateAgjMap(i, edgeIdx, bestNode);
                        
                        if (beliefSet.size() < 300)
                            beliefSet.add(nextBelief);
                    }
                }
            }
        }

        for (var b: beliefs) {
            int bestVec = Vn.getBestVectorIndex(b);
            nodeMap.get(bestVec).start = true;
        }
    }

    public void makeFSC(List<DD> beliefs, PBVISolvablePOMDPBasedModel m,
            AlphaVectorPolicy Vn) {

        for (int i = 0; i < Vn.size(); i++) {

            var v = Vn.get(i);
            var bestAction = v.getActId();
            LOGGER.info("Adding node %s for action %s with value %s",
                    i, m.A().get(bestAction), v.getVal());

            // Make policy node
            var n = new PolicyNode(i, bestAction, m.A().get(bestAction));
            n.nodeId = i;
            nodeMap.put(i, n);

            var witness = v.getWitness();
            var bestVecIdx = Vn.getBestVectorIndex(witness);
            if (bestVecIdx != i) {
                LOGGER.error("[!] Alpha vector not optimal at its own witness");
                LOGGER.debug("Belief is %s", DDOP.factors(witness, m.i_S()));
                LOGGER.debug("Vn suggests %s but b is a witness for %s",
                        bestVecIdx, i);
                LOGGER.debug("Actions are %s / %s",
                        m.A().get(Vn.get(bestVecIdx).getActId()),
                        m.A().get(Vn.get(i).getActId()));
                LOGGER.debug("Vals are %s / %s",
                        DDOP.dotProduct(witness, Vn.get(bestVecIdx).getVector(),
                            Vn.stateIndices),
                        DDOP.dotProduct(witness, Vn.get(i).getVector(),
                            Vn.stateIndices));
            }

            // For all o, link with best node
            var likelihoods = m.obsLikelihoods(witness, bestAction);
            for (var o: m.oAll) {

                var edgeIdx = edgeMap.get(Tuple.of(bestAction, o));
                var prob = DDOP.restrict(likelihoods, m.i_Om_p(), o).getVal();
                // If o is impossible, loop back to i
                if (prob < 1e-6)
                    updateAgjMap(i, edgeIdx, i);

                else { // Link to best vector for updated belief
                    var nextBelief = m.beliefUpdate(witness, bestAction, o);
                    int bestNode = Vn.getBestVectorIndex(nextBelief);
                    updateAgjMap(i, edgeIdx, bestNode);
                }
            }
        }

        for (var b: beliefs) {
            int bestVec = Vn.getBestVectorIndex(b);
            nodeMap.get(bestVec).start = true;
        }
    }

    public static PolicyGraph getPolicyGraphFromModel(final List<DD> b_i,
            PBVISolvablePOMDPBasedModel m) {

        var policy = 
            new SymbolicPerseusSolver<>(m).solve(b_i, 100, 10);

        return PolicyGraph.makePolicyGraph(b_i, m, policy);
    }

    public static PolicyGraph makePolicyGraph(final List<DD> b_is,
            PBVISolvablePOMDPBasedModel m, AlphaVectorPolicy p) {

        // Make empty policy graph
        var G = new PolicyGraph(m, p);
        //G.makeFSC(b_is, m, p);
        G.makeGraphFromSims(b_is, m, p);
        if(PolicyGraph.verify(m, b_is, G, p, 10, 100))
            return G;

//        var t = new PolicyTreeFSC(b_is, m, p, 8);
//        G.convertToTree(t, m);
        return G;
    }

    public float evalIPOMDPRollout(IPOMDP m, DD initBelief,
            AlphaVectorPolicy Vn, int iter, int len) {

        float totalR = 0.0f;
        int printIter = (int) (iter / 10);

        for (int i = 0; i < iter; i++) {

            var oppModelIdx = Global.random.nextInt(m.framesj.size());
            var oppFrame = m.ecThetas.get(oppModelIdx);
            var oppBeliefs = oppFrame.jBeliefs;
            var oppStartBelief = oppBeliefs.get(
                    Global.random.nextInt(oppBeliefs.size()));
            var oppGraph = oppFrame.G;
            var oppVn = oppFrame.Vn;
            var oppModel = oppFrame.m;

            var oppNode = oppGraph.nodeMap.get(
                    oppVn.getBestVectorIndex(oppStartBelief));

            var sampledState = DDOP.sample(List.of(initBelief), m.i_S());
            DD state = DDOP.ddFromVals(sampledState._0(), sampledState._1());
            DD currentBelief = initBelief;
            var node = nodeMap.get(Vn.getBestVectorIndex(initBelief));

            float reward = 0.0f;

            var stateIndices = new ArrayList<>(m.i_S());
            stateIndices.remove(stateIndices.size() - 1);

            state = DDOP.addMultVarElim(List.of(state),
                    List.of(m.i_EC));

            var sim = new Simulator(stateIndices, m.i_A, oppModel.i_A,
                    m.i_Om_p, oppModel.i_Om_p, m.T(), m.O(), oppModel.O());
            sim.setState(state);

            for (int l = 0; l < len; l++) {

                int act = node.actId;
                var R = m.jointR.get(act);

                var actJ = oppNode.actId;
                R = DDOP.restrict(R, List.of(oppModel.i_A), List.of(actJ));

                reward += DDOP.dotProduct(R, sim.state, sim.stateIndices);

                // update state
                var obs = sim.step(act, actJ);
//                var factors = new ArrayList<>(m.T().get(act));
//                factors.add(state);
//
//                var s_p = DDOP.addMultVarElim(factors, m.i_S());
//                s_p = DDOP.restrict(s_p, List.of(oppModel.i_A), List.of(actJ));
//
//                s_p = DDOP.primeVars(s_p, -(Global.NUM_VARS / 2));
//
//                var nextState = DDOP.sample(List.of(s_p), m.i_S());
//                state = DDOP.ddFromVals(nextState._0(), nextState._1());
//
//                // get obs
//                var obsFn = m.O().get(act);
//                obsFn = DDOP.restrict(obsFn, List.of(oppModel.i_A), List.of(actJ));
//
//                var obsFactors = new ArrayList<>(obsFn);
//                obsFactors.add(DDOP.primeVars(state, (Global.NUM_VARS / 2)));
//
//                var obsDist = DDOP.addMultVarElim(obsFactors, m.i_S_p());
//                var o = DDOP.sample(List.of(obsDist), m.i_Om_p());
                
                currentBelief = m.beliefUpdate(currentBelief, act, obs._0()._1());
                node = getNextNode(node.nodeId, act, obs._0()._1());
                oppNode = oppGraph.getNextNode(oppNode.nodeId, actJ, obs._1()._1());
                if (node == null || oppNode == null) {
                    LOGGER.error("FSM broke after observing %s, %s in %s with belief %s",
                            obs._0().toJson(),
                            obs._1().toJson(),
                            DDOP.toJson(state, new ArrayList<>(state.getVars())),
                            DDOP.toJson(currentBelief, m.i_S()));
                    break;
                }
            }

            if (i % printIter == 0)
                LOGGER.debug("Got reward %s against %s", reward, oppModel.getName());

            totalR += reward;
        }

        return totalR / ((float) iter);
    }

    public float evalPOMDPRollout(POMDP m, DD initBelief,
            AlphaVectorPolicy Vn, int iter, int len) {

        float totalR = 0.0f;

        for (int i = 0; i < iter; i++) {

            var sampledState = DDOP.sample(List.of(initBelief), m.i_S());
            DD state = DDOP.ddFromVals(sampledState._0(), sampledState._1());
            DD currentBelief = initBelief;
            var node = nodeMap.get(Vn.getBestVectorIndex(initBelief));

            float reward = 0.0f;

            for (int l = 0; l < len; l++) {

                int act = node.actId;
                var R = m.R().get(act);

                reward += DDOP.dotProduct(R, state, state.getVars());

                // update state
                var factors = new ArrayList<>(m.T().get(act));
                factors.add(state);

                var s_p = DDOP.addMultVarElim(factors, m.i_S());
                s_p = DDOP.primeVars(s_p, -(Global.NUM_VARS / 2));

                var nextState = DDOP.sample(List.of(s_p), m.i_S());
                state = DDOP.ddFromVals(nextState._0(), nextState._1());

                // get obs
                var obsFn = m.O().get(act);
                var obsFactors = new ArrayList<>(obsFn);
                obsFactors.add(DDOP.primeVars(state, (Global.NUM_VARS / 2)));

                var obsDist = DDOP.addMultVarElim(obsFactors, m.i_S_p());
                var o = DDOP.sample(List.of(obsDist), m.i_Om_p());
                
                currentBelief = m.beliefUpdate(currentBelief, act, o._1());
                node = getNextNode(node.nodeId, act, o._1());
                if (node == null) {
                    LOGGER.error("FSM broke after observing %s is %s with belief %s",
                            new Observation(o).toJson(),
                            DDOP.toJson(state, new ArrayList<>(state.getVars())),
                            DDOP.toJson(currentBelief, m.i_S()));
                    break;
                }
            }

            totalR += reward;
        }

        return totalR / ((float) iter);
    }

    public float evalWithRollout(PBVISolvablePOMDPBasedModel m, DD initBelief,
            AlphaVectorPolicy Vn, int iter, int len) {

        if (m instanceof IPOMDP ipomdp)
            return evalIPOMDPRollout(ipomdp, initBelief, Vn, iter, len);

        else return evalPOMDPRollout((POMDP) m, initBelief, Vn, iter, len);

//        float totalR = 0.0f;
//
//        for (int i = 0; i < iter; i++) {
//
//            var sampledState = DDOP.sample(List.of(initBelief), m.i_S());
//            DD state = DDOP.ddFromVals(sampledState._0(), sampledState._1());
//            DD currentBelief = initBelief;
//            var node = nodeMap.get(Vn.getBestVectorIndex(initBelief));
//
//            float reward = 0.0f;
//
//            if (m instanceof IPOMDP ipomdp)
//                state = DDOP.addMultVarElim(List.of(state),
//                        List.of(ipomdp.i_EC));
//
//            for (int l = 0; l < len; l++) {
//
//                int act = node.actId;
//                var R = m.R().get(act);
//
//                Tuple<List<Integer>, List<Integer>> sampledActJ = null;
//                if (m instanceof IPOMDP ipomdp) {
//                    DD actJDist = DDOP.addMultVarElim(
//                            List.of(ipomdp.PAjGivenEC, currentBelief),
//                            ipomdp.i_S());
//
//                    sampledActJ = DDOP.sample(actJDist, ipomdp.i_Aj);
//                    R = DDOP.restrict(R, sampledActJ._0(), sampledActJ._1());
//                }
//
//                reward += DDOP.dotProduct(R, state, state.getVars());
//
//                // update state
//                var factors = new ArrayList<>(m.T().get(act));
//                factors.add(state);
//
//                var s_p = DDOP.addMultVarElim(factors, m.i_S());
//                if (m instanceof IPOMDP ipomdp)
//                    s_p = DDOP.restrict(s_p, sampledActJ._0(), sampledActJ._1());
//
//                s_p = DDOP.primeVars(s_p, -(Global.NUM_VARS / 2));
//
//                var nextState = DDOP.sample(List.of(s_p), m.i_S());
//                state = DDOP.ddFromVals(nextState._0(), nextState._1());
//
//                // get obs
//                var obsFn = m.O().get(act);
//                if (m instanceof IPOMDP ipomdp)
//                    obsFn = DDOP.restrict(obsFn, sampledActJ._0(), sampledActJ._1());
//
//                var obsFactors = new ArrayList<>(obsFn);
//                obsFactors.add(DDOP.primeVars(state, (Global.NUM_VARS / 2)));
//
//                var obsDist = DDOP.addMultVarElim(obsFactors, m.i_S_p());
//                var o = DDOP.sample(List.of(obsDist), m.i_Om_p());
//                
//                currentBelief = m.beliefUpdate(currentBelief, act, o._1());
//                node = getNextNode(node.nodeId, act, o._1());
//                if (node == null) {
//                    LOGGER.error("FSM broke after observing %s is %s with belief %s",
//                            new Observation(o).toJson(),
//                            DDOP.toJson(state, new ArrayList<>(state.getVars())),
//                            DDOP.toJson(currentBelief, m.i_S()));
//                    break;
//                }
//            }
//
//            totalR += reward;
//        }
//
//        return totalR / ((float) iter);
    }

    public static boolean verify(PBVISolvablePOMDPBasedModel m, final List<DD> B,
            PolicyGraph G, AlphaVectorPolicy Vn, int maxLen, int numIter) {

        LOGGER.info("[.] Verifying PolicyGraph FSC with rollouts");

        for (var b: B) {

            for (int i = 0; i < numIter; i++) {

                int bestVec = Vn.getBestVectorIndex(b);
                DD belief = b;
                var node = G.nodeMap.get(bestVec);

                for (int l = 0; l < maxLen; l++) {
                    
                    int bestAct = Vn.getBestActionIndex(belief);
                    if (node == null || node.actId != bestAct) {
                        var bestVecFSC = Vn.get(node.nodeId);
                        var FSCVal = DDOP.dotProduct(belief, bestVecFSC.getVector(),
                                m.i_S());
                        var bestVecVn = Vn.get(Vn.getBestVectorIndex(belief));
                        var VnVal = DDOP.dotProduct(belief, bestVecVn.getVector(),
                                m.i_S());
                        LOGGER.error("[!!!] FSC diverges at iter %s step %s: Vn: %s / FSC: %s | Vn: %s / FSC: %s",
                                i, l, m.A().get(bestAct), m.A().get(node.actId), VnVal, FSCVal);
                        return false;
                    }

                    // sample observation for rollout
                    var likelihoods = m.obsLikelihoods(belief, node.actId);
                    var o = DDOP.sample(List.of(likelihoods), m.i_Om_p());

                    // update belief
                    belief = m.beliefUpdate(belief, bestAct, o._1());

                    // update FSC node
                    node = G.getNextNode(node.nodeId, node.actId, o._1());
                }
            }
        }

        LOGGER.info("[+] PolicyGraph verified with %s rollouts of len %s",
                numIter, maxLen);
        return true;
    }

    public void convertToTree(PolicyTreeFSC t, PBVISolvablePOMDPBasedModel m) {

        nodeMap.clear();
        adjMap.clear();

        nodeMap.putAll(t.nodeMap);
        for (var k: nodeMap.keySet()) {
            int actId = nodeMap.get(k).actId;
            nodeMap.get(k).actName = m.A().get(actId);
        }

        var reverseMap = new HashMap<Integer, List<Integer>>();

        for (var e: t.observationSpace.entrySet())
            reverseMap.put(e.getValue(), e.getKey());

        for (var adjEntry: t.adjMap.entrySet()) {
            
            int src = adjEntry.getKey();
            for (var edges: adjEntry.getValue().entrySet()) {

                int srcAct = t.nodeMap.get(src).actId;
                var obs = reverseMap.get(edges.getKey());
                int dest = edges.getValue();

                int edgeIdx = edgeMap.get(Tuple.of(srcAct, obs));

                if (src == -1)
                    continue;

                if(dest != -1)
                    updateAgjMap(src, edgeIdx, dest);
            }
        }

        for (var n: nodeMap.keySet()) {

            if (!adjMap.containsKey(n))
                adjMap.put(n, new HashMap<>());
        }
    }

    private void updateAgjMap(int src, int edge, int dest) {

        if (!adjMap.containsKey(src))
            adjMap.put(src, new HashMap<Integer, Integer>());

        adjMap.get(src).put(edge, dest);
    }

}
