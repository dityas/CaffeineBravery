package thinclab.models.datastructures;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import thinclab.DDOP;
import thinclab.legacy.DD;
import thinclab.models.PBVISolvablePOMDPBasedModel;
import thinclab.policy.AlphaVectorPolicy;
import thinclab.utils.Tuple;
import thinclab.utils.Tuple3;

public class PolicyTreeFSC {


    private static final Logger LOGGER = 
        LogManager.getFormatterLogger(PolicyTreeFSC.class);

    // node id to PolicyNode map
    public HashMap<Integer, PolicyNode> nodeMap = new HashMap<>();

    // Adjancency map to store the FSC
    public HashMap<Integer, HashMap<Integer, Integer>> adjMap =
        new HashMap<>();

    // This is actual just an index over the action-observation space
    // of the agent. I was really dumb 5 years ago, so I named it edgeMap in
    // the PolicyGraph implementation (which is also terribly written). So now,
    // I am going to name this edgeMap again just for upward compatibility.
    public HashMap<Tuple<Integer, List<Integer>>, Integer> edgeMap =
        new HashMap<>();

    public HashMap<List<Integer>, Integer> observationSpace =
        new HashMap<>();

    private int nodeId = 1;

    public PolicyTreeFSC(final List<DD> beliefs,
            final PBVISolvablePOMDPBasedModel ipomdp,
            final AlphaVectorPolicy Vn, int maxDepth) {

        LOGGER.info("Making approximate FSC for IPOMDP %s for horizon %s",
                ipomdp.getName(), maxDepth);

        makeActionObservationIndex(ipomdp);
        LOGGER.info("Observation space for %s is %s", ipomdp.getName(),
                observationSpace.size());

        if (observationSpace.size() > 5) {
            maxDepth = (int) Math.ceil(Math.log(5000.0) / 
                    Math.log((double) observationSpace.size()));

            LOGGER.info("Changing max depth to %s", maxDepth);
        }
        makePolicyTree(beliefs, ipomdp, maxDepth + 1, Vn);
        //makeTrees(beliefs, ipomdp, maxDepth, Vn);
        LOGGER.info("Policy tree for %s contains %s nodes", ipomdp.getName(),
                this.nodeId);
        makeFSC(ipomdp.oAll);
        sanityCheck();
    }

    private void sanityCheck() {

        LOGGER.debug(adjMap.size());
        for (var n: adjMap.keySet()) {
            for (var o: adjMap.get(n).keySet()) {
                var dest = adjMap.get(n).get(o);

                if (dest == -1)
                    continue;

                if (!nodeMap.containsKey(n) || !nodeMap.containsKey(dest)) {
                    throw new RuntimeException(
                            String.format("Node %s or %s not in %s",
                                n, dest, nodeMap));
                }
            }
        }
    }

    private void makeActionObservationIndex(final PBVISolvablePOMDPBasedModel ipomdp) {

        var allObservations = ipomdp.oAll;

        // Populate observationIndex
        for (var o: allObservations)
            observationSpace.put(o, observationSpace.size());

        for (int a = 0; a < ipomdp.A().size(); a++) {
            for (var o: allObservations)
                edgeMap.put(Tuple.of(a, o), edgeMap.size());
        }
    }

    private int nextNodeId() {
        var prev = nodeId;
        nodeId += 1;
        return prev;
    }

    private void updateFSC(int srcId, int action, List<Integer> obs,
            int destId) {

        if (!adjMap.containsKey(srcId))
            adjMap.put(srcId, new HashMap<>());

        var edge = Tuple.of(action, obs);
        var edgeIdx = edgeMap.get(edge);
        adjMap.get(srcId).put(edgeIdx, destId);
    }

    private void updateFSC(int srcId, List<Integer> obs,
            int destId) {

        if (!adjMap.containsKey(srcId))
            adjMap.put(srcId, new HashMap<>());

        var edgeIdx = observationSpace.get(obs);
        adjMap.get(srcId).put(edgeIdx, destId);
    }

    private int getNodeDescendent(int n, List<Integer> o) {

        int edgeIdx = observationSpace.get(o);

        if (!adjMap.get(n).containsKey(edgeIdx))
            return 0; // Placeholder because real nodeIDs start from 1

        return adjMap.get(n).get(edgeIdx);
    }

    private List<Integer> getAllDescendents(int n) {

        var dequeue = new ArrayDeque<Integer>();
        dequeue.addLast(n);

        var removeList = new ArrayList<Integer>();

        while (!dequeue.isEmpty()) {

            var toRemove = dequeue.removeFirst();

            if (toRemove < 0)
                continue;

            removeList.add(toRemove);
            if (adjMap.containsKey(toRemove)) {
                var children = adjMap.get(toRemove).values();
                dequeue.addAll(children);
            }
        }

        return removeList;
    }

