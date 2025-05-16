
package thinclab.executables;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
import thinclab.utils.Tuple3;
import thinclab.utils.Utils;


public class RecomputePolicy {


    private static final Logger LOGGER = 
        LogManager.getFormatterLogger(RecomputePolicy.class);

    public static void main(String[] args) throws Exception {

        CommandLineParser cliParser = new DefaultParser();
        Options opt = new Options();

        opt.addOption("h", false, "print help");
        opt.addOption("biased", false, "model biased attacker");
        opt.addOption("o", true, "dir containing serialized models");
        opt.addOption("iBel", true, 
                "name of the initial belief DD of agent i");
        opt.addOption("iName", true, "name of agent i");

        CommandLine line = null;
        line = cliParser.parse(opt, args);

        if (line.hasOption("h")) {
            new HelpFormatter().printHelp(" ", opt);
            System.exit(0);
        }

        if (line.hasOption("biased"))
            Global.MODEL_BIASED = true;

        String serializedDir = line.getOptionValue("o");
        Global.RESULTS_DIR = Path.of(serializedDir);

        String iName = line.getOptionValue("iName");
        String iBel = line.getOptionValue("iBel");

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

        // Solve IPOMDP
        AlphaVectorPolicy p = new SymbolicPerseusSolver<>(model)
            .solve(List.of(b_i), 100, 20);

        var G = PolicyGraph.makePolicyGraph(List.of(b_i), model, p);
        Utils.serializePolicyGraph(G, model.getName());

        String policyFile = String.format("%s/%s.policy",
                serializedDir, iName);
        LOGGER.info("Writing policy to %s", policyFile);
        var oos = new ObjectOutputStream(new FileOutputStream(policyFile));
        oos.writeObject(p);
        oos.close();
    }
}
