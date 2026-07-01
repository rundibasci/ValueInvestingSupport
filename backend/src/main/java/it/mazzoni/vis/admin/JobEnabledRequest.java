package it.mazzoni.vis.admin;

import jakarta.validation.constraints.NotNull;

public record JobEnabledRequest(@NotNull Boolean enabled) {
}
