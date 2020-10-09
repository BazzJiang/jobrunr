package org.jobrunr.jobs.details.instructions;

import org.jobrunr.jobs.details.JobDetailsFinderContext;

public class InvokeInterfaceInstruction extends JobDetailsInstruction {

    public InvokeInterfaceInstruction(JobDetailsFinderContext jobDetailsBuilder) {
        super(jobDetailsBuilder);
    }

    @Override
    public String toDiagnosticsString() {
        return "INVOKEINTERFACE " + owner + "." + name + descriptor;
    }

}
