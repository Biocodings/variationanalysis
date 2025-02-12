package org.campagnelab.dl.framework.tools.arguments;

/**
 * A version of ToolArguments that records command line parameters and results, then writes both to a log file.
 */

import com.beust.jcommander.Parameter;

public class RecordingToolArguments implements ToolArguments {
    @Parameter( names = {"--model-conditions"}, description = "Path to the model conditions file. The file will be appedend with a command line parameters and associated results.")
    public String modelConditionFilename = "./model-conditions.txt";
}
