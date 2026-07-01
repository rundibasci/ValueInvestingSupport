package it.mazzoni.vis.admin;

import java.util.function.Supplier;

record JobDefinition(String jobName, String cronKey, Supplier<Integer> runner) {
}
