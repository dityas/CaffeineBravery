
package thinclab.executables;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.HashMap;


import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import thinclab.legacy.DD;
import thinclab.legacy.Global;
import thinclab.legacy.TypedCacheMap;
import thinclab.models.PBVISolvablePOMDPBasedModel;
import thinclab.models.IPOMDP.IPOMDP;
import thinclab.models.datastructures.PolicyGraph;
import thinclab.models.IPOMDP.MjRepr;
import thinclab.models.datastructures.ReachabilityNode;
import thinclab.policy.AlphaVectorPolicy;
import thinclab.policy.BoltzmannExplorationPolicy;
import thinclab.simulator.SimulationSerializer;
import thinclab.simulator.Simulator;
import thinclab.solver.SymbolicPerseusSolver;
import thinclab.spuddx_parser.SpuddXMainParser;
import thinclab.utils.Tuple3;
import thinclab.utils.Utils;


public class RunSim {


    private static final Logger LOGGER = 
        LogManager.getFormatterLogger(RunSim.class);

    public static void runMultiAgentInteraction(Simulator sim,
            final IPOMDP agentI,
            final PBVISolvablePOMDPBasedModel agentJ,
            final AlphaVectorPolicy agentIPolicy,
            final AlphaVectorPolicy agentJPolicy,
            DD state, DD iBelief, DD jBelief, int length,
            SimulationSerializer recorder) {

        // set initial state
        sim.setState(state);

        for (int i = 0; i < length; i++) {

            var initialState = sim.getState();

            // get optimal actions
            var optActI = agentIPolicy.getBestActionIndex(
                    iBelief);
            var optActJ = agentJPolicy.getBestActionIndex(
                    jBelief);

            // step the simulator
            var observations = sim.step(optActI, optActJ);

            // record the interaction step
            recorder.recordStep(initialState, sim.stateIndices,
                    iBelief, jBelief, 
                    optActI, optActJ, 
                    observations._0(), observations._1());

            // update agent beliefs
            iBelief = agentI.beliefUpdate(
                    iBelief, optActI, observations._0()._1());
            jBelief = agentJ.beliefUpdate(
                    jBelief, optActJ, observations._1()._1());
        }
    }

