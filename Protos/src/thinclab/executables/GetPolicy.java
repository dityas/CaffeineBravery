/*
 *	THINC Lab at UGA | Cyber Deception Group
 *
 *	Author: Aditya Shinde
 * 
 *	email: shinde.aditya386@gmail.com
 */
package thinclab.executables;

import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.util.List;


import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import thinclab.legacy.Global;
import thinclab.models.IPOMDP.IPOMDP;
import thinclab.policy.AlphaVectorPolicy;
import thinclab.solver.SymbolicPerseusSolver;
import thinclab.spuddx_parser.SpuddXMainParser;

/*
 * @author adityas
 *
 */
public class GetPolicy {

    private static final Logger LOGGER = 
        LogManager.getFormatterLogger(GetPolicy.class);

	public static void main(String[] args) throws Exception {

        CommandLineParser cliParser = new DefaultParser();
        Options opt = new Options();

        opt.addOption("h", false, "print help");
        opt.addOption("biased", false, "model biased attacker");
        opt.addOption("d", true, "path to the SPUDDX file");
        opt.addOption("iBel", true, 
                "name of the initial belief DD of agent i");
        opt.addOption("iName", true, "name of agent i");
        opt.addOption("o", true, "output directory");
        opt.addOption("b", true, "DDs to serialize");

        CommandLine line = null;
        line = cliParser.parse(opt, args);

        if (line.hasOption("h")) {
            new HelpFormatter().printHelp(" ", opt);
            System.exit(0);
        }

        if (line.hasOption("biased"))
            Global.MODEL_BIASED = true;

        String domainFile = line.getOptionValue("d");
        String outputDir = line.getOptionValue("o");

        String iName = line.getOptionValue("iName");
        String iBel = line.getOptionValue("iBel");

        String[] ddNames = {};

        if (line.hasOption("b"))
            ddNames = line.getOptionValue("b").split(",");

        // Parse SPUDDX file
        var parser = new SpuddXMainParser(domainFile);
        parser.run();

        LOGGER.info("Parsed domain file");

        // Get the agent model
        var model = (IPOMDP) parser.getModel(iName).orElseGet(() ->
                {
                    LOGGER.error("Model %s not found", iName);
                    throw new RuntimeException("Model not found error");
                });

        var b_i = parser.getDD(iBel);
        b_i = model.getECDDFromMjDD(b_i);

        // Solve IPOMDP
        AlphaVectorPolicy p = new SymbolicPerseusSolver<>(model)
            .solve(List.of(b_i), 100, 20);

        String policyFile = String.format("%s/%s.policy", outputDir, iName);
        String varFile = String.format("%s/%s.vars", outputDir, iName);
        String modelFile = String.format("%s/%s.model", outputDir, iName);
        String modelVarsFile = String.format("%s/%s.mvars", outputDir, iName);

        LOGGER.info("Writing policy to %s", policyFile);
        var oos = new ObjectOutputStream(new FileOutputStream(policyFile));
        oos.writeObject(p);
        oos.close();

        LOGGER.info("Writing vars to %s", varFile);
        oos = new ObjectOutputStream(new FileOutputStream(varFile));
        oos.writeObject(Global.getVarsTuple());
        oos.close();

        LOGGER.info("Writing model to %s", modelFile);
        oos = new ObjectOutputStream(new FileOutputStream(modelFile));
        oos.writeObject(model);
        oos.close();

        LOGGER.info("Writing model vars to %s", modelVarsFile);
        oos = new ObjectOutputStream(new FileOutputStream(modelVarsFile));
        oos.writeObject(Global.modelVars);
        oos.close();

        // Serialize DDs
        String ddFile = String.format("%s/%s.dd", outputDir, iBel);
        LOGGER.info("Writing DD to %s", ddFile);
        oos = new ObjectOutputStream(new FileOutputStream(ddFile));
        oos.writeObject(b_i);
        oos.close();

        for (int j = 0; j < ddNames.length; j++) {

            var dd = parser.getDD(ddNames[j]);
            ddFile = String.format("%s/%s.dd", outputDir, ddNames[j]);
            LOGGER.info("Writing DD to %s", ddFile);
            oos = new ObjectOutputStream(new FileOutputStream(ddFile));
            oos.writeObject(dd);
            oos.close();
        }
	}
}
