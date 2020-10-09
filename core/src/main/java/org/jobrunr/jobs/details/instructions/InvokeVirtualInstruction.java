package org.jobrunr.jobs.details.instructions;

import org.jobrunr.jobs.details.JobDetailsFinderContext;

public class InvokeVirtualInstruction extends JobDetailsInstruction {

    public InvokeVirtualInstruction(JobDetailsFinderContext jobDetailsBuilder) {
        super(jobDetailsBuilder);
    }

    @Override
    public String toDiagnosticsString() {
        return "INVOKEVIRTUAL " + owner + "." + name + descriptor;
    }

}
