
package thinclab.executables;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import thinclab.DDOP;
import thinclab.legacy.DD;
import thinclab.legacy.Global;
import thinclab.models.PBVISolvablePOMDPBasedModel;
import thinclab.models.IPOMDP.IPOMDP;
import thinclab.models.IPOMDP.MjRepr;
import thinclab.models.datastructures.PolicyGraph;
import thinclab.models.datastructures.ReachabilityNode;
import thinclab.policy.AlphaVectorPolicy;
import thinclab.policy.BoltzmannExplorationPolicy;
import thinclab.simulator.SimulationSerializer;
import thinclab.simulator.Simulator;
import thinclab.solver.SymbolicPerseusSolver;
import thinclab.spuddx_parser.SpuddXMainParser;
import thinclab.utils.Tuple3;
import thinclab.utils.Utils;


public class SimBeliefUpdate {


    private static final Logger LOGGER = 
        LogManager.getFormatterLogger(SimBeliefUpdate.class);

    // -----------------------------------------------------------------------
    // Confirmation bias test for IPOMDPs

//    public List<DD> evidenceFactors(DD o) {
//
//        var vars = new ArrayList<>(o.getVars());
//        return vars.size() > 1 ? 
//            DDOP.factors(o, vars) : vars.size() == 1 ? 
//            List.of(o) : List.of();
//    }
//
//    public float getWeight(List<DD> OFaoFactors, List<DD> b_pFactors) {
//
//        float sum = 0.0f;
//
//        for (var f : OFaoFactors) {
//
//            var vars = f.getVars();
//            
//            if (vars.size() == 1) {
//                // Because of the variable ordering, the required var 
//                // will always be at the var index - (# unprimed vars) 
//                // in the array the -1 is to index the var in Globals.
//
//                int varIndex = vars.first() 
//                        - 1 - (Global.NUM_VARS / 2);
//
//                if (varIndex < 0)
//                    varIndex = b_pFactors.size() - 1;
//
//                var p = b_pFactors.get(varIndex);
//                sum += DDOP.l2NormSq(
//                        p, f, 
//                        Global.valNames.get(vars.first() - 1).size());
//            }
//        }
//
//        return 1.0f / (1.0f + sum);
//    }
//
//    public float getWeight(DD likelihoods, DD prediction) {
//
//        float sum = DDOP.l1Norm(likelihoods, prediction);
//        return 2.0f / (1.0f + sum);
//    }
//
//    public List<DD> getWeightedEvidence(DD predictedB, List<DD> OFao) {
//
//        var weighted = new ArrayList<DD>(OFao.size());
//        for (var ofao : OFao) {
//
//            var ofaoVars = ofao.getVars();
//            var vars = new ArrayList<>(i_S_p());
//            vars.removeAll(ofaoVars);
//
//            var w = getWeight(
//                    ofao, 
//                    DDOP.addMultVarElim(List.of(predictedB), vars));
//            weighted.add(DDOP.pow(ofao, w));
//        }
//
//        return weighted;
//    }
//
//    public List<DD> getWeightedEvidence(List<DD> p, List<DD> OFao) {
//
//        var weighted = new ArrayList<DD>(OFao.size());
//        for (var ofao : OFao) {
//            var w = getWeight(evidenceFactors(ofao), p);
//            var _w = DDOP.pow(ofao, w);
//            weighted.add(_w);
//        }
//
//        return weighted;
//    }
//
//    public DD beliefUpdateBiased(DD b, int a, List<Integer> o) {
//
//        var OFao = DDOP.restrict(this.OF.get(a), i_Om_p, o);
//
//		var factors = new ArrayList<DD>(S().size() + S().size() + Omj.size() + 3);
//
//		factors.add(b);
//		factors.add(PAjGivenEC);
//		factors.add(PThetajGivenEC);
//		factors.add(Taus.get(a));
//		factors.addAll(T().get(a));
//
//		var vars = new ArrayList<Integer>(factors.size());
//		vars.addAll(i_S());
//		vars.add(i_Thetaj);
//		// vars.add(i_Aj);
//
//        var b_p = DDOP.addMultVarElim(factors, vars);
//		var stateVars = new ArrayList<Integer>(i_S());
//
//        var _vars = new ArrayList<>(i_S_p());
//        _vars.add(i_Aj);
//        
//        // compute evidence weight
//        var wOFao = getWeightedEvidence(b_p, OFao);
//
//        wOFao.add(b_p);
//        b_p = DDOP.addMultVarElim(wOFao, List.of(i_Aj));
//		b_p = DDOP.primeVars(b_p, -(Global.NUM_VARS / 2));
//		
//        var prob = DDOP.addMultVarElim(List.of(b_p), stateVars);
//
//		if (DDOP.abs(DDOP.sub(prob, DD.zero)).getVal() < 1e-6) {
//            LOGGER.error("Zero probability observation");
//            return DDleaf.getDD(Float.NaN);
//        }
//
//		b_p = DDOP.div(b_p, prob);
//
//		return b_p;
//    }

