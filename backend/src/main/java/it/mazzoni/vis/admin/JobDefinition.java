package it.mazzoni.vis.admin;

record JobDefinition(String jobName, String cronKey, Runnable runner) {
}