    public boolean checkCPEquivalence(int n1, int n2, List<List<Integer>> O) {

        if (n1 == 0 || n2 == 0)
            LOGGER.error("Zero node does not exits, n1: %s, n2: %s", n1, n2);

        if (nodeMap.get(n1).actId != nodeMap.get(n2).actId)
            return false;

        for (var o: O) {
            
            var n1Desc = getNodeDescendent(n1, o);
            var n2Desc = getNodeDescendent(n2, o);

            if (n1Desc == 0 || n2Desc == 0) {
                if (n1Desc != n2Desc)
                    return false;
                else
                    continue;
            }

            if (n1Desc != -1 && !checkCPEquivalence(n1Desc, n2Desc, O))
                return false;
        }

        return true;
    }

    public void makeFSC(List<List<Integer>> O) {

        for (int i = 1; i < nodeId; i++) {

            if (!nodeMap.containsKey(i))
                continue;

            for (int j = 1; j < i; j++) {

                if (!nodeMap.containsKey(i) || !nodeMap.containsKey(j))
                    continue;

                if (checkCPEquivalence(i, j, O)) {
                    var desc = getAllDescendents(i);
                    pruneTree(desc, i, j);
                }
            }
        }

        LOGGER.info("Pruned FSC contains %s nodes", adjMap.size());
    }

    private void pruneTree(List<Integer> pruneIds, int from, int to) {
        for (int i: pruneIds) {
            nodeMap.remove(i);
            adjMap.remove(i);
        }

        for (var n: adjMap.keySet()) {
            for (var o: adjMap.get(n).keySet()) {

                if (adjMap.get(n).get(o) == from)
                    adjMap.get(n).put(o, to);
            }
        }
    }

    public int makePolicyTreeDFS(DD belief, int depth,
            final PBVISolvablePOMDPBasedModel ipomdp,
            final AlphaVectorPolicy Vn) {

        var n = nextNodeId(); // node id
        
        int bestAction = Vn.getBestActionIndex(belief);
        int alphaId = DDOP.bestAlphaIndex(Vn, belief);

        // Make new node
        PolicyNode pnode = new PolicyNode(alphaId, bestAction, "");
        if (true)
            pnode.start = true;

        pnode.nodeId = n;
        nodeMap.put(n, pnode);

        if (depth <= 0) {
            for (var obs: ipomdp.oAll)
                updateFSC(n, obs, -1);

            return n;
        }

        else {
            // Update belief for all possible observations
            var likelihoods = ipomdp.obsLikelihoods(belief, bestAction);
            for (var obs: ipomdp.oAll) {

                var prob = DDOP.restrict(likelihoods,
                        ipomdp.i_Om_p(), obs).getVal();

                // If observation is impossible for this belief, skip it
                if (prob < 1e-6f) 
                    continue;
                
                // Add new belief for action bestAction and observation obs
                // to the beliefsQueue
                var nextBelief = ipomdp.beliefUpdate(belief, bestAction, obs);
                var nextId = makePolicyTreeDFS(nextBelief, depth - 1, ipomdp, Vn);

                // Record policy tree edge
                updateFSC(n, obs, nextId);
            }

            return n;
        }
    }

    public void makeTrees(final List<DD> beliefs,
            final PBVISolvablePOMDPBasedModel ipomdp,
            final int maxDepth, final AlphaVectorPolicy Vn) {

        for (var b: beliefs)
            makePolicyTreeDFS(b, maxDepth, ipomdp, Vn);
    }

    public void makePolicyTree(final List<DD> beliefs,
            final PBVISolvablePOMDPBasedModel ipomdp,
            final int maxDepth, final AlphaVectorPolicy Vn) {

        // Populate belief deck with initial beliefs
        var beliefQueue = new ArrayDeque<Tuple3<DD, Integer, Integer>>();

        for (var b: beliefs)
            beliefQueue.addLast(Tuple.of(b, 0, nextNodeId()));

        while (!beliefQueue.isEmpty()) {

            var node = beliefQueue.removeFirst();
            var b = node._0(); // belief
            var d = node._1(); // depth
            var n = node._2(); // node id
            
            int bestAction = Vn.getBestActionIndex(b);
            int alphaId = DDOP.bestAlphaIndex(Vn, b);

            // Make new node
            PolicyNode pnode = new PolicyNode(alphaId, bestAction, "");
            if (d == 0)
                pnode.start = true;

            pnode.nodeId = n;
            nodeMap.put(n, pnode);

            if (d >= maxDepth - 1) {
                for (var obs: ipomdp.oAll)
                    updateFSC(n, obs, -1);
            }

            else {
                // Update belief for all possible observations
                var likelihoods = ipomdp.obsLikelihoods(b, bestAction);
                for (var obs: ipomdp.oAll) {

                    var prob = DDOP.restrict(likelihoods,
                            ipomdp.i_Om_p(), obs).getVal();

                    // If observation is impossible for this belief, skip it
                    if (prob < 1e-6f) 
                        continue;
                    
                    // Add new belief for action bestAction and observation obs
                    // to the beliefsQueue
                    var nextBelief = ipomdp.beliefUpdate(b, bestAction, obs);
                    var nextId = nextNodeId();
                    var nextNode = Tuple.of(nextBelief, d + 1, nextId);
                    beliefQueue.addLast(nextNode);

                    // Record policy tree edge
                    updateFSC(n, obs, nextId);
                }
            }
        }

        beliefQueue.clear();
        System.gc();
    }
}