    public static void main(String[] args) throws Exception {

        CommandLineParser cliParser = new DefaultParser();
        Options opt = new Options();

        opt.addOption("h", false, "print help");
        opt.addOption("biased", false, "model biased attacker");
        opt.addOption("o", true, "dir containing serialized models");
        opt.addOption("r", true, "results dir");
        opt.addOption("iBel", true, 
                "name of the initial belief DD of agent i");
        opt.addOption("jBel", true, 
                "name of the initial belief DD of agent j");
        opt.addOption("iState", true, 
                "name of the initial state DD");
        opt.addOption("iName", true, "name of agent i");
        opt.addOption("jName", true, "name of agent j");
        opt.addOption("l", true, "length of the interaction");
        opt.addOption("i", true, "number of interactions");
        opt.addOption("c", true, "confidence for quantal response model");

        CommandLine line = null;
        line = cliParser.parse(opt, args);

        if (line.hasOption("h")) {
            new HelpFormatter().printHelp(" ", opt);
            System.exit(0);
        }

        if (line.hasOption("biased"))
            Global.MODEL_BIASED = true;

        String serializedDir = line.getOptionValue("o");
        String resultsDir = line.getOptionValue("r");

        Global.RESULTS_DIR = Path.of(resultsDir);

        String iName = line.getOptionValue("iName");
        String iBel = line.getOptionValue("iBel");

        String[] jName = line.getOptionValue("jName").split(",");
        String[] jBel = line.getOptionValue("jBel").split(",");

        // Load from serialized
        // Load vars
        String varsFile = String.format("%s/%s.vars", serializedDir, iName);
        var ois = new ObjectInputStream(new FileInputStream(varsFile));
        var vars = (Tuple3<List<Integer>, List<String>, List<List<String>>>) ois.readObject();
        Global.loadFromVarsTuple(vars);

        // Load modelvars
        String mvarsFile = String.format("%s/%s.mvars", serializedDir, iName);
        ois = new ObjectInputStream(new FileInputStream(mvarsFile));
        var mvars = (HashMap<String, HashMap<MjRepr<ReachabilityNode>, String>>) ois.readObject();
        Global.modelVars = mvars;

        // Load model
        String modelFile = String.format("%s/%s.model", serializedDir, iName);
        ois = new ObjectInputStream(new FileInputStream(modelFile));
        IPOMDP model = (IPOMDP) ois.readObject();

        // Load start belief
        String b_iFile = String.format("%s/%s.dd", serializedDir, iBel);
        ois = new ObjectInputStream(new FileInputStream(b_iFile));
        DD b_i = (DD) ois.readObject();

        // Load policy
        String policyFile = String.format("%s/%s.policy", serializedDir, iName);
        ois = new ObjectInputStream(new FileInputStream(policyFile));
        AlphaVectorPolicy p = (AlphaVectorPolicy) ois.readObject();

        // Load other beliefs
        var jDDs = new ArrayList<DD>();
        for (int j = 0; j < jBel.length; j++) {
            String b_jFile = String.format("%s/%s.dd", serializedDir, jBel[j]);
            ois = new ObjectInputStream(new FileInputStream(b_jFile));
            DD b_j = (DD) ois.readObject();
            jDDs.add(b_j);
        }

        if (jName.length != jBel.length - 1)
            throw new RuntimeException("Agents and beliefs do not match for j");

        int l = Integer.parseInt(line.getOptionValue("l"));
        int i = Integer.parseInt(line.getOptionValue("i"));

//        var G = PolicyGraph.makePolicyGraph(List.of(b_i), model, p);
//        Utils.serializePolicyGraph(G, model.getName());

        var confidence = line.getOptionValue("c");

        for (int j = 0; j < jBel.length - 1; j++) {

            // Get the opponent model
            final int jIdx = j;
            var jModel = model.framesj.stream()
                .filter(_m -> _m._1().getName().equals(jName[jIdx]))
                .findFirst()
                .map(_m -> _m._1()).get();

            // Get opponent policy
            var jPolicy = model.ecThetas.stream()
                .filter(_p -> _p.m.getName().equals(jName[jIdx]))
                .findFirst()
                .map(_p -> _p.Vn).get();

            if (confidence != null)
                jPolicy = new BoltzmannExplorationPolicy(
                        jPolicy, jPolicy.stateIndices, Float.parseFloat(confidence),
                        jModel.A.size());

            // Get initial beliefs and starting state
            var b_j = jDDs.get(j);
            b_j = jModel instanceof IPOMDP _jModel ?
                _jModel.getECDDFromMjDD(b_j) : b_j;
            var s = jDDs.get(jDDs.size() - 1);

            if (b_i == null) {
                LOGGER.error("Belief DD %s does not exist", iBel);
                System.exit(-1);
            }

            if (b_j == null) {
                LOGGER.error("Belief DD %s does not exist", jBel[j]);
                System.exit(-1);
            }

            // Run the interaction
            var stateIndices = new ArrayList<>(model.i_S());
            stateIndices.remove(stateIndices.size() - 1);

            var sim = new Simulator(stateIndices,
                    model.i_A, jModel.i_A, 
                    model.i_Om_p(), jModel.i_Om_p(), 
                    model.T(), model.O(), jModel.O());

            for (int n = 0; n < i; n++) {
                LOGGER.info("Running interaction %s", n);

                // For recording the interaction
                var recorder = new SimulationSerializer(model, jModel);
                runMultiAgentInteraction(sim, model, jModel, p, jPolicy, 
                        s, b_i, b_j, l, recorder);

                // Write the interaction to a file
                if (Global.RESULTS_DIR != null) {
                    String fileName = String.format("%s/trace.%s.%s.json", 
                            Global.RESULTS_DIR, jName[j], n);
                    LOGGER.info("Recording interaction %s to %s", n, fileName);
                    Utils.writeJsonToFile(recorder.recorder, fileName);
                }
            }
        }
    }
}
