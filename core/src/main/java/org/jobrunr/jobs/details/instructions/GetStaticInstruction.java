package org.jobrunr.jobs.details.instructions;

import org.jobrunr.jobs.details.JobDetailsFinderContext;

import static org.jobrunr.jobs.details.JobDetailsGeneratorUtils.getObjectViaStaticField;
import static org.jobrunr.jobs.details.JobDetailsGeneratorUtils.toFQClassName;

public class GetStaticInstruction extends VisitFieldInstruction {

    public GetStaticInstruction(JobDetailsFinderContext jobDetailsBuilder) {
        super(jobDetailsBuilder);
    }

    @Override
    public Object invokeInstruction() {
        // TODO: how to know if we should invoke it or create JobDetails with static field?
        String className = toFQClassName(owner);
        String methodName = name;

        if (className.equals(System.class.getName())) {
            jobDetailsBuilder.setClassName(className);
            jobDetailsBuilder.setStaticFieldName(name);
        }
        return getObjectViaStaticField(className, methodName);
    }

    @Override
    public String toDiagnosticsString() {
        return "GETSTATIC " + owner + "." + name + ":" + descriptor;
    }
}