    public static int getAction(final IPOMDP agentI, DD belief) {

        var scanner = new Scanner(System.in);

        var factors = DDOP.factors(belief, agentI.i_S());
        System.out.println("Current belief");
        for (var d: factors)
            System.out.println(d);
        System.out.println("End current belief");
        System.out.println();

        System.out.println("Actions:");
        for (int a = 0; a < agentI.A().size(); a++)
            System.out.println(String.format("%s: %s", a, agentI.A().get(a)));
        System.out.print("Enter action index: ");

        int act = Integer.parseInt(scanner.nextLine());

        return act;
    }

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
            var optActJ = getAction((IPOMDP) agentJ, jBelief);

            // step the simulator
            var observations = sim.step(optActI, optActJ);

            // record the interaction step
            recorder.recordStep(initialState, sim.stateIndices,
                    iBelief, jBelief, 
                    optActI, optActJ, 
                    observations._0(), observations._1(),
                    agentIPolicy, agentJPolicy);

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
        ois.close();

        // Load modelvars
        String mvarsFile = String.format("%s/%s.mvars", serializedDir, iName);
        ois = new ObjectInputStream(new FileInputStream(mvarsFile));
        var mvars = (HashMap<String, HashMap<MjRepr<ReachabilityNode>, String>>) ois.readObject();
        Global.modelVars = mvars;
        ois.close();

        // Load model
        String modelFile = String.format("%s/%s.model", serializedDir, iName);
        ois = new ObjectInputStream(new FileInputStream(modelFile));
        IPOMDP model = (IPOMDP) ois.readObject();
        ois.close();


        // Load start belief
        String b_iFile = String.format("%s/%s.dd", serializedDir, iBel);
        ois = new ObjectInputStream(new FileInputStream(b_iFile));
        DD b_i = (DD) ois.readObject();
        ois.close();

        // Load policy
        String policyFile = String.format("%s/%s.policy", serializedDir, iName);
        ois = new ObjectInputStream(new FileInputStream(policyFile));
        AlphaVectorPolicy p = (AlphaVectorPolicy) ois.readObject();
        ois.close();

        // Load other beliefs
        var jDDs = new ArrayList<DD>();
        for (int j = 0; j < jBel.length; j++) {
            String b_jFile = String.format("%s/%s.dd", serializedDir, jBel[j]);
            ois = new ObjectInputStream(new FileInputStream(b_jFile));
            DD b_j = (DD) ois.readObject();
            jDDs.add(b_j);
            ois.close();
        }

        if (jName.length != jBel.length - 1)
            throw new RuntimeException("Agents and beliefs do not match for j");

        int l = Integer.parseInt(line.getOptionValue("l"));
        int i = Integer.parseInt(line.getOptionValue("i"));

//        var G = PolicyGraph.makePolicyGraph(List.of(b_i), model, p);
//        Utils.serializePolicyGraph(G, model.getName());

        var confidence = line.getOptionValue("c");

        for (int j = 0; j < jBel.length - 1; j++) {

            System.gc();

            // Get the opponent model
            final int jIdx = j;
            var jModel = model.framesj.stream()
                .filter(_m -> _m._1().getName().equals(jName[jIdx]))
                .findFirst()
                .map(_m -> _m._1()).get();

            LOGGER.info("Model %s is biased %s", jModel.getName(), jModel.getBiased());

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

                if (n % 50 == 0)
                    System.gc();
            }
        }
    }
}
